package com.cliniq.domain.auth.security

import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(private val jwt: JwtService) : OncePerRequestFilter() {

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val header = req.getHeader("Authorization")

        if (header != null && header.startsWith("Bearer ")) {
            val token = header.removePrefix("Bearer ").trim()
            try {
                val claims = jwt.parseClaims(token)
                if (!jwt.isAccess(claims)) {
                    deny(res, "Refresh token cannot be used for API access")
                    return
                }
                if (jwt.isBlacklisted(token)) {
                    deny(res, "Token has been revoked")
                    return
                }
                SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(
                    userId = jwt.userId(claims),
                    email = jwt.email(claims),
                    clinicId = jwt.clinicId(claims),
                    role = jwt.role(claims)
                )
            } catch (e: JwtException) {
                deny(res, "Invalid or expired token")
                return
            } catch (e: IllegalArgumentException) {
                deny(res, "Malformed token")
                return
            }
        }
        chain.doFilter(req, res)
    }

    private fun deny(res: HttpServletResponse, message: String) {
        res.status = 401
        res.contentType = "application/json"
        res.writer.write("""{"success":false,"error":{"code":"UNAUTHORIZED","message":"$message"}}""")
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
