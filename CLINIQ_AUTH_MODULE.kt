// =============================================================
// ClinIQ — Complete Auth Module
// Package: com.cliniq.domain.auth
// =============================================================
// Files in this module:
//   controller/AuthController.kt
//   service/AuthService.kt
//   service/UserDetailsServiceImpl.kt
//   security/JwtService.kt
//   security/JwtAuthenticationFilter.kt
//   security/JwtAuthenticationToken.kt
//   dto/AuthDTOs.kt
//   repository/UserRepository.kt
//   repository/RefreshTokenRepository.kt
// =============================================================


// =============================================
// dto/AuthDTOs.kt
// =============================================
package com.cliniq.domain.auth.dto

import com.cliniq.domain.auth.entity.UserRole
import jakarta.validation.constraints.*
import java.util.UUID

data class LoginRequest(
    @field:Email(message = "Valid email required")
    @field:NotBlank
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String,

    val clinicSlug: String? = null   // optional — for admin login via slug
)

data class RegisterClinicRequest(
    // Clinic details
    @field:NotBlank val clinicName: String,
    @field:NotBlank @field:Size(min = 3, max = 50) val clinicSlug: String,
    val clinicPhone: String? = null,

    // Admin account
    @field:NotBlank val firstName: String,
    @field:NotBlank val lastName: String,
    @field:Email @field:NotBlank val email: String,
    @field:Size(min = 8, message = "Password must be at least 8 characters") val password: String,
    val phone: String? = null
)

data class RegisterStaffRequest(
    val clinicId: UUID,
    @field:NotBlank val firstName: String,
    @field:NotBlank val lastName: String,
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String,
    val phone: String? = null,
    val role: UserRole = UserRole.RECEPTIONIST
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,   // seconds
    val user: UserProfile
)

data class UserProfile(
    val id: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: UserRole,
    val clinicId: UUID?,
    val avatarUrl: String?
)

data class RefreshRequest(
    @field:NotBlank val refreshToken: String
)

data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:Size(min = 8) val newPassword: String
)

data class ForgotPasswordRequest(
    @field:Email @field:NotBlank val email: String
)

data class ResetPasswordRequest(
    @field:NotBlank val token: String,
    @field:Size(min = 8) val newPassword: String
)


// =============================================
// security/JwtService.kt
// =============================================
package com.cliniq.domain.auth.security

import com.cliniq.domain.auth.entity.UserRole
import io.jsonwebtoken.*
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration-ms:900000}") private val expirationMs: Long,
    @Value("\${jwt.refresh-expiration-ms:604800000}") private val refreshExpirationMs: Long,
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val signingKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))
    }

    // ------------------------------------------------
    // Token generation
    // ------------------------------------------------

    fun generateAccessToken(userId: UUID, email: String, clinicId: UUID?, role: UserRole): String {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("clinicId", clinicId?.toString())
            .claim("role", role.name)
            .claim("type", "access")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expirationMs))
            .signWith(signingKey)
            .compact()
    }

    fun generateRefreshToken(userId: UUID): String {
        val token = Jwts.builder()
            .subject(userId.toString())
            .claim("type", "refresh")
            .claim("jti", UUID.randomUUID().toString())
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + refreshExpirationMs))
            .signWith(signingKey)
            .compact()

        // Store in Redis — single active refresh token per user (rotation)
        redisTemplate.opsForValue().set(
            "refresh:token:$userId",
            token,
            refreshExpirationMs,
            TimeUnit.MILLISECONDS
        )
        return token
    }

    // ------------------------------------------------
    // Token validation
    // ------------------------------------------------

    fun validateAndParseClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    fun isAccessToken(claims: Claims): Boolean = claims["type"] == "access"
    fun isRefreshToken(claims: Claims): Boolean = claims["type"] == "refresh"

    fun isRefreshTokenValid(userId: UUID, token: String): Boolean {
        val stored = redisTemplate.opsForValue().get("refresh:token:$userId")
        return stored != null && stored == token
    }

    // ------------------------------------------------
    // Token revocation
    // ------------------------------------------------

    fun revokeRefreshToken(userId: UUID) {
        redisTemplate.delete("refresh:token:$userId")
        log.info("Refresh token revoked for user {}", userId)
    }

    fun blacklistAccessToken(token: String, ttlMs: Long) {
        // Blacklist until expiry so the token can't be reused after logout
        redisTemplate.opsForValue().set(
            "blacklist:access:${token.take(32)}",
            "revoked",
            ttlMs,
            TimeUnit.MILLISECONDS
        )
    }

    fun isAccessTokenBlacklisted(token: String): Boolean {
        return redisTemplate.hasKey("blacklist:access:${token.take(32)}")
    }

    // ------------------------------------------------
    // Helpers
    // ------------------------------------------------

    fun extractUserId(claims: Claims): UUID = UUID.fromString(claims.subject)
    fun extractEmail(claims: Claims): String = claims["email"] as String
    fun extractRole(claims: Claims): UserRole = UserRole.valueOf(claims["role"] as String)
    fun extractClinicId(claims: Claims): UUID? = (claims["clinicId"] as? String)?.let { UUID.fromString(it) }
    fun getAccessTokenExpirationSec(): Long = expirationMs / 1000
}


// =============================================
// security/JwtAuthenticationToken.kt
// =============================================
package com.cliniq.domain.auth.security

import com.cliniq.domain.auth.entity.UserRole
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.UUID

class JwtAuthenticationToken(
    val userId: UUID,
    val email: String,
    val clinicId: UUID?,
    val role: UserRole
) : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_${role.name}"))) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null
    override fun getPrincipal(): Any = email
}


// =============================================
// security/JwtAuthenticationFilter.kt
// =============================================
package com.cliniq.domain.auth.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val token = extractToken(request)

        if (token != null) {
            try {
                val claims = jwtService.validateAndParseClaims(token)

                if (!jwtService.isAccessToken(claims)) {
                    sendError(response, "Refresh token cannot be used for API access")
                    return
                }

                if (jwtService.isAccessTokenBlacklisted(token)) {
                    sendError(response, "Token has been revoked")
                    return
                }

                val auth = JwtAuthenticationToken(
                    userId = jwtService.extractUserId(claims),
                    email = jwtService.extractEmail(claims),
                    clinicId = jwtService.extractClinicId(claims),
                    role = jwtService.extractRole(claims)
                )

                SecurityContextHolder.getContext().authentication = auth

            } catch (e: ExpiredJwtException) {
                log.debug("JWT expired: {}", e.message)
                sendError(response, "Token expired", 401)
                return
            } catch (e: JwtException) {
                log.warn("Invalid JWT: {}", e.message)
                sendError(response, "Invalid token", 401)
                return
            }
        }

        chain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.removePrefix("Bearer ").trim().takeIf { it.isNotBlank() }
    }

    private fun sendError(
        response: HttpServletResponse,
        message: String,
        status: Int = HttpServletResponse.SC_UNAUTHORIZED
    ) {
        response.status = status
        response.contentType = "application/json"
        response.writer.write("""{"success":false,"error":{"code":"UNAUTHORIZED","message":"$message"}}""")
    }
}


// =============================================
// service/UserDetailsServiceImpl.kt
// =============================================
package com.cliniq.domain.auth.service

import com.cliniq.domain.auth.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.*
import org.springframework.stereotype.Service

@Service
class UserDetailsServiceImpl(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(email: String): UserDetails {
        val user = userRepository.findByEmail(email)
            ?: throw UsernameNotFoundException("User not found: $email")

        if (!user.isActive) {
            throw DisabledException("Account is disabled")
        }

        return User(
            user.email,
            user.passwordHash,
            user.isActive,
            true, true, true,
            listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
        )
    }
}


// =============================================
// repository/UserRepository.kt
// =============================================
package com.cliniq.domain.auth.repository

import com.cliniq.domain.auth.entity.User
import com.cliniq.domain.auth.entity.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, UUID> {

    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun existsByPhone(phone: String): Boolean

    @Query("SELECT u FROM User u WHERE u.clinicId = :clinicId ORDER BY u.createdAt DESC")
    fun findAllByClinicId(clinicId: UUID): List<User>

    @Query("SELECT u FROM User u WHERE u.clinicId = :clinicId AND u.role = :role")
    fun findByClinicAndRole(clinicId: UUID, role: UserRole): List<User>

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.clinicId = :clinicId")
    fun findByIdAndClinicId(id: UUID, clinicId: UUID): User?
}


// =============================================
// service/AuthService.kt
// =============================================
package com.cliniq.domain.auth.service

import com.cliniq.domain.auth.dto.*
import com.cliniq.domain.auth.entity.User
import com.cliniq.domain.auth.entity.UserRole
import com.cliniq.domain.auth.repository.UserRepository
import com.cliniq.domain.auth.security.JwtService
import com.cliniq.domain.clinic.entity.Clinic
import com.cliniq.domain.clinic.repository.ClinicRepository
import com.cliniq.shared.audit.AuditService
import com.cliniq.shared.exceptions.*
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val clinicRepository: ClinicRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
    private val authManager: AuthenticationManager,
    private val auditService: AuditService,
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    // ------------------------------------------------
    // Login
    // ------------------------------------------------

    fun login(request: LoginRequest, ipAddress: String): TokenResponse {
        try {
            authManager.authenticate(
                UsernamePasswordAuthenticationToken(request.email, request.password)
            )
        } catch (e: BadCredentialsException) {
            recordFailedLogin(request.email, ipAddress)
            throw UnauthorizedException("Invalid email or password")
        }

        val user = userRepository.findByEmail(request.email)
            ?: throw UnauthorizedException("User not found")

        if (!user.isActive) throw ForbiddenException("Account is suspended. Contact clinic admin.")

        // Update last login
        user.lastLoginAt = Instant.now()
        userRepository.save(user)

        // Audit
        user.clinicId?.let {
            auditService.log(it, user.id, "USER_LOGIN", "user", user.id, ipAddress = ipAddress)
        }

        return buildTokenResponse(user)
    }

    // ------------------------------------------------
    // Clinic self-registration (onboarding)
    // ------------------------------------------------

    fun registerClinic(request: RegisterClinicRequest): TokenResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw ConflictException("Email already registered")
        }
        if (clinicRepository.existsBySlug(request.clinicSlug)) {
            throw ConflictException("Clinic URL '${request.clinicSlug}' is already taken")
        }

        // Create clinic
        val clinic = clinicRepository.save(
            Clinic(
                name = request.clinicName,
                slug = request.clinicSlug.lowercase().trim(),
                phone = request.clinicPhone,
                email = request.email,
                subscriptionTier = "starter"
            )
        )

        // Create clinic admin user
        val admin = userRepository.save(
            User(
                clinicId = clinic.id,
                email = request.email,
                passwordHash = passwordEncoder.encode(request.password),
                firstName = request.firstName,
                lastName = request.lastName,
                phone = request.phone,
                role = UserRole.CLINIC_ADMIN,
                isVerified = false
            )
        )

        log.info("New clinic registered: {} ({})", clinic.name, clinic.slug)
        auditService.log(clinic.id, admin.id, "CLINIC_REGISTERED", "clinic", clinic.id)

        return buildTokenResponse(admin)
    }

    // ------------------------------------------------
    // Staff registration (by clinic admin)
    // ------------------------------------------------

    fun registerStaff(request: RegisterStaffRequest, registeredBy: UUID): UserProfile {
        if (userRepository.existsByEmail(request.email)) {
            throw ConflictException("Email already in use")
        }

        val user = userRepository.save(
            User(
                clinicId = request.clinicId,
                email = request.email,
                passwordHash = passwordEncoder.encode(request.password),
                firstName = request.firstName,
                lastName = request.lastName,
                phone = request.phone,
                role = request.role,
                isVerified = true // staff verified by admin
            )
        )

        auditService.log(request.clinicId, registeredBy, "STAFF_REGISTERED", "user", user.id)
        return user.toProfile()
    }

    // ------------------------------------------------
    // Refresh token
    // ------------------------------------------------

    fun refreshTokens(request: RefreshRequest): TokenResponse {
        val claims = try {
            jwtService.validateAndParseClaims(request.refreshToken)
        } catch (e: Exception) {
            throw UnauthorizedException("Invalid or expired refresh token")
        }

        if (!jwtService.isRefreshToken(claims)) {
            throw UnauthorizedException("Token is not a refresh token")
        }

        val userId = jwtService.extractUserId(claims)

        if (!jwtService.isRefreshTokenValid(userId, request.refreshToken)) {
            // Possible token theft — revoke everything
            jwtService.revokeRefreshToken(userId)
            log.warn("Refresh token reuse detected for user {}", userId)
            throw UnauthorizedException("Token reuse detected. Please log in again.")
        }

        val user = userRepository.findById(userId).orElseThrow {
            UnauthorizedException("User not found")
        }

        if (!user.isActive) throw ForbiddenException("Account is suspended")

        // Rotate: issue new refresh token (old one invalidated automatically)
        return buildTokenResponse(user)
    }

    // ------------------------------------------------
    // Logout
    // ------------------------------------------------

    fun logout(accessToken: String, userId: UUID) {
        // Blacklist current access token for its remaining TTL
        try {
            val claims = jwtService.validateAndParseClaims(accessToken)
            val remainingMs = claims.expiration.time - System.currentTimeMillis()
            if (remainingMs > 0) {
                jwtService.blacklistAccessToken(accessToken, remainingMs)
            }
        } catch (_: Exception) { /* Already expired — nothing to blacklist */ }

        // Revoke refresh token
        jwtService.revokeRefreshToken(userId)
        log.info("User {} logged out", userId)
    }

    // ------------------------------------------------
    // Change password
    // ------------------------------------------------

    fun changePassword(userId: UUID, request: ChangePasswordRequest) {
        val user = userRepository.findById(userId).orElseThrow {
            NotFoundException("User not found")
        }

        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw UnauthorizedException("Current password is incorrect")
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword)
        userRepository.save(user)

        // Invalidate all sessions
        jwtService.revokeRefreshToken(userId)

        user.clinicId?.let {
            auditService.log(it, userId, "PASSWORD_CHANGED", "user", userId)
        }
    }

    // ------------------------------------------------
    // Password reset (forgot password flow)
    // ------------------------------------------------

    fun initiatePasswordReset(request: ForgotPasswordRequest) {
        val user = userRepository.findByEmail(request.email) ?: return // silent — don't reveal existence

        val resetToken = UUID.randomUUID().toString().replace("-", "")
        val key = "pwd:reset:$resetToken"

        // Store token in Redis for 15 minutes
        redisTemplate.opsForValue().set(key, user.id.toString(), 15, TimeUnit.MINUTES)

        // TODO: send reset email via EmailService
        log.info("Password reset requested for {} — token stored in Redis", request.email)
    }

    fun resetPassword(request: ResetPasswordRequest) {
        val key = "pwd:reset:${request.token}"
        val userIdStr = redisTemplate.opsForValue().get(key)
            ?: throw BadRequestException("Reset token is invalid or has expired")

        val user = userRepository.findById(UUID.fromString(userIdStr)).orElseThrow {
            NotFoundException("User not found")
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword)
        userRepository.save(user)
        redisTemplate.delete(key)

        // Invalidate all sessions
        jwtService.revokeRefreshToken(user.id)

        user.clinicId?.let {
            auditService.log(it, user.id, "PASSWORD_RESET", "user", user.id)
        }
    }

    // ------------------------------------------------
    // Helpers
    // ------------------------------------------------

    private fun buildTokenResponse(user: User): TokenResponse {
        val accessToken = jwtService.generateAccessToken(
            userId = user.id,
            email = user.email,
            clinicId = user.clinicId,
            role = user.role
        )
        val refreshToken = jwtService.generateRefreshToken(user.id)

        return TokenResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = jwtService.getAccessTokenExpirationSec(),
            user = user.toProfile()
        )
    }

    private fun recordFailedLogin(email: String, ip: String) {
        val key = "login:fails:${ip}"
        val count = redisTemplate.opsForValue().increment(key) ?: 1L
        if (count == 1L) redisTemplate.expire(key, 15, TimeUnit.MINUTES)
        log.warn("Failed login attempt for {} from {} (attempt {})", email, ip, count)
    }

    private fun User.toProfile() = UserProfile(id, email, firstName, lastName, role, clinicId, avatarUrl)
}


// =============================================
// controller/AuthController.kt
// =============================================
package com.cliniq.domain.auth.controller

import com.cliniq.domain.auth.dto.*
import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.domain.auth.service.AuthService
import com.cliniq.shared.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    // POST /api/v1/auth/login
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val ip = httpRequest.getHeader("X-Forwarded-For")?.split(",")?.first()?.trim()
            ?: httpRequest.remoteAddr
        return ResponseEntity.ok(ApiResponse.success(authService.login(request, ip)))
    }

    // POST /api/v1/auth/register  — self-service clinic onboarding
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterClinicRequest
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        return ResponseEntity.status(201).body(ApiResponse.success(authService.registerClinic(request)))
    }

    // POST /api/v1/auth/refresh
    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshRequest
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        return ResponseEntity.ok(ApiResponse.success(authService.refreshTokens(request)))
    }

    // POST /api/v1/auth/logout
    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal auth: JwtAuthenticationToken,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val token = httpRequest.getHeader("Authorization")?.removePrefix("Bearer ")?.trim() ?: ""
        authService.logout(token, auth.userId)
        return ResponseEntity.ok(ApiResponse.success(Unit))
    }

    // POST /api/v1/auth/staff  — admin registers staff member
    @PostMapping("/staff")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN', 'SUPER_ADMIN')")
    fun registerStaff(
        @Valid @RequestBody request: RegisterStaffRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<UserProfile>> {
        val req = request.copy(clinicId = auth.clinicId ?: request.clinicId)
        return ResponseEntity.status(201).body(ApiResponse.success(authService.registerStaff(req, auth.userId)))
    }

    // PATCH /api/v1/auth/password
    @PatchMapping("/password")
    fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<Unit>> {
        authService.changePassword(auth.userId, request)
        return ResponseEntity.ok(ApiResponse.success(Unit))
    }

    // POST /api/v1/auth/forgot-password
    @PostMapping("/forgot-password")
    fun forgotPassword(
        @Valid @RequestBody request: ForgotPasswordRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        authService.initiatePasswordReset(request)
        return ResponseEntity.ok(ApiResponse.success(Unit)) // always 200 to avoid email enumeration
    }

    // POST /api/v1/auth/reset-password
    @PostMapping("/reset-password")
    fun resetPassword(
        @Valid @RequestBody request: ResetPasswordRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        authService.resetPassword(request)
        return ResponseEntity.ok(ApiResponse.success(Unit))
    }

    // GET /api/v1/auth/me
    @GetMapping("/me")
    fun me(
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<JwtAuthenticationToken>> {
        return ResponseEntity.ok(ApiResponse.success(auth))
    }
}
