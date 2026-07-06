package com.cliniq.domain.consultation.service

import com.cliniq.domain.appointment.entity.AppointmentStatus
import com.cliniq.domain.appointment.repository.AppointmentRepository
import com.cliniq.domain.consultation.dto.*
import com.cliniq.domain.consultation.entity.Consultation
import com.cliniq.domain.consultation.entity.Prescription
import com.cliniq.domain.consultation.repository.ConsultationRepository
import com.cliniq.domain.consultation.repository.PrescriptionRepository
import com.cliniq.shared.audit.AuditService
import com.cliniq.shared.exceptions.ForbiddenException
import com.cliniq.shared.exceptions.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class ConsultationService(
    private val consultations: ConsultationRepository,
    private val prescriptions: PrescriptionRepository,
    private val appointments: AppointmentRepository,
    private val audit: AuditService
) {
    fun start(clinicId: UUID, req: StartConsultationRequest, doctorId: UUID): ConsultationResponse {
        req.appointmentId?.let { id -> appointments.findByIdAndClinicId(id, clinicId)?.let { it.status = AppointmentStatus.IN_CONSULTATION; appointments.save(it) } }
        val visitNumber = consultations.countByPatientId(clinicId, req.patientId) + 1
        val c = consultations.save(Consultation(clinicId = clinicId, appointmentId = req.appointmentId, patientId = req.patientId, doctorId = doctorId, visitNumber = visitNumber, chiefComplaint = req.chiefComplaint, vitals = req.vitals ?: emptyMap()))
        audit.log(clinicId, doctorId, "START_CONSULTATION", "consultation", c.id)
        return c.toResponse(emptyList())
    }

    fun update(clinicId: UUID, id: UUID, req: UpdateConsultationRequest, by: UUID): ConsultationResponse {
        val c = consultations.findByIdAndClinicId(id, clinicId) ?: throw NotFoundException("Consultation not found")
        if (c.doctorId != null && c.doctorId != by) throw ForbiddenException("Not your consultation")
        req.chiefComplaint?.let { c.chiefComplaint = it }; req.history?.let { c.history = it }; req.examination?.let { c.examination = it }
        req.diagnosis?.let { c.diagnosis = it }; req.diagnosisCodes?.let { c.diagnosisCodes = it.toTypedArray() }
        req.treatmentPlan?.let { c.treatmentPlan = it }; req.notes?.let { c.notes = it }
        req.vitals?.let { c.vitals = it }; req.followUpDate?.let { c.followUpDate = it }; req.followUpNotes?.let { c.followUpNotes = it }
        c.updatedAt = Instant.now()
        return consultations.save(c).toResponse(prescriptions.findByConsultationId(id))
    }

    fun complete(clinicId: UUID, id: UUID, doctorId: UUID): ConsultationResponse {
        val c = consultations.findByIdAndClinicId(id, clinicId) ?: throw NotFoundException("Consultation not found")
        c.appointmentId?.let { apptId -> appointments.findByIdAndClinicId(apptId, clinicId)?.let { it.status = AppointmentStatus.COMPLETED; appointments.save(it) } }
        c.updatedAt = Instant.now(); audit.log(clinicId, doctorId, "COMPLETE_CONSULTATION", "consultation", id)
        return consultations.save(c).toResponse(prescriptions.findByConsultationId(id))
    }

    fun addPrescription(clinicId: UUID, req: AddPrescriptionRequest, doctorId: UUID): PrescriptionResponse {
        consultations.findByIdAndClinicId(req.consultationId, clinicId) ?: throw NotFoundException("Consultation not found")
        val rx = prescriptions.save(Prescription(clinicId = clinicId, consultationId = req.consultationId, patientId = consultations.findById(req.consultationId).get().patientId, doctorId = doctorId, medications = req.medications, notes = req.notes))
        audit.log(clinicId, doctorId, "ADD_PRESCRIPTION", "prescription", rx.id)
        return rx.toResponse()
    }

    @Transactional(readOnly = true)
    fun getTimeline(clinicId: UUID, patientId: UUID, pageable: Pageable): Page<ConsultationResponse> =
        consultations.findByPatientId(clinicId, patientId, pageable).map { c -> c.toResponse(prescriptions.findByConsultationId(c.id)) }

    private fun Consultation.toResponse(rxList: List<Prescription>) = ConsultationResponse(id, appointmentId, patientId, doctorId, visitNumber, chiefComplaint, history, examination, diagnosis, diagnosisCodes.toList(), treatmentPlan, notes, vitals, followUpDate, followUpNotes, createdAt, rxList.map { it.toResponse() })
    private fun Prescription.toResponse() = PrescriptionResponse(id, medications, notes, isDispensed, createdAt)
}
