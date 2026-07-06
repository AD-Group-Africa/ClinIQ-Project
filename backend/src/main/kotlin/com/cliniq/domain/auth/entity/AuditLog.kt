package com.cliniq.domain.auth.entity

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_logs")
data class AuditLog(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id")
    val clinicId: UUID,

    @Column(name = "user_id")
    val userId: UUID? = null,

    @Column(nullable = false)
    val action: String,

    @Column(name = "entity_type")
    val entityType: String? = null,

    @Column(name = "entity_id")
    val entityId: UUID? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb")
    val oldValues: Map<String, Any?>? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb")
    val newValues: Map<String, Any?>? = null,

    @Column(name = "ip_address")
    val ipAddress: String? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)
