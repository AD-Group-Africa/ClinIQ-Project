package com.cliniq.shared.filter

import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.shared.context.ClinicContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class ClinicIsolationFilter : OncePerRequestFilter() {

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val auth = SecurityContextHolder.getContext().authentication

        if (auth is JwtAuthenticationToken) {
            val tokenClinicId = auth.clinicId?.toString()
            val requestClinicId = req.getHeader("X-Clinic-ID")

            if (auth.role.name != "SUPER_ADMIN") {
                if (tokenClinicId != null && requestClinicId != null && tokenClinicId != requestClinicId) {
                    res.status = 403
                    res.contentType = "application/json"
                    res.writer.write("""{"success":false,"error":{"code":"FORBIDDEN","message":"Clinic access denied"}}""")
                    return
                }
            }

            val clinicId = tokenClinicId ?: requestClinicId
            if (clinicId != null) {
                try { ClinicContext.set(UUID.fromString(clinicId)) } catch (_: Exception) {}
            }
        }

        try {
            chain.doFilter(req, res)
        } finally {
            ClinicContext.clear()
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return path.startsWith("/api/v1/auth/") ||
               path.startsWith("/api/v1/public/") ||
               path.startsWith("/actuator") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs")
    }
}
