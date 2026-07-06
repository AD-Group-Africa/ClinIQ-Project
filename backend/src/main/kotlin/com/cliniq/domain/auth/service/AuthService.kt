package com.cliniq.domain.auth.service

import com.cliniq.domain.auth.dto.*
import com.cliniq.domain.auth.entity.User
import com.cliniq.domain.auth.entity.UserRole
import com.cliniq.domain.auth.repository.UserRepository
import com.cliniq.domain.auth.security.JwtService
import com.cliniq.domain.clinic.entity.Clinic
import com.cliniq.domain.clinic.repository.ClinicRepository
import com.cliniq.shared.audit.AuditService
import com.cliniq.shared.exceptions.ConflictException
import com.cliniq.shared.exceptions.ForbiddenException
import com.cliniq.shared.exceptions.NotFoundException
import com.cliniq.shared.exceptions.UnauthorizedException
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class AuthService(
    private val users: UserRepository,
    private val clinics: ClinicRepository,
    private val jwt: JwtService,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val audit: AuditService
) {

    fun login(req: LoginRequest, ip: String?): TokenResponse {
        try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(req.email, req.password)
            )
        } catch (e: BadCredentialsException) {
            throw UnauthorizedException("Invalid email or password")
        }

        val user = users.findByEmail(req.email) ?: throw UnauthorizedException("Invalid email or password")
        if (!user.isActive) throw ForbiddenException("Account is deactivated")

        user.lastLoginAt = Instant.now()
        users.save(user)

        user.clinicId?.let { audit.log(it, user.id, "LOGIN", "user", user.id, ipAddress = ip) }

        return issueTokens(user)
    }

    fun registerClinic(req: RegisterClinicRequest): TokenResponse {
        if (users.existsByEmail(req.email)) throw ConflictException("Email already registered")
        if (clinics.existsBySlug(req.clinicSlug)) throw ConflictException("Clinic slug already taken")

        val clinic = clinics.save(
            Clinic(
                name = req.clinicName,
                slug = req.clinicSlug.lowercase(),
                phone = req.clinicPhone,
                email = req.email
            )
        )

        val admin = users.save(
            User(
                clinicId = clinic.id,
                email = req.email,
                passwordHash = passwordEncoder.encode(req.password),
                firstName = req.firstName,
                lastName = req.lastName,
                phone = req.phone,
                role = UserRole.CLINIC_ADMIN,
                isVerified = true
            )
        )

        audit.log(clinic.id, admin.id, "REGISTER_CLINIC", "clinic", clinic.id)
        return issueTokens(admin)
    }

    fun registerStaff(req: RegisterStaffRequest, clinicId: UUID, registeredBy: UUID): UserProfile {
        if (users.existsByEmail(req.email)) throw ConflictException("Email already registered")

        val staff = users.save(
            User(
                clinicId = clinicId,
                email = req.email,
                passwordHash = passwordEncoder.encode(req.password),
                firstName = req.firstName,
                lastName = req.lastName,
                phone = req.phone,
                role = req.role,
                isVerified = true
            )
        )

        audit.log(clinicId, registeredBy, "REGISTER_STAFF", "user", staff.id,
            newValues = mapOf("email" to staff.email, "role" to staff.role.name))

        return staff.toProfile()
    }

    fun refresh(req: RefreshTokenRequest): TokenResponse {
        val claims = try { jwt.parseClaims(req.refreshToken) } catch (e: Exception) {
            throw UnauthorizedException("Invalid refresh token")
        }
        if (!jwt.isRefresh(claims)) throw UnauthorizedException("Not a refresh token")

        val userId = jwt.userId(claims)
        if (!jwt.isRefreshTokenValid(userId, req.refreshToken)) {
            throw UnauthorizedException("Refresh token expired or revoked")
        }

        val user = users.findById(userId).orElseThrow { UnauthorizedException("User not found") }
        if (!user.isActive) throw ForbiddenException("Account is deactivated")

        // Rotate: revoke old, issue new
        jwt.revokeRefreshToken(userId)
        return issueTokens(user)
    }

    fun logout(userId: UUID, accessToken: String?) {
        jwt.revokeRefreshToken(userId)
        if (accessToken != null) {
            try {
                val claims = jwt.parseClaims(accessToken)
                val ttl = claims.expiration.time - System.currentTimeMillis()
                jwt.blacklist(accessToken, ttl)
            } catch (_: Exception) { /* token already invalid */ }
        }
    }

    fun changePassword(userId: UUID, req: ChangePasswordRequest) {
        val user = users.findById(userId).orElseThrow { NotFoundException("User not found") }
        if (!passwordEncoder.matches(req.currentPassword, user.passwordHash)) {
            throw UnauthorizedException("Current password is incorrect")
        }
        user.passwordHash = passwordEncoder.encode(req.newPassword)
        user.updatedAt = Instant.now()
        users.save(user)
        jwt.revokeRefreshToken(userId)
    }

    fun me(userId: UUID): UserProfile =
        users.findById(userId).orElseThrow { NotFoundException("User not found") }.toProfile()

    private fun issueTokens(user: User): TokenResponse = TokenResponse(
        accessToken = jwt.generateAccessToken(user.id, user.email, user.clinicId, user.role),
        refreshToken = jwt.generateRefreshToken(user.id),
        expiresIn = jwt.accessTokenTtlSeconds(),
        user = user.toProfile()
    )

    private fun User.toProfile() = UserProfile(id, email, firstName, lastName, role, clinicId, avatarUrl)
}
