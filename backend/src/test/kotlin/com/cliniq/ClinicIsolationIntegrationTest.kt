package com.cliniq

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Proves the multi-tenancy guarantee:
 * - a user of clinic B cannot read clinic A's patients/invoices
 * - the X-Clinic-ID header can never escalate to another clinic
 * - a clinic admin can read their own clinic's data
 */
class ClinicIsolationIntegrationTest : AbstractIntegrationTest() {

    private lateinit var demoAdminToken: String
    private lateinit var clinicBToken: String
    private lateinit var demoPatientId: UUID
    private lateinit var clinicBId: String

    @org.springframework.beans.factory.annotation.Autowired
    lateinit var redis: org.springframework.data.redis.core.StringRedisTemplate

    @BeforeEach
    fun setup() {
        redis.keys("rl:*")?.let { if (it.isNotEmpty()) redis.delete(it) }
        demoAdminToken = login("admin@cliniq.app", "Demo1234!")

        // Find the seeded demo patient (PAT001) or create one
        val listResult = mockMvc.perform(get("/api/v1/patients?q=PAT001", demoAdminToken)).andReturn()
        val patientNode = json(listResult).path("data")
        demoPatientId = if (patientNode.isArray && patientNode.size() > 0) {
            UUID.fromString(patientNode[0].path("id").asText())
        } else {
            val allResult = mockMvc.perform(get("/api/v1/patients", demoAdminToken)).andReturn()
            val allData = json(allResult).path("data")
            if (allData.isArray && allData.size() > 0) {
                UUID.fromString(allData[0].path("id").asText())
            } else {
                val uniquePhone = "+2547${(100000000 + (System.nanoTime() % 900000000)).toString().padStart(9,'0')}"
                val createResult = mockMvc.perform(
                    post("/api/v1/patients", mapOf(
                        "firstName" to "Demo",
                        "lastName" to "Patient",
                        "phone" to uniquePhone,
                        "gender" to "MALE",
                        "dateOfBirth" to "1990-01-01"
                    ), demoAdminToken)
                ).andExpect(status().isCreated).andReturn()
                UUID.fromString(json(createResult).path("data").path("id").asText())
            }
        }

        // Register a second clinic (clinic B) with its own admin
        val slug = "clinicb-${UUID.randomUUID().toString().substring(0, 8)}"
        val registerResult = mockMvc.perform(
            post(
                "/api/v1/auth/register",
                mapOf(
                    "clinicName" to "Clinic B",
                    "clinicSlug" to slug,
                    "firstName" to "Bob",
                    "lastName" to "Admin",
                    "email" to "admin-$slug@cliniq.test",
                    "password" to "Password123!",
                    "phone" to "+254711111111"
                )
            )
        ).andReturn()
        val body = json(registerResult)
        clinicBToken = body.path("data").path("accessToken").asText()
        clinicBId = body.path("data").path("user").path("clinicId").asText()
    }

    @Test
    fun `clinic admin can read own clinic patient`() {
        mockMvc.perform(get("/api/v1/patients/$demoPatientId", demoAdminToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(demoPatientId.toString()))
    }

    @Test
    fun `clinic B admin cannot read clinic A patient`() {
        mockMvc.perform(get("/api/v1/patients/$demoPatientId", clinicBToken))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `clinic B admin cannot list clinic A patients`() {
        mockMvc.perform(get("/api/v1/patients", clinicBToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(0))
    }

    @Test
    fun `X-Clinic-ID header cannot escalate to another clinic`() {
        // Clinic B admin sends X-Clinic-ID of clinic A → must be forbidden
        mockMvc.perform(
            get("/api/v1/patients", clinicBToken)
                .header("X-Clinic-ID", clinicBId) // own clinic: allowed
        )
            .andExpect(status().isOk)

        // Now attempt escalation to clinic A (the demo clinic id lives in the demo admin's token)
        val demoClinicId = json(
            mockMvc.perform(get("/api/v1/auth/me", demoAdminToken)).andReturn()
        ).path("data").path("clinicId").asText()

        mockMvc.perform(
            get("/api/v1/patients", clinicBToken)
                .header("X-Clinic-ID", demoClinicId)
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
    }

    @Test
    fun `clinic B admin cannot read clinic A invoice`() {
        // Create an invoice in clinic A for the demo patient
        val createResult = mockMvc.perform(
            post(
                "/api/v1/billing/invoices",
                mapOf(
                    "patientId" to demoPatientId.toString(),
                    "lineItems" to listOf(
                        mapOf("description" to "Consultation", "quantity" to 1, "unitPrice" to 1000.0)
                    )
                ),
                demoAdminToken
            )
        ).andExpect(status().isCreated).andReturn()

        val invoiceId = json(createResult).path("data").path("id").asText()

        // Clinic B admin must not see it
        mockMvc.perform(get("/api/v1/billing/invoices/$invoiceId", clinicBToken))
            .andExpect(status().isNotFound)
    }
}