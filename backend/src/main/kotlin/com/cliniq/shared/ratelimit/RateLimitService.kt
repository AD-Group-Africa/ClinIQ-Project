package com.cliniq.shared.ratelimit

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.TimeUnit

@Service
class RateLimitService(
    private val redis: StringRedisTemplate
) {
    @Value("\${app.auth-rate-limit.enabled:true}")
    var enabled: Boolean = true

    @Value("\${app.auth-rate-limit.login.max-attempts:5}")
    var loginMaxAttempts: Int = 5

    @Value("\${app.auth-rate-limit.login.window-seconds:300}")
    var loginWindowSeconds: Long = 300

    @Value("\${app.auth-rate-limit.register.max-attempts:3}")
    var registerMaxAttempts: Int = 3

    @Value("\${app.auth-rate-limit.register.window-seconds:3600}")
    var registerWindowSeconds: Long = 3600

    /**
     * Returns true if the request is allowed, false if rate limited.
     * Increments counter atomically and sets TTL on first hit.
     * Fails open if Redis is unavailable.
     */
    fun isAllowed(key: String, limit: Int, windowSeconds: Long): Boolean {
        if (!enabled) return true
        return try {
            val count = redis.opsForValue().increment(key) ?: 1L
            if (count == 1L) {
                redis.expire(key, Duration.ofSeconds(windowSeconds))
            }
            count <= limit
        } catch (_: Exception) {
            true
        }
    }

    fun checkLoginAllowed(ip: String, email: String): Boolean {
        if (!enabled) return true
        val ipKey = "rl:login:ip:${sanitize(ip)}"
        val emailKey = "rl:login:email:${sanitize(email.lowercase())}"
        // Check both dimensions; fail if either exceeds limit
        val ipAllowed = isAllowed(ipKey, loginMaxAttempts, loginWindowSeconds)
        val emailAllowed = isAllowed(emailKey, loginMaxAttempts, loginWindowSeconds)
        return ipAllowed && emailAllowed
    }

    fun checkRegisterAllowed(ip: String): Boolean {
        if (!enabled) return true
        val key = "rl:register:ip:${sanitize(ip)}"
        return isAllowed(key, registerMaxAttempts, registerWindowSeconds)
    }

    fun remainingTtlSeconds(key: String): Long {
        return redis.getExpire(key, TimeUnit.SECONDS).coerceAtLeast(0)
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9@._-]"), "_").take(120)
}
