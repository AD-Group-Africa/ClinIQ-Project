package com.cliniq.domain.auth.dto

import com.cliniq.domain.auth.entity.UserRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class LoginRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String
)

data class RegisterClinicRequest(
    @field:NotBlank val clinicName: String,
    @field:NotBlank @field:Size(min = 3, max = 60) val clinicSlug: String,
    val clinicPhone: String? = null,
    @field:NotBlank val firstName: String,
    @field:NotBlank val lastName: String,
    @field:Email @field:NotBlank val email: String,
    @field:Size(min = 8) val password: String,
    val phone: String? = null
)

data class RegisterStaffRequest(
    @field:NotBlank val firstName: String,
    @field:NotBlank val lastName: String,
    @field:Email @field:NotBlank val email: String,
    @field:Size(min = 8) val password: String,
    val phone: String? = null,
    val role: UserRole = UserRole.RECEPTIONIST
)

data class RefreshTokenRequest(@field:NotBlank val refreshToken: String)

data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:Size(min = 8) val newPassword: String
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
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
