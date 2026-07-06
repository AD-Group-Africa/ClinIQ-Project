package com.cliniq.domain.crm.entity

import com.cliniq.domain.communication.entity.CommChannel
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "crm_campaigns")
data class CrmCampaign(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID = UUID.randomUUID(),
    @Column(name = "clinic_id") val clinicId: UUID,
    @Column var name: String,
    @Column var description: String? = null,
    @Enumerated(EnumType.STRING) var channel: CommChannel,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "target_segment", columnDefinition = "jsonb") var targetSegment: Map<String, Any> = emptyMap(),
    @Column(name = "message_template", columnDefinition = "TEXT") var messageTemplate: String,
    @Column(name = "scheduled_at") var scheduledAt: Instant? = null,
    @Column var status: String = "DRAFT",
    @Column(name = "sent_count") var sentCount: Int = 0,
    @Column(name = "delivered_count") var deliveredCount: Int = 0,
    @Column(name = "failed_count") var failedCount: Int = 0,
    @Column(name = "created_by") val createdBy: UUID? = null,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now()
)
