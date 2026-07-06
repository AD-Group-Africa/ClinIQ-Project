package com.cliniq.domain.appointment.service

import com.cliniq.domain.appointment.dto.*
import com.cliniq.domain.appointment.entity.Appointment
import com.cliniq.domain.appointment.entity.AppointmentStatus
import com.cliniq.domain.appointment.repository.AppointmentRepository
import com.cliniq.domain.appointment.repository.DoctorScheduleRepository
import com.cliniq.domain.clinic.repository.DoctorRepository
import com.cliniq.domain.communication.service.NotificationService
import com.cliniq.domain.patient.repository.PatientRepository
import com.cliniq.shared.audit.AuditService
import com.cliniq.shared.exceptions.BadRequestException
import com.cliniq.shared.exceptions.ConflictException
import com.cliniq.shared.exceptions.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class AppointmentService(
    private val appointments: AppointmentRepository,
    private val patients: PatientRepository,
    private val doctors: DoctorRepository,
    private val schedules: DoctorScheduleRepository,
    private val scheduleService: ScheduleService,
    private val notificationService: NotificationService,
    private val audit: AuditService
) {

    fun createAppointment(clinicId: UUID, req: CreateAppointmentRequest, bookedBy: UUID?): AppointmentResponse {
        val patient = patients.findByIdAndClinicId(req.patientId, clinicId) ?: throw NotFoundException("Patient not found")
        val doctor  = doctors.findByIdAndClinicId(req.doctorId, clinicId) ?: throw NotFoundException("Doctor not found")

        val dayOfWeek = req.appointmentDate.dayOfWeek.value % 7
        val schedule  = schedules.findByDoctorAndDay(req.doctorId, dayOfWeek)
            ?: throw BadRequestException("Doctor has no schedule for this day")

        val endTime = req.startTime.plusMinutes(schedule.slotDurationMinutes.toLong())

        val conflicts = appointments.countConflicts(clinicId, req.doctorId, req.appointmentDate, req.startTime, endTime)
        if (conflicts > 0) throw ConflictException("This time slot is already booked")

        val queueNumber = appointments.findMaxQueueNumber(clinicId, req.appointmentDate) + 1

        val appt = appointments.save(
            Appointment(
                clinicId = clinicId, patient = patient, doctor = doctor,
                appointmentDate = req.appointmentDate, startTime = req.startTime, endTime = endTime,
                type = req.type, chiefComplaint = req.chiefComplaint,
                queueNumber = queueNumber, bookedBy = bookedBy,
                bookedVia = if (bookedBy == null) "CHATBOT" else "PORTAL"
            )
        )

        bookedBy?.let { audit.log(clinicId, it, "CREATE_APPOINTMENT", "appointment", appt.id) }
        notificationService.sendAppointmentConfirmation(appt)

        return appt.toResponse()
    }

    @Transactional(readOnly = true)
    fun getTodayQueue(clinicId: UUID): List<AppointmentResponse> =
        appointments.findByClinicAndDate(clinicId, java.time.LocalDate.now()).map { it.toResponse() }

    @Transactional(readOnly = true)
    fun getPatientAppointments(clinicId: UUID, patientId: UUID, pageable: Pageable): Page<AppointmentResponse> =
        appointments.findByPatient(clinicId, patientId, pageable).map { it.toResponse() }

    fun checkIn(clinicId: UUID, id: UUID, by: UUID): AppointmentResponse {
        val appt = appointments.findByIdAndClinicId(id, clinicId) ?: throw NotFoundException("Appointment not found")
        if (appt.status !in listOf(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED)) {
            throw BadRequestException("Cannot check in appointment with status ${appt.status}")
        }
        appt.status = AppointmentStatus.CHECKED_IN
        appt.updatedAt = Instant.now()
        val saved = appointments.save(appt)
        audit.log(clinicId, by, "CHECK_IN", "appointment", id)
        return saved.toResponse()
    }

    fun startConsultation(clinicId: UUID, id: UUID, by: UUID): AppointmentResponse {
        val appt = appointments.findByIdAndClinicId(id, clinicId) ?: throw NotFoundException("Appointment not found")
        appt.status = AppointmentStatus.IN_CONSULTATION
        appt.updatedAt = Instant.now()
        val saved = appointments.save(appt)
        audit.log(clinicId, by, "START_CONSULTATION", "appointment", id)
        return saved.toResponse()
    }

    fun complete(clinicId: UUID, id: UUID, by: UUID): AppointmentResponse {
        val appt = appointments.findByIdAndClinicId(id, clinicId) ?: throw NotFoundException("Appointment not found")
        appt.status = AppointmentStatus.COMPLETED
        appt.updatedAt = Instant.now()
        val saved = appointments.save(appt)
        audit.log(clinicId, by, "COMPLETE_APPOINTMENT", "appointment", id)
        return saved.toResponse()
    }

    fun cancel(clinicId: UUID, id: UUID, req: CancelRequest, by: UUID) {
        val appt = appointments.findByIdAndClinicId(id, clinicId) ?: throw NotFoundException("Appointment not found")
        appt.status = AppointmentStatus.CANCELLED
        appt.cancellationReason = req.reason
        appt.updatedAt = Instant.now()
        appointments.save(appt)
        audit.log(clinicId, by, "CANCEL_APPOINTMENT", "appointment", id, newValues = mapOf("reason" to req.reason))
        notificationService.sendCancellationNotice(appt, req.reason)
    }

    /**
     * Used by the public chatbot API - cancels the next upcoming appointment for a phone number.
     */
    fun cancelNextByPhone(clinicId: UUID, phone: String) {
        val patient = patients.findByClinicIdAndPhone(clinicId, phone)
            ?: throw NotFoundException("No patient found with that phone number")

        val upcoming = appointments.findByPatient(clinicId, patient.id, org.springframework.data.domain.PageRequest.of(0, 5))
            .content.firstOrNull { it.status in listOf(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED) }
            ?: throw NotFoundException("No upcoming appointment found")

        upcoming.status = AppointmentStatus.CANCELLED
        upcoming.cancellationReason = "Cancelled via WhatsApp"
        upcoming.updatedAt = Instant.now()
        appointments.save(upcoming)
        notificationService.sendCancellationNotice(upcoming, "Cancelled via WhatsApp")
    }

    @Transactional(readOnly = true)
    fun getAvailableSlots(clinicId: UUID, doctorId: UUID, date: java.time.LocalDate): List<TimeSlot> =
        scheduleService.getAvailableSlots(clinicId, doctorId, date)

    private fun Appointment.toResponse() = AppointmentResponse(
        id = id, patientId = patient.id, patientName = patient.fullName, patientPhone = patient.phone,
        doctorId = doctor?.id, doctorName = doctor?.fullName,
        appointmentDate = appointmentDate, startTime = startTime, endTime = endTime,
        status = status, type = type, queueNumber = queueNumber,
        chiefComplaint = chiefComplaint, createdAt = createdAt
    )
}
