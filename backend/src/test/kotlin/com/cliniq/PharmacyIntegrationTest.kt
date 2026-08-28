package com.cliniq

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID

/**
 * Pharmacy integration coverage for existing service:
 * A. Successful dispensing with stock decrease
 * B. Insufficient inventory rejected
 * C. Out-of-stock rejected
 * D. Unauthorized role cannot dispense (403)
 * E. Clinic isolation
 */
class PharmacyIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var redis: StringRedisTemplate

    private lateinit var adminToken: String
    private lateinit var doctorToken: String
    private lateinit var receptionToken: String
    private lateinit var patientId: UUID

    @BeforeEach
    fun setup() {
        redis.keys("rl:*")?.let { if (it.isNotEmpty()) redis.delete(it) }
        adminToken = login("admin@cliniq.app", "Demo1234!")
        doctorToken = login("doctor@cliniq.app", "Demo1234!")
        receptionToken = login("reception@cliniq.app", "Demo1234!")

        // Find or create patient
        val listResult = mockMvc.perform(get("/api/v1/patients?q=PAT001", adminToken)).andReturn()
        val data = json(listResult).path("data")
        patientId = if (data.isArray && data.size() > 0) {
            UUID.fromString(data[0].path("id").asText())
        } else {
            val allResult = mockMvc.perform(get("/api/v1/patients", adminToken)).andReturn()
            val allData = json(allResult).path("data")
            if (allData.isArray && allData.size() > 0) UUID.fromString(allData[0].path("id").asText())
            else {
                val uniquePhone = "+2547${(100000000 + (System.nanoTime() % 900000000)).toString().padStart(9,'0')}"
                val createResult = mockMvc.perform(
                    post("/api/v1/patients", mapOf(
                        "firstName" to "Pharm",
                        "lastName" to "Patient",
                        "phone" to uniquePhone,
                        "gender" to "MALE",
                        "dateOfBirth" to "1990-01-01"
                    ), adminToken)
                ).andExpect(status().isCreated).andReturn()
                UUID.fromString(json(createResult).path("data").path("id").asText())
            }
        }
    }

    private fun createDrug(token: String = adminToken): UUID {
        val name = "Drug-${UUID.randomUUID().toString().substring(0,8)}"
        val result = mockMvc.perform(
            post("/api/v1/pharmacy/drugs", mapOf(
                "name" to name,
                "category" to "OTHER",
                "unit" to "TABLETS",
                "reorderLevel" to 10,
                "sellingPrice" to 10.0
            ), token)
        ).andExpect(status().isCreated).andReturn()
        return UUID.fromString(json(result).path("data").path("drugId").asText())
    }

    private fun addStock(drugId: UUID, quantity: Int, token: String = adminToken): String {
        val batch = "BATCH-${System.nanoTime()}"
        mockMvc.perform(
            post("/api/v1/pharmacy/stock/add", mapOf(
                "drugId" to drugId.toString(),
                "batchNumber" to batch,
                "quantity" to quantity,
                "unitCost" to 5.0,
                "expiryDate" to LocalDate.now().plusMonths(6).toString()
            ), token)
        ).andExpect(status().isCreated).andReturn()
        return batch
    }

    private fun getStock(drugId: UUID, token: String = adminToken): Int {
        val result = mockMvc.perform(get("/api/v1/pharmacy/drugs/${drugId}/stock", token)).andExpect(status().isOk).andReturn()
        return json(result).path("data").path("totalStock").asInt()
    }

    @Test
    fun `A successful dispensing decreases stock correctly`() {
        val drugId = createDrug()
        addStock(drugId, 100)
        val before = getStock(drugId)
        assert(before == 100)

        mockMvc.perform(
            post("/api/v1/pharmacy/dispense", mapOf(
                "drugId" to drugId.toString(),
                "patientId" to patientId.toString(),
                "quantity" to 20,
                "instructions" to "Take twice daily"
            ), doctorToken)
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.quantity").value(20))
            .andExpect(jsonPath("$.data.drugName").isNotEmpty)

        val after = getStock(drugId)
        assert(after == 80) { "expected 80 but was $after" }
    }

    @Test
    fun `B insufficient inventory is rejected and stock unchanged`() {
        val drugId = createDrug()
        addStock(drugId, 10)
        val before = getStock(drugId)
        mockMvc.perform(
            post("/api/v1/pharmacy/dispense", mapOf(
                "drugId" to drugId.toString(),
                "patientId" to patientId.toString(),
                "quantity" to 50
            ), doctorToken)
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
        val after = getStock(drugId)
        assert(after == before) { "stock should remain $before but was $after" }
    }

    @Test
    fun `C out of stock is rejected`() {
        val drugId = createDrug()
        // No stock added
        val before = getStock(drugId)
        assert(before == 0)
        mockMvc.perform(
            post("/api/v1/pharmacy/dispense", mapOf(
                "drugId" to drugId.toString(),
                "patientId" to patientId.toString(),
                "quantity" to 5
            ), doctorToken)
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
        val after = getStock(drugId)
        assert(after == 0)
    }

    @Test
    fun `D unauthorized role cannot dispense`() {
        val drugId = createDrug()
        addStock(drugId, 20)
        // reception has RECEPTIONIST role, not allowed for /dispense (requires CLINIC_ADMIN,NURSE,DOCTOR,SUPER_ADMIN)
        mockMvc.perform(
            post("/api/v1/pharmacy/dispense", mapOf(
                "drugId" to drugId.toString(),
                "patientId" to patientId.toString(),
                "quantity" to 5
            ), receptionToken)
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `E clinic isolation prevents using other clinic inventory`() {
        val drugIdClinicA = createDrug(adminToken)
        addStock(drugIdClinicA, 50, adminToken)

        // Create clinic B
        val slug = "pharmb-${UUID.randomUUID().toString().substring(0,8)}"
        val registerResult = mockMvc.perform(
            post("/api/v1/auth/register", mapOf(
                "clinicName" to "Pharm Clinic B",
                "clinicSlug" to slug,
                "firstName" to "Bob",
                "lastName" to "Admin",
                "email" to "admin-$slug@cliniq.test",
                "password" to "Password123!",
                "phone" to "+254711111111"
            ))
        ).andExpect(status().isCreated).andReturn()
        val clinicBToken = json(registerResult).path("data").path("accessToken").asText()

        // Clinic B user tries to dispense Clinic A drug -> 404 Drug not found (clinic isolation)
        // Reuse patientId from clinic A (patient clinic not validated, drug isolation is the check)
        mockMvc.perform(
            post("/api/v1/pharmacy/dispense", mapOf(
                "drugId" to drugIdClinicA.toString(),
                "patientId" to patientId.toString(),
                "quantity" to 5
            ), clinicBToken)
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))

        // Also verify stock still 50 in Clinic A
        val after = getStock(drugIdClinicA, adminToken)
        assert(after == 50)
    }
}
