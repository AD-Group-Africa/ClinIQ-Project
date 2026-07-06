package com.cliniq.domain.auth.security

import com.cliniq.domain.auth.entity.UserRole
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.expiration-ms:900000}") private val accessExpMs: Long,
    @Value("\${jwt.refresh-expiration-ms:604800000}") private val refreshExpMs: Long,
    private val redis: StringRedisTemplate
) {
    private val key: SecretKey = resolveKey(secret)

    private fun resolveKey(secret: String): SecretKey =
        try {
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))
        } catch (e: Exception) {
            Keys.hmacShaKeyFor(secret.toByteArray())
        }

    fun generateAccessToken(userId: UUID, email: String, clinicId: UUID?, role: UserRole): String =
        Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("clinicId", clinicId?.toString())
            .claim("role", role.name)
            .claim("type", "access")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + accessExpMs))
            .signWith(key)
            .compact()

    fun generateRefreshToken(userId: UUID): String {
        val token = Jwts.builder()
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + refreshExpMs))
            .signWith(key)
            .compact()
        redis.opsForValue().set("refresh:$userId", token, refreshExpMs, TimeUnit.MILLISECONDS)
        return token
    }

    fun parseClaims(token: String): Claims =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload

    fun isBlacklisted(token: String): Boolean =
        redis.hasKey("blacklist:${tokenHash(token)}") == true

    fun blacklist(token: String, ttlMs: Long) {
        if (ttlMs > 0) redis.opsForValue().set("blacklist:${tokenHash(token)}", "1", ttlMs, TimeUnit.MILLISECONDS)
    }

    fun revokeRefreshToken(userId: UUID) {
        redis.delete("refresh:$userId")
    }

    fun isRefreshTokenValid(userId: UUID, token: String): Boolean =
        redis.opsForValue().get("refresh:$userId") == token

    fun accessTokenTtlSeconds(): Long = accessExpMs / 1000

    fun userId(c: Claims): UUID    = UUID.fromString(c.subject)
    fun email(c: Claims): String   = c["email"] as String
    fun role(c: Claims): UserRole  = UserRole.valueOf(c["role"] as String)
    fun clinicId(c: Claims): UUID? = (c["clinicId"] as? String)?.let { UUID.fromString(it) }
    fun isAccess(c: Claims): Boolean  = c["type"] == "access"
    fun isRefresh(c: Claims): Boolean = c["type"] == "refresh"

    private fun tokenHash(token: String): Int = token.hashCode()
}
