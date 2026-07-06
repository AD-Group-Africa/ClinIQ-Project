package com.cliniq.domain.appointment.service

import com.cliniq.domain.appointment.dto.QueueItem
import com.cliniq.domain.appointment.dto.QueueSummary
import com.cliniq.domain.appointment.entity.Appointment
import com.cliniq.domain.appointment.entity.AppointmentStatus
import com.cliniq.domain.appointment.repository.AppointmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional(readOnly = true)
class QueueService(private val appointments: AppointmentRepository) {

    fun getLiveQueue(clinicId: UUID): QueueSummary {
        val today = appointments.findByClinicAndDate(clinicId, LocalDate.now())

        val waiting    = today.filter { it.status == AppointmentStatus.CHECKED_IN }
            .sortedBy { it.queueNumber ?: Int.MAX_VALUE }
        val inProgress = today.filter { it.status == AppointmentStatus.IN_CONSULTATION }
        val completed  = today.count { it.status == AppointmentStatus.COMPLETED }
        val upcoming   = today.count { it.status in listOf(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED) }

        // Rough estimate: 15 min average consult time per waiting patient ahead
        val estimatedWait = waiting.size * 15

        return QueueSummary(
            waiting = waiting.map { it.toQueueItem() },
            inProgress = inProgress.map { it.toQueueItem() },
            completedCount = completed,
            upcomingCount = upcoming,
            estimatedWaitMinutes = estimatedWait
        )
    }

    private fun Appointment.toQueueItem() = QueueItem(
        id = id, queueNumber = queueNumber, patientName = patient.fullName,
        patientPhone = patient.phone, doctorName = doctor?.fullName,
        time = startTime, status = status, type = type
    )
}
