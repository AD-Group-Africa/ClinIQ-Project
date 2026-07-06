package com.cliniq.domain.lab.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "lab_requests")
data class LabRequest(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID = UUID.randomUUID(),
    @Column(name = "clinic_id", nullable = false) val clinicId: UUID,
    @Column(name = "patient_id", nullable = false) val patientId: UUID,
    @Column(name = "doctor_id") val doctorId: UUID? = null,
    @Column(name = "consultation_id") val consultationId: UUID? = null,
    @Column(name = "request_number", unique = true) var requestNumber: String? = null,
    @Enumerated(EnumType.STRING) var status: LabRequestStatus = LabRequestStatus.REQUESTED,
    @Enumerated(EnumType.STRING) var priority: LabPriority = LabPriority.ROUTINE,
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") var tests: List<Map<String, Any>> = emptyList(),
    @Column(name = "total_price") var totalPrice: BigDecimal = BigDecimal.ZERO,
    @Column(name = "clinical_notes") var clinicalNotes: String? = null,
    @Column(name = "sample_type") var sampleType: String? = null,
    @Column(name = "sample_collected_at") var sampleCollectedAt: Instant? = null,
    @Column(name = "due_at") var dueAt: Instant? = null,
    @Column(name = "completed_at") var completedAt: Instant? = null,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now()
)

enum class LabRequestStatus { REQUESTED, SAMPLE_COLLECTED, PROCESSING, COMPLETED, CANCELLED }
enum class LabPriority { ROUTINE, URGENT, STAT }
