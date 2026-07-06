package com.cliniq.domain.auth.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class UserRole {
    SUPER_ADMIN, CLINIC_ADMIN, DOCTOR, NURSE, RECEPTIONIST,
    FINANCE, PATIENT, LAB_TECHNICIAN, PHARMACIST
}

@Entity
@Table(name = "users")
data class User(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id")
    val clinicId: UUID? = null,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column
    var phone: String? = null,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(name = "first_name", nullable = false)
    var firstName: String,

    @Column(name = "last_name", nullable = false)
    var lastName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: UserRole,

    @Column(name = "avatar_url")
    var avatarUrl: String? = null,

    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "is_verified")
    var isVerified: Boolean = false,

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
) {
    val fullName get() = "$firstName $lastName"
}
