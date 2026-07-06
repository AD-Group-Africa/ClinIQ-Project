package com.cliniq.domain.consultation.dto

import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class StartConsultationRequest(@field:NotNull val patientId: UUID, val appointmentId: UUID? = null, val chiefComplaint: String? = null, val vitals: Map<String, Any?>? = null)
data class UpdateConsultationRequest(val chiefComplaint: String? = null, val history: String? = null, val examination: String? = null, val diagnosis: String? = null, val diagnosisCodes: List<String>? = null, val treatmentPlan: String? = null, val notes: String? = null, val vitals: Map<String, Any?>? = null, val followUpDate: LocalDate? = null, val followUpNotes: String? = null)
data class AddPrescriptionRequest(@field:NotNull val consultationId: UUID, val medications: List<Map<String, Any?>> = emptyList(), val notes: String? = null)
data class ConsultationResponse(val id: UUID, val appointmentId: UUID?, val patientId: UUID, val doctorId: UUID?, val visitNumber: Int, val chiefComplaint: String?, val history: String?, val examination: String?, val diagnosis: String?, val diagnosisCodes: List<String>, val treatmentPlan: String?, val notes: String?, val vitals: Map<String, Any?>, val followUpDate: LocalDate?, val followUpNotes: String?, val createdAt: Instant, val prescriptions: List<PrescriptionResponse> = emptyList())
data class PrescriptionResponse(val id: UUID, val medications: List<Map<String, Any?>>, val notes: String?, val isDispensed: Boolean, val createdAt: Instant)
