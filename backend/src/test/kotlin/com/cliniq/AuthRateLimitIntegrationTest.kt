package com.cliniq

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Proves Redis rate limiting for auth endpoints:
 * - below limit succeeds
 * - excessive login receives 429
 * - registration abuse receives 429
 * - rate limit expires (simulated by clearing Redis)
 * - authenticated endpoints are not rate limited
 */
@TestPropertySource(properties = [
    "app.auth-rate-limit.enabled=true",
    "app.auth-rate-limit.login.max-attempts=3",
    "app.auth-rate-limit.login.window-seconds=60",
    "app.auth-rate-limit.register.max-attempts=2",
    "app.auth-rate-limit.register.window-seconds=60"
])
class AuthRateLimitIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var redis: StringRedisTemplate

    private fun clearRateLimitKeys() {
        val keys = redis.keys("rl:*") ?: emptySet()
        if (keys.isNotEmpty()) redis.delete(keys)
    }

    @Test
    fun `requests below limit succeed normally`() {
        clearRateLimitKeys()
        // 2 logins below limit 3 should succeed (even with wrong password -> 401, not 429)
        repeat(2) {
            mockMvc.perform(
                post("/api/v1/auth/login", mapOf("email" to "ratelimit-below@cliniq.test", "password" to "WrongPass123!"))
            ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }
        // Third attempt with valid demo admin should still succeed, not rate limited
        mockMvc.perform(
            post("/api/v1/auth/login", mapOf("email" to "admin@cliniq.app", "password" to "Demo1234!"))
        ).andExpect(status().isOk)
    }

    @Test
    fun `excessive login requests receive 429`() {
        clearRateLimitKeys()
        val email = "ratelimit-login-${System.currentTimeMillis()}@cliniq.test"
        // Exhaust limit (3)
        repeat(3) {
            mockMvc.perform(
                post("/api/v1/auth/login", mapOf("email" to email, "password" to "WrongPass123!"))
            ).andExpect(status().isUnauthorized)
        }
        // 4th should be rate limited
        mockMvc.perform(
            post("/api/v1/auth/login", mapOf("email" to email, "password" to "WrongPass123!"))
        ).andExpect(status().`is`(429))
            .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"))
    }

    @Test
    fun `excessive login by same IP but different emails still rate limited via IP dimension`() {
        clearRateLimitKeys()
        // First exhaust via same email
        val email = "ip-test-${System.currentTimeMillis()}@cliniq.test"
        repeat(3) {
            mockMvc.perform(post("/api/v1/auth/login", mapOf("email" to email, "password" to "WrongPass123!")))
                .andExpect(status().isUnauthorized)
        }
        // Same IP but different email should still be blocked because IP counter already hit limit
        // Our implementation checks both ipKey and emailKey, ipKey already at 3
        mockMvc.perform(
            post("/api/v1/auth/login", mapOf("email" to "other-${System.currentTimeMillis()}@cliniq.test", "password" to "WrongPass123!"))
        ).andExpect(status().`is`(429))
    }

    @Test
    fun `registration abuse receives 429`() {
        clearRateLimitKeys()
        // Register limit is 2 per window
        repeat(2) { idx ->
            mockMvc.perform(
                post("/api/v1/auth/register", mapOf(
                    "clinicName" to "RL Clinic $idx ${System.currentTimeMillis()}",
                    "clinicSlug" to "rl-clinic-${System.currentTimeMillis()}-$idx",
                    "firstName" to "Test",
                    "lastName" to "User",
                    "email" to "rl-reg-$idx-${System.currentTimeMillis()}@cliniq.test",
                    "password" to "Password123!",
                    "phone" to "+25470000000$idx"
                ))
            ).andExpect(status().isCreated)
        }
        // 3rd should be rate limited
        mockMvc.perform(
            post("/api/v1/auth/register", mapOf(
                "clinicName" to "RL Clinic X ${System.currentTimeMillis()}",
                "clinicSlug" to "rl-clinic-x-${System.currentTimeMillis()}",
                "firstName" to "Test",
                "lastName" to "User",
                "email" to "rl-reg-x-${System.currentTimeMillis()}@cliniq.test",
                "password" to "Password123!",
                "phone" to "+254700000009"
            ))
        ).andExpect(status().`is`(429))
    }

    @Test
    fun `rate limit expires after clearing redis`() {
        clearRateLimitKeys()
        val email = "expiry-${System.currentTimeMillis()}@cliniq.test"
        repeat(3) {
            mockMvc.perform(post("/api/v1/auth/login", mapOf("email" to email, "password" to "WrongPass123!")))
                .andExpect(status().isUnauthorized)
        }
        mockMvc.perform(post("/api/v1/auth/login", mapOf("email" to email, "password" to "WrongPass123!")))
            .andExpect(status().`is`(429))
        // Simulate expiry by clearing
        clearRateLimitKeys()
        mockMvc.perform(post("/api/v1/auth/login", mapOf("email" to email, "password" to "WrongPass123!")))
            .andExpect(status().isUnauthorized) // not 429 anymore
    }

    @Test
    fun `authenticated endpoints are not accidentally rate limited`() {
        clearRateLimitKeys()
        // Get a valid token BEFORE exhausting limit
        val token = login("admin@cliniq.app", "Demo1234!")
        clearRateLimitKeys()
        // Exhaust login limit for a different email/IP
        val email = "auth-not-limited-${System.currentTimeMillis()}@cliniq.test"
        repeat(3) {
            mockMvc.perform(post("/api/v1/auth/login", mapOf("email" to email, "password" to "WrongPass123!")))
                .andExpect(status().isUnauthorized)
        }
        // Authenticated endpoint must still work even though login is rate limited
        repeat(5) {
            mockMvc.perform(get("/api/v1/auth/me", token))
                .andExpect(status().isOk)
        }
    }
}
