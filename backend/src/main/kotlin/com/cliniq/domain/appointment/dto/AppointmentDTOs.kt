package com.cliniq.domain.appointment.dto

import com.cliniq.domain.appointment.entity.AppointmentStatus
import com.cliniq.domain.appointment.entity.AppointmentType
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class CreateAppointmentRequest(
    @field:NotNull val patientId: UUID,
    @field:NotNull val doctorId: UUID,
    @field:NotNull val appointmentDate: LocalDate,
    @field:NotNull val startTime: LocalTime,
    val type: AppointmentType = AppointmentType.CONSULTATION,
    val chiefComplaint: String? = null
)

data class CancelRequest(val reason: String = "Cancelled by staff")

data class AppointmentResponse(
    val id: UUID,
    val patientId: UUID,
    val patientName: String,
    val patientPhone: String,
    val doctorId: UUID?,
    val doctorName: String?,
    val appointmentDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val status: AppointmentStatus,
    val type: AppointmentType,
    val queueNumber: Int?,
    val chiefComplaint: String?,
    val createdAt: Instant
)

data class TimeSlot(val startTime: LocalTime, val endTime: LocalTime)

data class QueueItem(
    val id: UUID,
    val queueNumber: Int?,
    val patientName: String,
    val patientPhone: String,
    val doctorName: String?,
    val time: LocalTime,
    val status: AppointmentStatus,
    val type: AppointmentType
)

data class QueueSummary(
    val waiting: List<QueueItem>,
    val inProgress: List<QueueItem>,
    val completedCount: Int,
    val upcomingCount: Int,
    val estimatedWaitMinutes: Int
)
