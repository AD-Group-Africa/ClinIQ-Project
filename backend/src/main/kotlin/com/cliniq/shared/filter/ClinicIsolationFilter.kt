package com.cliniq.shared.filter

import com.cliniq.domain.auth.entity.UserRole
import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.shared.context.ClinicContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * ClinicIsolationFilter
 *
 * Guarantees that every authenticated request is bound to a single clinic and
 * that the binding can ONLY be derived from the signed access token. The
 * `X-Clinic-ID` header is treated as advisory: it is verified against the
 * JWT's clinicId for non-super-admin tokens, and never used as a primary
 * source of clinic context.
 *
 * Why: Clients must not be able to switch clinic context by sending a header.
 * The token is the only trusted source.
 *
 * Not a Spring bean on purpose: it is constructed inside the security filter
 * chain (see SecurityConfig) so Spring Boot never auto-registers it as a
 * servlet filter.
 */
class ClinicIsolationFilter : OncePerRequestFilter() {

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val auth = SecurityContextHolder.getContext().authentication

        if (auth is JwtAuthenticationToken) {
            val tokenClinicId = auth.clinicId
            val requestClinicId = req.getHeader("X-Clinic-ID")?.let { runCatching { UUID.fromString(it) }.getOrNull() }

            if (auth.role != UserRole.SUPER_ADMIN) {
                if (requestClinicId != null && tokenClinicId != null && requestClinicId != tokenClinicId) {
                    deny(res, "Clinic context mismatch")
                    return
                }
                if (tokenClinicId == null) {
                    deny(res, "Token has no clinic context")
                    return
                }
            }

            val effectiveClinicId = if (auth.role == UserRole.SUPER_ADMIN) {
                requestClinicId ?: tokenClinicId
            } else {
                tokenClinicId
            }
            if (effectiveClinicId != null) {
                ClinicContext.set(effectiveClinicId)
            }
        }

        try {
            chain.doFilter(req, res)
        } finally {
            ClinicContext.clear()
        }
    }

    private fun deny(res: HttpServletResponse, message: String) {
        res.status = 403
        res.contentType = "application/json"
        res.writer.write("""{"success":false,"error":{"code":"FORBIDDEN","message":"$message"}}""")
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        // Public auth endpoints and public/actuator/swagger paths have no clinic
        // context. Authenticated auth endpoints (/me, /logout, /password) are
        // included so the clinic context is available if needed.
        return path.startsWith("/api/v1/auth/login") ||
               path.startsWith("/api/v1/auth/register") ||
               path.startsWith("/api/v1/auth/refresh") ||
               path.startsWith("/api/v1/public/") ||
               path.startsWith("/actuator") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs")
    }
}
