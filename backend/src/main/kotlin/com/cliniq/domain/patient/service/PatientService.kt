package com.cliniq.domain.patient.service

import com.cliniq.domain.patient.dto.*
import com.cliniq.domain.patient.entity.Patient
import com.cliniq.domain.patient.repository.PatientRepository
import com.cliniq.shared.audit.AuditService
import com.cliniq.shared.exceptions.ConflictException
import com.cliniq.shared.exceptions.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.Year
import java.util.UUID

@Service
@Transactional
class PatientService(
    private val patients: PatientRepository,
    private val audit: AuditService
) {

    fun create(clinicId: UUID, req: CreatePatientRequest, createdBy: UUID): PatientResponse {
        if (patients.existsByClinicIdAndPhone(clinicId, req.phone)) {
            throw ConflictException("A patient with phone ${req.phone} already exists")
        }

        val patient = patients.save(
            Patient(
                clinicId = clinicId,
                firstName = req.firstName,
                lastName = req.lastName,
                phone = req.phone,
                email = req.email,
                dateOfBirth = req.dateOfBirth,
                gender = req.gender,
                nationalId = req.nationalId,
                bloodGroup = req.bloodGroup,
                allergies = req.allergies.toTypedArray(),
                emergencyContactName = req.emergencyContactName,
                emergencyContactPhone = req.emergencyContactPhone,
                insuranceProvider = req.insuranceProvider,
                insuranceNumber = req.insuranceNumber,
                address = req.address,
                notes = req.notes,
                tags = req.tags.toTypedArray()
            )
        )
        patient.patientNumber = generatePatientNumber(clinicId, patient.id)
        val saved = patients.save(patient)

        audit.log(clinicId, createdBy, "CREATE_PATIENT", "patient", saved.id,
            newValues = mapOf("name" to saved.fullName, "phone" to saved.phone))

        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun search(clinicId: UUID, query: String, pageable: Pageable): Page<PatientResponse> {
        val page = if (query.isBlank()) patients.findAllByClinicId(clinicId, pageable)
                   else patients.search(clinicId, query, pageable)
        return page.map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getById(clinicId: UUID, id: UUID): PatientResponse =
        patients.findByIdAndClinicId(id, clinicId)?.toResponse()
            ?: throw NotFoundException("Patient not found")

    fun update(clinicId: UUID, id: UUID, req: UpdatePatientRequest, updatedBy: UUID): PatientResponse {
        val patient = patients.findByIdAndClinicId(id, clinicId) ?: throw NotFoundException("Patient not found")

        req.firstName?.let { patient.firstName = it }
        req.lastName?.let { patient.lastName = it }
        req.phone?.let { patient.phone = it }
        req.email?.let { patient.email = it }
        req.dateOfBirth?.let { patient.dateOfBirth = it }
        req.gender?.let { patient.gender = it }
        req.bloodGroup?.let { patient.bloodGroup = it }
        req.allergies?.let { patient.allergies = it.toTypedArray() }
        req.emergencyContactName?.let { patient.emergencyContactName = it }
        req.emergencyContactPhone?.let { patient.emergencyContactPhone = it }
        req.insuranceProvider?.let { patient.insuranceProvider = it }
        req.insuranceNumber?.let { patient.insuranceNumber = it }
        req.address?.let { patient.address = it }
        req.notes?.let { patient.notes = it }
        req.tags?.let { patient.tags = it.toTypedArray() }
        patient.updatedAt = Instant.now()

        val saved = patients.save(patient)
        audit.log(clinicId, updatedBy, "UPDATE_PATIENT", "patient", saved.id)
        return saved.toResponse()
    }

    /**
     * Used by the public chatbot API to find-or-create a patient by phone number.
     */
    fun findOrCreateByPhone(clinicId: UUID, phone: String, name: String): Patient {
        patients.findByClinicIdAndPhone(clinicId, phone)?.let { return it }

        val parts = name.trim().split(" ", limit = 2)
        val patient = Patient(
            clinicId = clinicId,
            firstName = parts.firstOrNull()?.ifBlank { "Patient" } ?: "Patient",
            lastName = parts.getOrNull(1) ?: "",
            phone = phone,
            notes = "Auto-registered via WhatsApp chatbot"
        )
        val saved = patients.save(patient)
        saved.patientNumber = generatePatientNumber(clinicId, saved.id)
        return patients.save(saved)
    }

    private fun generatePatientNumber(clinicId: UUID, id: UUID): String {
        val year = Year.now().value
        val seq = patients.countByClinicId(clinicId) + 1
        return "PT-$year-${seq.toString().padStart(5, '0')}"
    }

    private fun Patient.toResponse() = PatientResponse(
        id = id, patientNumber = patientNumber, firstName = firstName, lastName = lastName,
        phone = phone, email = email, dateOfBirth = dateOfBirth, gender = gender,
        bloodGroup = bloodGroup, allergies = allergies.toList(),
        emergencyContactName = emergencyContactName, emergencyContactPhone = emergencyContactPhone,
        insuranceProvider = insuranceProvider, insuranceNumber = insuranceNumber,
        address = address, notes = notes, tags = tags.toList(), createdAt = createdAt
    )
}
