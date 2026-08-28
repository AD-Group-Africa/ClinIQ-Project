package com.cliniq.shared.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * RequestIdFilter
 *
 * Ensures every request has a correlation id in:
 * - the `X-Request-Id` response header
 * - the SLF4J MDC under the key `requestId`
 *
 * The id is taken from an inbound `X-Request-Id` header if present, otherwise
 * a UUID is generated. This is the lowest-cost, highest-leverage observability
 * change we can make.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val requestId = req.getHeader("X-Request-Id")?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        MDC.put("requestId", requestId)
        res.setHeader("X-Request-Id", requestId)
        try {
            chain.doFilter(req, res)
        } finally {
            MDC.remove("requestId")
        }
    }
}
