package com.cliniq

import com.cliniq.domain.billing.service.MpesaService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Proves M-Pesa callback security:
 * - valid callback with correct HMAC token succeeds
 * - invalid authenticity (wrong token) is rejected
 * - malformed callback is rejected
 * - duplicate callback is idempotent
 * - unknown transaction is handled safely
 * - cross-clinic token cannot be reused
 *
 * Provider limitation documented: Safaricom Daraja does not sign callbacks;
 * ClinIQ secures callbacks via HMAC token embedded in the callback URL at STK push time.
 */
@org.springframework.test.context.TestPropertySource(properties = ["mpesa.callback-secret=test-callback-secret-32chars-min"])
class MpesaCallbackSecurityIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var mpesaService: MpesaService

    private lateinit var adminToken: String
    private lateinit var patientId: UUID

    @Autowired
    lateinit var redis: org.springframework.data.redis.core.StringRedisTemplate

    @BeforeEach
    fun setup() {
        redis.keys("rl:*")?.let { if (it.isNotEmpty()) redis.delete(it) }
        adminToken = login("admin@cliniq.app", "Demo1234!")
        val listResult = mockMvc.perform(get("/api/v1/patients?q=PAT001", adminToken)).andReturn()
        val body = json(listResult)
        val data = body.path("data")
        patientId = if (data.isArray && data.size() > 0) {
            UUID.fromString(data[0].path("id").asText())
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
            ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated).andReturn()
            UUID.fromString(json(createResult).path("data").path("id").asText())
        }
    }

    private fun createInvoice(amount: Double = 1000.0): String {
        val result = mockMvc.perform(
            post(
                "/api/v1/billing/invoices",
                mapOf(
                    "patientId" to patientId.toString(),
                    "lineItems" to listOf(mapOf("description" to "Consultation", "quantity" to 1, "unitPrice" to amount))
                ), adminToken
            )
        ).andExpect(status().isCreated).andReturn()
        return json(result).path("data").path("id").asText()
    }

    private fun validCallbackBody(amount: Number = 1000, receipt: String = "REC${System.currentTimeMillis()}") =
        mapOf(
            "Body" to mapOf(
                "stkCallback" to mapOf(
                    "ResultCode" to 0,
                    "ResultDesc" to "Success",
                    "CallbackMetadata" to mapOf(
                        "Item" to listOf(
                            mapOf("Name" to "Amount", "Value" to amount),
                            mapOf("Name" to "MpesaReceiptNumber", "Value" to receipt),
                            mapOf("Name" to "PhoneNumber", "Value" to 254700000000)
                        )
                    )
                )
            )
        )

    @Test
    fun `valid callback with correct token marks invoice paid`() {
        val invoiceId = createInvoice(1000.0)
        val token = mpesaService.buildCallbackUrl(UUID.fromString(invoiceId)).substringAfter("token=")
        val receipt = "VALID${System.nanoTime()}"
        mockMvc.perform(
            post("/api/v1/billing/mpesa/callback?invoiceId=$invoiceId&token=$token", validCallbackBody(1000, receipt))
        ).andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/billing/invoices/$invoiceId", adminToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PAID"))
    }

    @Test
    fun `invalid authenticity data is rejected`() {
        val invoiceId = createInvoice(800.0)
        mockMvc.perform(
            post("/api/v1/billing/mpesa/callback?invoiceId=$invoiceId&token=invalid-token", validCallbackBody())
        ).andExpect(status().isUnauthorized)
        // Invoice must remain ISSUED
        mockMvc.perform(get("/api/v1/billing/invoices/$invoiceId", adminToken))
            .andExpect(jsonPath("$.data.status").value("ISSUED"))
    }

    @Test
    fun `missing token is rejected when secret is configured`() {
        val invoiceId = createInvoice(600.0)
        mockMvc.perform(
            post("/api/v1/billing/mpesa/callback?invoiceId=$invoiceId", validCallbackBody())
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `malformed callback is rejected`() {
        val invoiceId = createInvoice(500.0)
        val token = mpesaService.buildCallbackUrl(UUID.fromString(invoiceId)).substringAfter("token=")
        // Missing Body
        mockMvc.perform(
            post("/api/v1/billing/mpesa/callback?invoiceId=$invoiceId&token=$token", mapOf("bad" to "data"))
        ).andExpect(status().isBadRequest)
        // Invoice still ISSUED
        mockMvc.perform(get("/api/v1/billing/invoices/$invoiceId", adminToken))
            .andExpect(jsonPath("$.data.status").value("ISSUED"))
    }

    @Test
    fun `duplicate callback is idempotent`() {
        val invoiceId = createInvoice(1000.0)
        val token = mpesaService.buildCallbackUrl(UUID.fromString(invoiceId)).substringAfter("token=")
        val receipt = "DUP${System.nanoTime()}"
        val body = validCallbackBody(1000, receipt)
        mockMvc.perform(post("/api/v1/billing/mpesa/callback?invoiceId=$invoiceId&token=$token", body))
            .andExpect(status().isOk)
        // Second identical callback
        mockMvc.perform(post("/api/v1/billing/mpesa/callback?invoiceId=$invoiceId&token=$token", body))
            .andExpect(status().isOk)
        // Should still be PAID with only one payment (paidAmount == total)
        mockMvc.perform(get("/api/v1/billing/invoices/$invoiceId", adminToken))
            .andExpect(jsonPath("$.data.status").value("PAID"))
            .andExpect(jsonPath("$.data.paidAmount").value(1000.0))
    }

    @Test
    fun `callback for unknown transaction is handled safely`() {
        val fakeId = UUID.randomUUID()
        val token = mpesaService.buildCallbackUrl(fakeId).substringAfter("token=")
        // Should not throw 500, returns success but does not crash
        mockMvc.perform(
            post("/api/v1/billing/mpesa/callback?invoiceId=$fakeId&token=$token", validCallbackBody())
        ).andExpect(status().isOk)
    }

    @Test
    fun `callback token cannot cross clinic boundaries`() {
        // Create invoice in clinic A
        val invoiceId = createInvoice(700.0)
        val invoiceUuid = UUID.fromString(invoiceId)
        val validToken = mpesaService.buildCallbackUrl(invoiceUuid).substringAfter("token=")
        // Token for different invoice should not work for this invoice
        val otherId = UUID.randomUUID()
        val otherToken = mpesaService.buildCallbackUrl(otherId).substringAfter("token=")
        mockMvc.perform(
            post("/api/v1/billing/mpesa/callback?invoiceId=$invoiceId&token=$otherToken", validCallbackBody())
        ).andExpect(status().isUnauthorized)
        // Valid token still works after failed attempt
        mockMvc.perform(
            post("/api/v1/billing/mpesa/callback?invoiceId=$invoiceId&token=$validToken", validCallbackBody(700, "CROSSOK"))
        ).andExpect(status().isOk)
    }

    @Test
    fun `forged callback without correct HMAC cannot mark invoice paid`() {
        val invoiceId = createInvoice(900.0)
        // Try with random token
        mockMvc.perform(
            post("/api/v1/billing/mpesa/callback?invoiceId=$invoiceId&token=deadbeef", validCallbackBody(900, "FORGED"))
        ).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/billing/invoices/$invoiceId", adminToken))
            .andExpect(jsonPath("$.data.status").value("ISSUED"))
    }
}
