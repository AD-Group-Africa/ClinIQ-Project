package com.cliniq

import com.cliniq.domain.billing.service.MpesaService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Proves the billing/payment foundation:
 * - invoice lifecycle (ISSUED → PAID)
 * - overpayment is rejected
 * - M-Pesa callbacks are the only path that can mark an M-Pesa payment paid,
 *   and failed callbacks (resultCode != 0) never change invoice state.
 */
@org.springframework.test.context.TestPropertySource(properties = ["mpesa.callback-secret=test-callback-secret-32chars-min"])
class BillingFlowIntegrationTest : AbstractIntegrationTest() {

    private lateinit var adminToken: String
    private lateinit var patientId: UUID

    @Autowired
    lateinit var mpesaService: MpesaService

    @Autowired
    lateinit var redis: StringRedisTemplate

    @BeforeEach
    fun setup() {
        redis.keys("rl:*")?.let { if (it.isNotEmpty()) redis.delete(it) }
        adminToken = login("admin@cliniq.app", "Demo1234!")
        val listResult = mockMvc.perform(get("/api/v1/patients?q=PAT001", adminToken)).andReturn()
        val data = json(listResult).path("data")
        if (data.isArray && data.size() > 0) {
            patientId = UUID.fromString(data[0].path("id").asText())
        } else {
            val uniquePhone = "+2547${(100000000 + (System.nanoTime() % 900000000)).toString().padStart(9,'0')}"
            val createResult = mockMvc.perform(
                post("/api/v1/patients", mapOf(
                    "firstName" to "Test",
                    "lastName" to "Patient",
                    "phone" to uniquePhone,
                    "gender" to "MALE",
                    "dateOfBirth" to "1990-01-01"
                ), adminToken)
            ).andExpect(status().isCreated).andReturn()
            patientId = UUID.fromString(json(createResult).path("data").path("id").asText())
        }
    }

    private fun createInvoice(amount: Double = 1000.0): String {
        val result = mockMvc.perform(
            post(
                "/api/v1/billing/invoices",
                mapOf(
                    "patientId" to patientId.toString(),
                    "lineItems" to listOf(
                        mapOf("description" to "Consultation", "quantity" to 1, "unitPrice" to amount)
                    )
                ),
                adminToken
            )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.status").value("ISSUED"))
            .andReturn()
        return json(result).path("data").path("id").asText()
    }

    @Test
    fun `invoice is created as ISSUED and listed`() {
        val invoiceId = createInvoice()

        mockMvc.perform(get("/api/v1/billing/invoices", adminToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content[?(@.id == '$invoiceId')]").exists())
    }

    @Test
    fun `full payment marks invoice PAID`() {
        val invoiceId = createInvoice(1000.0)

        mockMvc.perform(
            post(
                "/api/v1/billing/payments",
                mapOf("invoiceId" to invoiceId, "amount" to 1000.0, "method" to "CASH"),
                adminToken
            )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.status").value("PAID"))
            .andExpect(jsonPath("$.data.balance").value(0.0))
    }

    @Test
    fun `overpayment is rejected`() {
        val invoiceId = createInvoice(500.0)

        mockMvc.perform(
            post(
                "/api/v1/billing/payments",
                mapOf("invoiceId" to invoiceId, "amount" to 600.0, "method" to "CASH"),
                adminToken
            )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    @Test
    fun `failed M-Pesa callback does not mark invoice paid`() {
        val invoiceId = createInvoice(1000.0)
        val token = mpesaService.buildCallbackUrl(UUID.fromString(invoiceId)).substringAfter("token=")

        mockMvc.perform(
            post(
                "/api/v1/billing/mpesa/callback?invoiceId=$invoiceId&token=$token",
                mapOf(
                    "Body" to mapOf(
                        "stkCallback" to mapOf(
                            "ResultCode" to 1,
                            "ResultDesc" to "The service request is processed successfully.",
                            "CallbackMetadata" to mapOf(
                                "Item" to listOf(
                                    mapOf("Name" to "Amount", "Value" to 1000),
                                    mapOf("Name" to "MpesaReceiptNumber", "Value" to "FAIL123")
                                )
                            )
                        )
                    )
                )
            )
        )
            .andExpect(status().isOk)

        // Invoice must remain unpaid
        mockMvc.perform(get("/api/v1/billing/invoices/$invoiceId", adminToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("ISSUED"))
            .andExpect(jsonPath("$.data.paidAmount").value(0.0))
    }

    @Test
    fun `successful M-Pesa callback marks invoice PAID`() {
        val invoiceId = createInvoice(1000.0)
        val token = mpesaService.buildCallbackUrl(UUID.fromString(invoiceId)).substringAfter("token=")
        val receipt = "OK${System.nanoTime()}"

        mockMvc.perform(
            post(
                "/api/v1/billing/mpesa/callback?invoiceId=$invoiceId&token=$token",
                mapOf(
                    "Body" to mapOf(
                        "stkCallback" to mapOf(
                            "ResultCode" to 0,
                            "ResultDesc" to "Success",
                            "CallbackMetadata" to mapOf(
                                "Item" to listOf(
                                    mapOf("Name" to "Amount", "Value" to 1000),
                                    mapOf("Name" to "MpesaReceiptNumber", "Value" to receipt),
                                    mapOf("Name" to "PhoneNumber", "Value" to 254700000000)
                                )
                            )
                        )
                    )
                )
            )
        )
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/billing/invoices/$invoiceId", adminToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PAID"))
    }
}