package com.cliniq.domain.appointment.entity

import com.cliniq.domain.clinic.entity.Doctor
import com.cliniq.domain.patient.entity.Patient
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class AppointmentStatus {
    SCHEDULED, CONFIRMED, CHECKED_IN, IN_CONSULTATION, COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED
}

enum class AppointmentType {
    CONSULTATION, FOLLOW_UP, PROCEDURE, CHECK_UP, WALK_IN, TELECONSULTATION
}

@Entity
@Table(name = "appointments")
data class Appointment(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    val patient: Patient,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    var doctor: Doctor?,

    @Column(name = "appointment_date") val appointmentDate: LocalDate,
    @Column(name = "start_time") val startTime: LocalTime,
    @Column(name = "end_time")   val endTime: LocalTime,

    @Enumerated(EnumType.STRING) var status: AppointmentStatus = AppointmentStatus.SCHEDULED,
    @Enumerated(EnumType.STRING) val type: AppointmentType = AppointmentType.CONSULTATION,

    @Column(name = "queue_number") var queueNumber: Int? = null,
    @Column(name = "chief_complaint") val chiefComplaint: String? = null,

    @Column(name = "booked_via") val bookedVia: String = "PORTAL",
    @Column(name = "booked_by")  val bookedBy: UUID? = null,

    @Column(name = "reminder_sent") var reminderSent: Boolean = false,
    @Column(name = "cancellation_reason") var cancellationReason: String? = null,
    @Column(name = "rescheduled_from") val rescheduledFrom: UUID? = null,

    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "reminders")
data class Reminder(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id") val clinicId: UUID,
    @Column(name = "appointment_id") val appointmentId: UUID,
    @Column(name = "patient_id") val patientId: UUID,

    @Column(name = "send_at") val sendAt: Instant,

    @Enumerated(EnumType.STRING)
    val channel: com.cliniq.domain.communication.entity.CommChannel,

    @Column(name = "is_sent") var isSent: Boolean = false,
    @Column(name = "sent_at") var sentAt: Instant? = null,

    @Column(name = "created_at") val createdAt: Instant = Instant.now()
)
