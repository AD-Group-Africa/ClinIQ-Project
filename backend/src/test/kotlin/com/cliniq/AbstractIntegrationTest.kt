package com.cliniq

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Base class for integration tests.
 *
 * Two modes:
 * 1. If `DATABASE_URL`/`REDIS_HOST` env vars are set (CI or `docker compose up`),
 *    tests run against that infrastructure.
 * 2. Otherwise disposable PostgreSQL + Redis containers are started via
 *    Testcontainers (requires a working local Docker daemon).
 *
 * Flyway applies V1-V3 and DemoUserSeeder creates the demo accounts
 * (password: `Demo1234!`) for the seeded `demo` clinic.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractIntegrationTest {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    companion object {
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
        private val redis: GenericContainer<*> = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)

        init {
            // Only start disposable containers when no external infrastructure
            // is provided (e.g. CI services or `docker compose up`).
            if (System.getenv("DATABASE_URL").isNullOrBlank()) {
                postgres.start()
                redis.start()
            }
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val externalDbUrl = System.getenv("DATABASE_URL")
            if (!externalDbUrl.isNullOrBlank()) {
                // Use externally provided infrastructure (CI services / docker compose).
                registry.add("spring.datasource.url") { externalDbUrl }
                registry.add("spring.datasource.username") { System.getenv("DATABASE_USER") ?: "cliniq" }
                registry.add("spring.datasource.password") { System.getenv("DATABASE_PASSWORD") ?: "" }
                registry.add("spring.data.redis.host") { System.getenv("REDIS_HOST") ?: "localhost" }
                registry.add("spring.data.redis.port") { System.getenv("REDIS_PORT") ?: "6379" }
                registry.add("spring.data.redis.password") { System.getenv("REDIS_PASSWORD") ?: "" }
            } else {
                registry.add("spring.datasource.url", postgres::getJdbcUrl)
                registry.add("spring.datasource.username", postgres::getUsername)
                registry.add("spring.datasource.password", postgres::getPassword)
                registry.add("spring.data.redis.host", redis::getHost)
                registry.add("spring.data.redis.port") { redis.getMappedPort(6379).toString() }
                registry.add("spring.data.redis.password") { "" }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    protected fun post(path: String, body: Any? = null, token: String? = null): MockHttpServletRequestBuilder {
        val req = MockMvcRequestBuilders.post(path)
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
        return withAuth(req, token)
    }

    protected fun get(path: String, token: String? = null): MockHttpServletRequestBuilder =
        withAuth(MockMvcRequestBuilders.get(path), token)

    protected fun patch(path: String, body: Any? = null, token: String? = null): MockHttpServletRequestBuilder {
        val req = MockMvcRequestBuilders.patch(path)
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
        return withAuth(req, token)
    }

    private fun withAuth(req: MockHttpServletRequestBuilder, token: String?): MockHttpServletRequestBuilder {
        if (token != null) req.header("Authorization", "Bearer $token")
        return req
    }

    /** Logs in and returns the access token. */
    protected fun login(email: String, password: String): String {
        val result: MvcResult = mockMvc.perform(
            post("/api/v1/auth/login", mapOf("email" to email, "password" to password))
        ).andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)
        return json.path("data").path("accessToken").asText()
    }

    protected fun json(result: MvcResult): JsonNode = objectMapper.readTree(result.response.contentAsString)
}