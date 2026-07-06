package com.cliniq.domain.consultation.entity

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "consultations")
data class Consultation(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),
    @Column(name = "clinic_id", nullable = false) val clinicId: UUID,
    @Column(name = "appointment_id") val appointmentId: UUID? = null,
    @Column(name = "patient_id", nullable = false) val patientId: UUID,
    @Column(name = "doctor_id") val doctorId: UUID? = null,
    @Column(name = "visit_number") var visitNumber: Int = 1,
    @Column(name = "chief_complaint") var chiefComplaint: String? = null,
    @Column var history: String? = null,
    @Column var examination: String? = null,
    @Column var diagnosis: String? = null,
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(name = "diagnosis_codes", columnDefinition = "text[]")
    var diagnosisCodes: Array<String> = emptyArray(),
    @Column(name = "treatment_plan") var treatmentPlan: String? = null,
    @Column var notes: String? = null,
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") var vitals: Map<String, Any?> = emptyMap(),
    @Column(name = "follow_up_date") var followUpDate: LocalDate? = null,
    @Column(name = "follow_up_notes") var followUpNotes: String? = null,
    @Column(name = "is_admitted") var isAdmitted: Boolean = false,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "prescriptions")
data class Prescription(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),
    @Column(name = "clinic_id", nullable = false) val clinicId: UUID,
    @Column(name = "consultation_id", nullable = false) val consultationId: UUID,
    @Column(name = "patient_id", nullable = false) val patientId: UUID,
    @Column(name = "doctor_id") val doctorId: UUID? = null,
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
    var medications: List<Map<String, Any?>> = emptyList(),
    @Column var notes: String? = null,
    @Column(name = "is_dispensed") var isDispensed: Boolean = false,
    @Column(name = "created_at") val createdAt: Instant = Instant.now()
)
