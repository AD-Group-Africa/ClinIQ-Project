package com.cliniq.domain.communication.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class CommChannel { SMS, WHATSAPP, EMAIL, IN_APP, PUSH }
enum class CommStatus  { PENDING, SENT, DELIVERED, FAILED, READ }
enum class CommType {
    APPOINTMENT_REMINDER, APPOINTMENT_CONFIRMATION, APPOINTMENT_CANCELLATION,
    FOLLOW_UP, BILLING_REMINDER, CAMPAIGN, GENERAL
}

@Entity
@Table(name = "communications")
data class Communication(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @Column(name = "patient_id", nullable = false)
    val patientId: UUID,

    @Enumerated(EnumType.STRING) val channel: CommChannel,
    @Enumerated(EnumType.STRING) val type: CommType,

    @Column val subject: String? = null,
    @Column(nullable = false, columnDefinition = "TEXT") val body: String,

    @Enumerated(EnumType.STRING) var status: CommStatus = CommStatus.PENDING,

    @Column(name = "external_ref")  var externalRef: String? = null,
    @Column(name = "sent_at")       var sentAt: Instant? = null,
    @Column(name = "delivered_at")  var deliveredAt: Instant? = null,
    @Column(name = "failed_reason") var failedReason: String? = null,

    @Column(name = "appointment_id") val appointmentId: UUID? = null,

    @Column(name = "created_at") val createdAt: Instant = Instant.now()
)
