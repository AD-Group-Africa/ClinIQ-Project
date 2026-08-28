package com.cliniq

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Proves the authentication foundation:
 * - login issues access + refresh tokens
 * - wrong password is rejected
 * - refresh tokens rotate (old token is revoked after use)
 * - logout blacklists the access token
 */
class AuthFlowIntegrationTest : AbstractIntegrationTest() {

    private val demoEmail = "admin@cliniq.app"
    private val demoPassword = "Demo1234!"

    @Test
    fun `login succeeds for demo admin`() {
        mockMvc.perform(
            post("/api/v1/auth/login", mapOf("email" to demoEmail, "password" to demoPassword))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.data.user.role").value("SUPER_ADMIN"))
    }

    @Test
    fun `login fails with wrong password`() {
        mockMvc.perform(
            post("/api/v1/auth/login", mapOf("email" to demoEmail, "password" to "WrongPassword1!"))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `refresh token rotates and old token is revoked`() {
        // 1. login → capture refresh token R1
        val loginResult = mockMvc.perform(
            post("/api/v1/auth/login", mapOf("email" to demoEmail, "password" to demoPassword))
        ).andReturn()
        val r1 = json(loginResult).path("data").path("refreshToken").asText()

        // 2. refresh with R1 → get R2
        val refreshResult = mockMvc.perform(
            post("/api/v1/auth/refresh", mapOf("refreshToken" to r1))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
            .andReturn()
        val r2 = json(refreshResult).path("data").path("refreshToken").asText()
        assert(r2.isNotBlank() && r2 != r1) { "refresh token must rotate" }

        // 3. refresh with R1 again → must be rejected (rotation revoked it)
        mockMvc.perform(
            post("/api/v1/auth/refresh", mapOf("refreshToken" to r1))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `logout blacklists the access token`() {
        val token = login(demoEmail, demoPassword)

        // token works before logout
        mockMvc.perform(get("/api/v1/auth/me", token))
            .andExpect(status().isOk)

        // logout
        mockMvc.perform(post("/api/v1/auth/logout", token = token))
            .andExpect(status().isOk)

        // same token must now be rejected
        mockMvc.perform(get("/api/v1/auth/me", token))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `unauthenticated request is rejected`() {
        mockMvc.perform(get("/api/v1/patients"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh token cannot be used as access token`() {
        val loginResult = mockMvc.perform(
            post("/api/v1/auth/login", mapOf("email" to demoEmail, "password" to demoPassword))
        ).andReturn()
        val refreshToken = json(loginResult).path("data").path("refreshToken").asText()

        mockMvc.perform(
            MockMvcRequestBuilders.get("/api/v1/auth/me")
                .header("Authorization", "Bearer $refreshToken")
        )
            .andExpect(status().isUnauthorized)
    }
}