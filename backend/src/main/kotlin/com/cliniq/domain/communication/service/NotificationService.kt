package com.cliniq.domain.communication.service

import com.cliniq.domain.appointment.entity.Appointment
import com.cliniq.domain.clinic.entity.Clinic
import com.cliniq.domain.communication.entity.*
import com.cliniq.domain.communication.repository.CommunicationRepository
import com.cliniq.domain.pharmacy.dto.StockAlertSummary
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class NotificationService(
    private val repo: CommunicationRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun sendAppointmentConfirmation(appt: Appointment) = send(
        clinicId = appt.clinicId,
        patientId = appt.patient.id,
        type = CommType.APPOINTMENT_CONFIRMATION,
        body = "Appointment confirmed for ${appt.patient.firstName} on ${appt.appointmentDate} at ${appt.startTime}. Queue #${appt.queueNumber ?: "TBC"}.",
        appointmentId = appt.id
    )

    fun sendCancellationNotice(appt: Appointment, reason: String) = send(
        clinicId = appt.clinicId,
        patientId = appt.patient.id,
        type = CommType.APPOINTMENT_CANCELLATION,
        body = "Your appointment on ${appt.appointmentDate} has been cancelled. Reason: $reason",
        appointmentId = appt.id
    )

    fun sendAppointmentReminder(appt: Appointment) = send(
        clinicId = appt.clinicId,
        patientId = appt.patient.id,
        type = CommType.APPOINTMENT_REMINDER,
        body = "Reminder: You have an appointment tomorrow at ${appt.startTime} with ${appt.doctor?.fullName ?: "the doctor"}.",
        appointmentId = appt.id
    )

    fun sendInventoryAlert(clinic: Clinic, summary: StockAlertSummary) {
        log.info("Inventory alert for clinic {}: {} low, {} out, {} expiring soon",
            clinic.name, summary.lowStockCount, summary.outOfStockCount, summary.expiringSoonCount)
        // In production, this would also send an SMS/email to the clinic admin
    }

    private fun send(
        clinicId: java.util.UUID,
        patientId: java.util.UUID,
        type: CommType,
        body: String,
        appointmentId: java.util.UUID? = null
    ) {
        try {
            repo.save(
                Communication(
                    clinicId = clinicId,
                    patientId = patientId,
                    channel = CommChannel.SMS,
                    type = type,
                    body = body,
                    status = CommStatus.PENDING,
                    appointmentId = appointmentId
                )
            )
            log.info("Notification queued: {} for patient {}", type, patientId)
        } catch (e: Exception) {
            log.warn("Notification failed (non-critical): {}", e.message)
        }
    }
}
