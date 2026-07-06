package com.cliniq.domain.clinic.entity

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "clinics")
data class Clinic(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false, unique = true)
    val slug: String,

    @Column var phone: String? = null,
    @Column var email: String? = null,
    @Column var address: String? = null,
    @Column var city: String? = null,
    @Column var country: String = "KE",

    @Column(name = "logo_url") var logoUrl: String? = null,
    @Column var website: String? = null,
    @Column var timezone: String = "Africa/Nairobi",
    @Column var currency: String = "KES",

    @Column(name = "subscription_tier") var subscriptionTier: String = "starter",
    @Column(name = "subscription_expires_at") var subscriptionExpiresAt: Instant? = null,

    @Column(name = "is_active") var isActive: Boolean = true,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var settings: Map<String, Any?> = emptyMap(),

    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now()
)
