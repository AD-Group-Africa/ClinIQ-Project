package com.cliniq.shared.ratelimit

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter

class AuthRateLimitFilter(
    private val rateLimitService: RateLimitService,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val path = request.requestURI
        val method = request.method

        if (method != "POST" || !rateLimitService.enabled) {
            chain.doFilter(request, response)
            return
        }

        val ip = extractIp(request)

        when {
            path == "/api/v1/auth/login" -> {
                val wrapped = CachedBodyRequestWrapper(request)
                val email = extractEmail(wrapped) ?: ""
                if (!rateLimitService.checkLoginAllowed(ip, email)) {
                    rateLimited(response, "Too many login attempts. Please try again later.")
                    return
                }
                chain.doFilter(wrapped, response)
                return
            }
            path == "/api/v1/auth/register" -> {
                if (!rateLimitService.checkRegisterAllowed(ip)) {
                    rateLimited(response, "Too many registration attempts. Please try again later.")
                    return
                }
                chain.doFilter(request, response)
                return
            }
        }
        chain.doFilter(request, response)
    }

    private fun extractIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.split(",").first().trim()
        }
        return request.remoteAddr ?: "unknown"
    }

    /**
     * Extracts email without storing password or full body.
     */
    private fun extractEmail(wrapper: CachedBodyRequestWrapper): String? {
        return try {
            val body = wrapper.cachedBody.toString(Charsets.UTF_8)
            if (body.isBlank()) return null
            val node = objectMapper.readTree(body)
            node.path("email").asText(null)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun rateLimited(response: HttpServletResponse, message: String) {
        response.status = 429
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.setHeader("Retry-After", "60")
        response.writer.write(
            """{"success":false,"error":{"code":"TOO_MANY_REQUESTS","message":"$message"}}"""
        )
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val p = request.requestURI
        return p != "/api/v1/auth/login" && p != "/api/v1/auth/register"
    }
}
