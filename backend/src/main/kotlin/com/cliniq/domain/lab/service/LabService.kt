package com.cliniq.domain.lab.service

import com.cliniq.domain.lab.dto.*
import com.cliniq.domain.lab.entity.*
import com.cliniq.domain.lab.repository.*
import com.cliniq.domain.patient.repository.PatientRepository
import com.cliniq.domain.auth.repository.UserRepository
import com.cliniq.shared.audit.AuditService
import com.cliniq.shared.exceptions.*
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
@Transactional
class LabService(
    private val labTestRepository: LabTestRepository,
    private val labRequestRepository: LabRequestRepository,
    private val labResultRepository: LabResultRepository,
    private val patientRepository: PatientRepository,
    private val userRepository: UserRepository,
    private val auditService: AuditService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun addTest(clinicId: UUID, request: CreateLabTestRequest): LabTest =
        labTestRepository.save(
            LabTest(
                clinicId = clinicId, name = request.name, code = request.code,
                category = request.category, description = request.description,
                normalRange = request.normalRange, price = request.price,
                turnaroundHours = request.turnaroundHours
            )
        )

    @Transactional(readOnly = true)
    fun getTestCatalog(clinicId: UUID, pageable: Pageable): Page<LabTest> =
        labTestRepository.findByClinicIdAndIsActiveTrue(clinicId, pageable)

    fun createRequest(clinicId: UUID, request: CreateLabRequestRequest, orderedBy: UUID): LabRequestResponse {
        val patient = patientRepository.findByIdAndClinicId(request.patientId, clinicId)
            ?: throw NotFoundException("Patient not found")
        val tests = labTestRepository.findAllById(request.testIds).filter { it.clinicId == clinicId }
        if (tests.isEmpty()) throw BadRequestException("No valid tests found")

        val totalPrice = tests.fold(java.math.BigDecimal.ZERO) { acc, t -> acc.add(t.price) }
        val labRequest = LabRequest(
            clinicId = clinicId, patientId = request.patientId, doctorId = orderedBy,
            consultationId = request.consultationId, requestNumber = generateRequestNumber(clinicId),
            priority = request.priority,
            tests = tests.map { mapOf("testId" to it.id.toString(), "testName" to it.name, "price" to it.price) },
            totalPrice = totalPrice, clinicalNotes = request.clinicalNotes, sampleType = request.sampleType,
            dueAt = Instant.now().plus(tests.maxOf { it.turnaroundHours }.toLong(), ChronoUnit.HOURS)
        )
        val saved = labRequestRepository.save(labRequest)
        auditService.log(clinicId, orderedBy, "CREATE_LAB_REQUEST", "lab_request", saved.id)
        return saved.toResponse(patient.fullName, null)
    }

    fun updateStatus(clinicId: UUID, requestId: UUID, newStatus: LabRequestStatus, updatedBy: UUID): LabRequestResponse {
        val req = labRequestRepository.findByIdAndClinicId(requestId, clinicId)
            ?: throw NotFoundException("Lab request not found")
        req.status = newStatus
        if (newStatus == LabRequestStatus.SAMPLE_COLLECTED) req.sampleCollectedAt = Instant.now()
        if (newStatus == LabRequestStatus.COMPLETED) req.completedAt = Instant.now()
        req.updatedAt = Instant.now()
        val saved = labRequestRepository.save(req)
        val patient = patientRepository.findById(saved.patientId).orElse(null)
        auditService.log(clinicId, updatedBy, "UPDATE_LAB_STATUS", "lab_request", requestId, newValues = mapOf("status" to newStatus))
        return saved.toResponse(patient?.fullName ?: "Unknown", null)
    }

    fun enterResults(clinicId: UUID, request: EnterResultRequest, technicianId: UUID): LabRequestResponse {
        val labRequest = labRequestRepository.findByIdAndClinicId(request.requestId, clinicId)
            ?: throw NotFoundException("Lab request not found")
        val results = request.results.map { entry ->
            labResultRepository.save(
                LabResult(
                    clinicId = clinicId, request = labRequest, testId = entry.testId,
                    testName = entry.testName, value = entry.value, unit = entry.unit,
                    normalRange = entry.normalRange, isAbnormal = entry.isAbnormal,
                    interpretation = entry.interpretation, performedBy = technicianId
                )
            )
        }
        labRequest.status = LabRequestStatus.COMPLETED
        labRequest.completedAt = Instant.now()
        labRequest.updatedAt = Instant.now()
        labRequestRepository.save(labRequest)

        if (results.any { it.isAbnormal }) {
            log.warn("Abnormal results for patient {} — notify doctor {}", labRequest.patientId, labRequest.doctorId)
        }
        auditService.log(clinicId, technicianId, "ENTER_LAB_RESULTS", "lab_request", labRequest.id)
        val patient = patientRepository.findById(labRequest.patientId).orElse(null)
        return labRequest.toResponse(patient?.fullName ?: "Unknown", results)
    }

    @Transactional(readOnly = true)
    fun getPendingRequests(clinicId: UUID, pageable: Pageable): Page<LabRequestResponse> {
        return labRequestRepository.findPending(clinicId, pageable).map { req ->
            val patient = patientRepository.findById(req.patientId).orElse(null)
            req.toResponse(patient?.fullName ?: "Unknown", null)
        }
    }

    @Transactional(readOnly = true)
    fun getRequestsByPatient(clinicId: UUID, patientId: UUID, pageable: Pageable): Page<LabRequestResponse> {
        return labRequestRepository.findByPatient(clinicId, patientId, pageable).map { req ->
            val results = labResultRepository.findByRequestId(req.id)
            val patient = patientRepository.findById(req.patientId).orElse(null)
            req.toResponse(patient?.fullName ?: "Unknown", results)
        }
    }

    private fun generateRequestNumber(clinicId: UUID): String {
        val count = labRequestRepository.countByClinicId(clinicId)
        val year = java.time.Year.now().value
        return "LAB-$year-${(count + 1).toString().padStart(4, '0')}"
    }

    private fun LabRequest.toResponse(patientName: String, results: List<LabResult>?) = LabRequestResponse(
        id = id, requestNumber = requestNumber, patientId = patientId,
        patientName = patientName, doctorName = null,
        status = status, priority = priority, tests = tests,
        totalPrice = totalPrice, sampleType = sampleType,
        sampleCollectedAt = sampleCollectedAt, dueAt = dueAt,
        completedAt = completedAt, createdAt = createdAt,
        results = results?.map { it.toResponse() } ?: emptyList()
    )

    private fun LabResult.toResponse() = LabResultResponse(
        id = id, testName = testName, value = value, unit = unit,
        normalRange = normalRange, isAbnormal = isAbnormal,
        interpretation = interpretation, resultPdfUrl = resultPdfUrl,
        createdAt = createdAt
    )
}
