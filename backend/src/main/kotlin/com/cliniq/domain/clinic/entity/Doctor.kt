package com.cliniq.domain.clinic.entity

import com.cliniq.domain.auth.entity.User
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "doctors")
data class Doctor(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    val user: User,

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @Column var specialization: String? = null,
    @Column var qualification: String? = null,
    @Column(name = "license_number") var licenseNumber: String? = null,
    @Column var bio: String? = null,

    @Column(name = "consultation_fee")
    var consultationFee: BigDecimal = BigDecimal.ZERO,

    @Column(name = "is_available")
    var isAvailable: Boolean = true,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
) {
    val fullName get() = "Dr. ${user.firstName} ${user.lastName}"
}

@Entity
@Table(name = "doctor_schedules")
data class DoctorSchedule(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    val doctor: Doctor,

    @Column(name = "clinic_id")
    val clinicId: UUID,

    @Column(name = "day_of_week")
    val dayOfWeek: Int,

    @Column(name = "start_time")
    val startTime: LocalTime,

    @Column(name = "end_time")
    val endTime: LocalTime,

    @Column(name = "slot_duration_minutes")
    val slotDurationMinutes: Int = 30,

    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "schedule_overrides")
data class ScheduleOverride(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    val doctor: Doctor,

    @Column(name = "clinic_id")
    val clinicId: UUID,

    @Column(name = "override_date")
    val overrideDate: java.time.LocalDate,

    @Column(name = "is_available")
    val isAvailable: Boolean = false,

    @Column(name = "start_time") val startTime: LocalTime? = null,
    @Column(name = "end_time")   val endTime: LocalTime? = null,
    @Column val reason: String? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)
