// =============================================================
// ClinIQ — Laboratory & Consultation Modules
// =============================================================
// LAB MODULE:
//   entity/LabTest.kt        - test catalog
//   entity/LabRequest.kt     - doctor-issued request
//   entity/LabResult.kt      - technician result entry
//   dto/LabDTOs.kt
//   service/LabService.kt
//   controller/LabController.kt
//
// CONSULTATION MODULE:
//   entity/Consultation.kt   - clinical note
//   entity/Prescription.kt   - medication orders
//   entity/Vitals.kt         - embedded vitals JSONB
//   dto/ConsultationDTOs.kt
//   service/ConsultationService.kt
//   controller/ConsultationController.kt
// =============================================================
// DB Migration: V6__lab_consultation.sql
// =============================================================


// =============================================
// V6__lab_consultation.sql
// =============================================
/*
CREATE TYPE lab_request_status AS ENUM (
    'REQUESTED', 'SAMPLE_COLLECTED', 'PROCESSING', 'COMPLETED', 'CANCELLED'
);
CREATE TYPE lab_priority AS ENUM ('ROUTINE', 'URGENT', 'STAT');

-- Test catalog (shared per clinic)
CREATE TABLE lab_tests (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    name            VARCHAR(300) NOT NULL,
    code            VARCHAR(50),          -- e.g. FBC, LFT, RFT
    category        VARCHAR(100),         -- Haematology, Biochemistry, etc.
    description     TEXT,
    normal_range    TEXT,                 -- e.g. "3.8–5.0 × 10¹²/L"
    price           DECIMAL(10,2) DEFAULT 0,
    turnaround_hours INTEGER DEFAULT 24,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Doctor-issued lab requests
CREATE TABLE lab_requests (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    patient_id      UUID REFERENCES patients(id) ON DELETE CASCADE,
    doctor_id       UUID REFERENCES doctors(id) ON DELETE SET NULL,
    consultation_id UUID REFERENCES consultations(id) ON DELETE SET NULL,
    request_number  VARCHAR(50) UNIQUE,
    status          lab_request_status DEFAULT 'REQUESTED',
    priority        lab_priority DEFAULT 'ROUTINE',
    tests           JSONB NOT NULL DEFAULT '[]',  -- [{test_id, test_name, price}]
    total_price     DECIMAL(10,2) DEFAULT 0,
    clinical_notes  TEXT,
    sample_type     VARCHAR(100),         -- Blood, Urine, Stool, Swab, etc.
    sample_collected_at TIMESTAMPTZ,
    due_at          TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Individual test results
CREATE TABLE lab_results (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    request_id      UUID REFERENCES lab_requests(id) ON DELETE CASCADE,
    test_id         UUID REFERENCES lab_tests(id) ON DELETE SET NULL,
    test_name       VARCHAR(300) NOT NULL,
    value           TEXT,                 -- could be numeric or text
    unit            VARCHAR(50),
    normal_range    TEXT,
    is_abnormal     BOOLEAN DEFAULT FALSE,
    interpretation  TEXT,
    performed_by    UUID REFERENCES users(id),
    verified_by     UUID REFERENCES users(id),
    result_pdf_url  TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Consultations (clinical record per visit)
CREATE TABLE consultations (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    appointment_id  UUID UNIQUE REFERENCES appointments(id) ON DELETE SET NULL,
    patient_id      UUID REFERENCES patients(id) ON DELETE CASCADE,
    doctor_id       UUID REFERENCES doctors(id) ON DELETE SET NULL,
    visit_number    INTEGER DEFAULT 1,
    chief_complaint TEXT,
    history         TEXT,
    examination     TEXT,
    diagnosis       TEXT,
    diagnosis_codes TEXT[],               -- ICD-10 codes
    treatment_plan  TEXT,
    notes           TEXT,
    vitals          JSONB DEFAULT '{}',
    -- {systolicBp, diastolicBp, pulse, temp, weight, height, spo2, rbs}
    follow_up_date  DATE,
    follow_up_notes TEXT,
    is_admitted     BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Prescriptions from consultation
CREATE TABLE prescriptions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    consultation_id UUID REFERENCES consultations(id) ON DELETE CASCADE,
    patient_id      UUID REFERENCES patients(id) ON DELETE CASCADE,
    doctor_id       UUID REFERENCES doctors(id) ON DELETE SET NULL,
    medications     JSONB NOT NULL DEFAULT '[]',
    -- [{drugId, drugName, dosage, frequency, duration, route, instructions, quantity}]
    notes           TEXT,
    is_dispensed    BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_lab_requests_patient ON lab_requests(clinic_id, patient_id, created_at DESC);
CREATE INDEX idx_lab_requests_status ON lab_requests(clinic_id, status, priority);
CREATE INDEX idx_lab_results_request ON lab_results(request_id);
CREATE INDEX idx_consultations_patient ON consultations(clinic_id, patient_id, created_at DESC);
CREATE INDEX idx_prescriptions_consultation ON prescriptions(consultation_id);
CREATE INDEX idx_prescriptions_patient ON prescriptions(clinic_id, patient_id, created_at DESC);
*/


// =============================================
// entity/LabTest.kt
// =============================================
package com.cliniq.domain.lab.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "lab_tests")
data class LabTest(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @Column(nullable = false)
    var name: String,

    @Column
    var code: String? = null,

    @Column
    var category: String? = null,

    @Column
    var description: String? = null,

    @Column(name = "normal_range")
    var normalRange: String? = null,

    @Column
    var price: BigDecimal = BigDecimal.ZERO,

    @Column(name = "turnaround_hours")
    var turnaroundHours: Int = 24,

    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)


// =============================================
// entity/LabRequest.kt
// =============================================
package com.cliniq.domain.lab.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "lab_requests")
data class LabRequest(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @Column(name = "patient_id", nullable = false)
    val patientId: UUID,

    @Column(name = "doctor_id")
    val doctorId: UUID? = null,

    @Column(name = "consultation_id")
    val consultationId: UUID? = null,

    @Column(name = "request_number", unique = true)
    var requestNumber: String? = null,

    @Enumerated(EnumType.STRING)
    var status: LabRequestStatus = LabRequestStatus.REQUESTED,

    @Enumerated(EnumType.STRING)
    var priority: LabPriority = LabPriority.ROUTINE,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var tests: List<Map<String, Any>> = emptyList(),

    @Column(name = "total_price")
    var totalPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "clinical_notes")
    var clinicalNotes: String? = null,

    @Column(name = "sample_type")
    var sampleType: String? = null,

    @Column(name = "sample_collected_at")
    var sampleCollectedAt: Instant? = null,

    @Column(name = "due_at")
    var dueAt: Instant? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
)

enum class LabRequestStatus {
    REQUESTED, SAMPLE_COLLECTED, PROCESSING, COMPLETED, CANCELLED
}

enum class LabPriority { ROUTINE, URGENT, STAT }


// =============================================
// entity/LabResult.kt
// =============================================
package com.cliniq.domain.lab.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "lab_results")
data class LabResult(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    val request: LabRequest,

    @Column(name = "test_id")
    val testId: UUID? = null,

    @Column(name = "test_name", nullable = false)
    val testName: String,

    @Column
    var value: String? = null,

    @Column
    var unit: String? = null,

    @Column(name = "normal_range")
    var normalRange: String? = null,

    @Column(name = "is_abnormal")
    var isAbnormal: Boolean = false,

    @Column
    var interpretation: String? = null,

    @Column(name = "performed_by")
    var performedBy: UUID? = null,

    @Column(name = "verified_by")
    var verifiedBy: UUID? = null,

    @Column(name = "result_pdf_url")
    var resultPdfUrl: String? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
)


// =============================================
// dto/LabDTOs.kt
// =============================================
package com.cliniq.domain.lab.dto

import com.cliniq.domain.lab.entity.LabPriority
import com.cliniq.domain.lab.entity.LabRequestStatus
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateLabTestRequest(
    @field:NotBlank val name: String,
    val code: String? = null,
    val category: String? = null,
    val description: String? = null,
    val normalRange: String? = null,
    @field:DecimalMin("0.0") val price: BigDecimal = BigDecimal.ZERO,
    val turnaroundHours: Int = 24
)

data class CreateLabRequestRequest(
    @field:NotNull val patientId: UUID,
    val consultationId: UUID? = null,
    @field:NotEmpty val testIds: List<UUID>,
    val priority: LabPriority = LabPriority.ROUTINE,
    val clinicalNotes: String? = null,
    val sampleType: String? = null
)

data class EnterResultRequest(
    @field:NotNull val requestId: UUID,
    @field:NotEmpty val results: List<ResultEntry>
)

data class ResultEntry(
    @field:NotNull val testId: UUID?,
    @field:NotBlank val testName: String,
    val value: String?,
    val unit: String?,
    val normalRange: String?,
    val isAbnormal: Boolean = false,
    val interpretation: String?
)

data class LabRequestResponse(
    val id: UUID,
    val requestNumber: String?,
    val patientId: UUID,
    val patientName: String,
    val doctorName: String?,
    val status: LabRequestStatus,
    val priority: LabPriority,
    val tests: List<Map<String, Any>>,
    val totalPrice: BigDecimal,
    val sampleType: String?,
    val sampleCollectedAt: Instant?,
    val dueAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant,
    val results: List<LabResultResponse> = emptyList()
)

data class LabResultResponse(
    val id: UUID,
    val testName: String,
    val value: String?,
    val unit: String?,
    val normalRange: String?,
    val isAbnormal: Boolean,
    val interpretation: String?,
    val resultPdfUrl: String?,
    val createdAt: Instant
)


// =============================================
// service/LabService.kt
// =============================================
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

    // -----------------------------------------------
    // Test catalog
    // -----------------------------------------------

    fun addTest(clinicId: UUID, request: CreateLabTestRequest): LabTest {
        return labTestRepository.save(
            LabTest(
                clinicId = clinicId,
                name = request.name,
                code = request.code,
                category = request.category,
                description = request.description,
                normalRange = request.normalRange,
                price = request.price,
                turnaroundHours = request.turnaroundHours
            )
        )
    }

    @Transactional(readOnly = true)
    fun getTestCatalog(clinicId: UUID, pageable: Pageable): Page<LabTest> =
        labTestRepository.findByClinicIdAndIsActiveTrue(clinicId, pageable)

    // -----------------------------------------------
    // Lab requests (doctor ordering tests)
    // -----------------------------------------------

    fun createRequest(clinicId: UUID, request: CreateLabRequestRequest, orderedBy: UUID): LabRequestResponse {
        val patient = patientRepository.findByIdAndClinicId(request.patientId, clinicId)
            ?: throw NotFoundException("Patient not found")

        val tests = labTestRepository.findAllById(request.testIds)
            .filter { it.clinicId == clinicId }

        if (tests.isEmpty()) throw BadRequestException("No valid tests found")

        val totalPrice = tests.fold(java.math.BigDecimal.ZERO) { acc, t -> acc.add(t.price) }

        val labRequest = LabRequest(
            clinicId = clinicId,
            patientId = request.patientId,
            doctorId = orderedBy,
            consultationId = request.consultationId,
            requestNumber = generateRequestNumber(clinicId),
            priority = request.priority,
            tests = tests.map { mapOf("testId" to it.id.toString(), "testName" to it.name, "price" to it.price) },
            totalPrice = totalPrice,
            clinicalNotes = request.clinicalNotes,
            sampleType = request.sampleType,
            dueAt = Instant.now().plus(tests.maxOf { it.turnaroundHours }.toLong(), ChronoUnit.HOURS)
        )

        val saved = labRequestRepository.save(labRequest)
        auditService.log(clinicId, orderedBy, "CREATE_LAB_REQUEST", "lab_request", saved.id)

        return saved.toResponse(patient.fullName, null)
    }

    fun updateStatus(clinicId: UUID, requestId: UUID, newStatus: LabRequestStatus, updatedBy: UUID): LabRequestResponse {
        val request = labRequestRepository.findByIdAndClinicId(requestId, clinicId)
            ?: throw NotFoundException("Lab request not found")

        request.status = newStatus
        if (newStatus == LabRequestStatus.SAMPLE_COLLECTED) {
            request.sampleCollectedAt = Instant.now()
        }
        if (newStatus == LabRequestStatus.COMPLETED) {
            request.completedAt = Instant.now()
        }
        request.updatedAt = Instant.now()

        val saved = labRequestRepository.save(request)
        val patient = patientRepository.findById(saved.patientId).orElse(null)
        auditService.log(clinicId, updatedBy, "UPDATE_LAB_STATUS", "lab_request", requestId,
            newValues = mapOf("status" to newStatus))

        return saved.toResponse(patient?.fullName ?: "Unknown", null)
    }

    // -----------------------------------------------
    // Result entry (lab technician workflow)
    // -----------------------------------------------

    fun enterResults(clinicId: UUID, request: EnterResultRequest, technicianId: UUID): LabRequestResponse {
        val labRequest = labRequestRepository.findByIdAndClinicId(request.requestId, clinicId)
            ?: throw NotFoundException("Lab request not found")

        // Persist each result
        val results = request.results.map { entry ->
            labResultRepository.save(
                LabResult(
                    clinicId = clinicId,
                    request = labRequest,
                    testId = entry.testId,
                    testName = entry.testName,
                    value = entry.value,
                    unit = entry.unit,
                    normalRange = entry.normalRange,
                    isAbnormal = entry.isAbnormal,
                    interpretation = entry.interpretation,
                    performedBy = technicianId
                )
            )
        }

        // Mark request as completed
        labRequest.status = LabRequestStatus.COMPLETED
        labRequest.completedAt = Instant.now()
        labRequest.updatedAt = Instant.now()
        labRequestRepository.save(labRequest)

        val hasAbnormal = results.any { it.isAbnormal }
        log.info("Results entered for request {} — {} tests, {} abnormal",
            labRequest.requestNumber, results.size, results.count { it.isAbnormal })

        // TODO: notify doctor if abnormal results
        if (hasAbnormal) {
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

    private fun LabRequest.toResponse(patientName: String, results: List<LabResult>?): LabRequestResponse =
        LabRequestResponse(
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


// =============================================
// controller/LabController.kt
// =============================================
package com.cliniq.domain.lab.controller

import com.cliniq.domain.lab.dto.*
import com.cliniq.domain.lab.entity.LabRequestStatus
import com.cliniq.domain.lab.service.LabService
import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.shared.response.ApiResponse
import com.cliniq.shared.response.PageMeta
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/lab")
class LabController(private val labService: LabService) {

    @PostMapping("/tests")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','SUPER_ADMIN')")
    fun addTest(
        @Valid @RequestBody request: CreateLabTestRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.status(201).body(ApiResponse.success(labService.addTest(auth.clinicId!!, request)))

    @GetMapping("/tests")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','DOCTOR','NURSE','LAB_TECH','SUPER_ADMIN')")
    fun listTests(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<List<Any>>> {
        val result = labService.getTestCatalog(auth.clinicId!!, PageRequest.of(page, size))
        return ResponseEntity.ok(ApiResponse.success(result.content, PageMeta(page, size, result.totalElements, result.totalPages)))
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun createRequest(
        @Valid @RequestBody request: CreateLabRequestRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.status(201).body(
        ApiResponse.success(labService.createRequest(auth.clinicId!!, request, auth.userId))
    )

    @GetMapping("/requests/pending")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','LAB_TECH','SUPER_ADMIN')")
    fun pendingRequests(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<List<LabRequestResponse>>> {
        val result = labService.getPendingRequests(auth.clinicId!!, PageRequest.of(page, size))
        return ResponseEntity.ok(ApiResponse.success(result.content, PageMeta(page, size, result.totalElements, result.totalPages)))
    }

    @GetMapping("/requests/patient/{patientId}")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN','LAB_TECH','SUPER_ADMIN')")
    fun patientRequests(
        @PathVariable patientId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<List<LabRequestResponse>>> {
        val result = labService.getRequestsByPatient(auth.clinicId!!, patientId, PageRequest.of(page, size))
        return ResponseEntity.ok(ApiResponse.success(result.content, PageMeta(page, size, result.totalElements, result.totalPages)))
    }

    @PatchMapping("/requests/{id}/status")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','LAB_TECH','SUPER_ADMIN')")
    fun updateStatus(
        @PathVariable id: UUID,
        @RequestParam status: LabRequestStatus,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.ok(ApiResponse.success(labService.updateStatus(auth.clinicId!!, id, status, auth.userId)))

    @PostMapping("/results")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','LAB_TECH','SUPER_ADMIN')")
    fun enterResults(
        @Valid @RequestBody request: EnterResultRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.ok(ApiResponse.success(labService.enterResults(auth.clinicId!!, request, auth.userId)))
}


// =============================================================
// ============= CONSULTATION MODULE ===========================
// =============================================================

// =============================================
// entity/Consultation.kt
// =============================================
package com.cliniq.domain.consultation.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "consultations")
data class Consultation(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @Column(name = "appointment_id")
    val appointmentId: UUID? = null,

    @Column(name = "patient_id", nullable = false)
    val patientId: UUID,

    @Column(name = "doctor_id")
    val doctorId: UUID? = null,

    @Column(name = "visit_number")
    var visitNumber: Int = 1,

    @Column(name = "chief_complaint")
    var chiefComplaint: String? = null,

    @Column
    var history: String? = null,

    @Column
    var examination: String? = null,

    @Column
    var diagnosis: String? = null,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "diagnosis_codes", columnDefinition = "text[]")
    var diagnosisCodes: Array<String> = emptyArray(),

    @Column(name = "treatment_plan")
    var treatmentPlan: String? = null,

    @Column
    var notes: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var vitals: Map<String, Any?> = emptyMap(),

    @Column(name = "follow_up_date")
    var followUpDate: LocalDate? = null,

    @Column(name = "follow_up_notes")
    var followUpNotes: String? = null,

    @Column(name = "is_admitted")
    var isAdmitted: Boolean = false,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
)


// =============================================
// entity/Prescription.kt
// =============================================
package com.cliniq.domain.consultation.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "prescriptions")
data class Prescription(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @Column(name = "consultation_id", nullable = false)
    val consultationId: UUID,

    @Column(name = "patient_id", nullable = false)
    val patientId: UUID,

    @Column(name = "doctor_id")
    val doctorId: UUID? = null,

    /**
     * Medication list stored as JSONB:
     * [{
     *   "drugId": "uuid | null",
     *   "drugName": "Amoxicillin",
     *   "dosage": "500mg",
     *   "frequency": "3 times daily",
     *   "duration": "7 days",
     *   "route": "Oral",
     *   "instructions": "Take with food",
     *   "quantity": 21
     * }]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var medications: List<Map<String, Any?>> = emptyList(),

    @Column
    var notes: String? = null,

    @Column(name = "is_dispensed")
    var isDispensed: Boolean = false,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)


// =============================================
// dto/ConsultationDTOs.kt
// =============================================
package com.cliniq.domain.consultation.dto

import jakarta.validation.constraints.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class StartConsultationRequest(
    @field:NotNull val appointmentId: UUID,
    @field:NotNull val patientId: UUID,
    val chiefComplaint: String? = null,
    val vitals: VitalsData? = null
)

data class VitalsData(
    val systolicBp: Int? = null,
    val diastolicBp: Int? = null,
    val pulse: Int? = null,
    val temperature: Double? = null,   // Celsius
    val weight: Double? = null,         // kg
    val height: Double? = null,         // cm
    val spo2: Int? = null,              // %
    val rbs: Double? = null             // mmol/L
)

data class UpdateConsultationRequest(
    val chiefComplaint: String? = null,
    val history: String? = null,
    val examination: String? = null,
    val diagnosis: String? = null,
    val diagnosisCodes: List<String>? = null,
    val treatmentPlan: String? = null,
    val notes: String? = null,
    val vitals: VitalsData? = null,
    val followUpDate: LocalDate? = null,
    val followUpNotes: String? = null
)

data class AddPrescriptionRequest(
    @field:NotNull val consultationId: UUID,
    @field:NotEmpty val medications: List<MedicationItem>,
    val notes: String? = null
)

data class MedicationItem(
    val drugId: UUID? = null,
    @field:NotBlank val drugName: String,
    @field:NotBlank val dosage: String,
    @field:NotBlank val frequency: String,
    @field:NotBlank val duration: String,
    val route: String = "Oral",
    val instructions: String? = null,
    val quantity: Int = 1
)

data class ConsultationResponse(
    val id: UUID,
    val appointmentId: UUID?,
    val patientId: UUID,
    val patientName: String,
    val doctorId: UUID?,
    val doctorName: String?,
    val visitNumber: Int,
    val chiefComplaint: String?,
    val history: String?,
    val examination: String?,
    val diagnosis: String?,
    val diagnosisCodes: List<String>,
    val treatmentPlan: String?,
    val notes: String?,
    val vitals: Map<String, Any?>,
    val followUpDate: LocalDate?,
    val followUpNotes: String?,
    val createdAt: Instant,
    val prescriptions: List<PrescriptionResponse> = emptyList()
)

data class PrescriptionResponse(
    val id: UUID,
    val medications: List<Map<String, Any?>>,
    val notes: String?,
    val isDispensed: Boolean,
    val createdAt: Instant
)


// =============================================
// service/ConsultationService.kt
// =============================================
package com.cliniq.domain.consultation.service

import com.cliniq.domain.appointment.entity.AppointmentStatus
import com.cliniq.domain.appointment.repository.AppointmentRepository
import com.cliniq.domain.consultation.dto.*
import com.cliniq.domain.consultation.entity.Consultation
import com.cliniq.domain.consultation.entity.Prescription
import com.cliniq.domain.consultation.repository.ConsultationRepository
import com.cliniq.domain.consultation.repository.PrescriptionRepository
import com.cliniq.domain.patient.repository.PatientRepository
import com.cliniq.shared.audit.AuditService
import com.cliniq.shared.exceptions.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class ConsultationService(
    private val consultationRepository: ConsultationRepository,
    private val prescriptionRepository: PrescriptionRepository,
    private val appointmentRepository: AppointmentRepository,
    private val patientRepository: PatientRepository,
    private val auditService: AuditService
) {
    // -----------------------------------------------
    // Start a consultation (doctor begins seeing patient)
    // -----------------------------------------------

    fun start(clinicId: UUID, request: StartConsultationRequest, doctorId: UUID): ConsultationResponse {
        val patient = patientRepository.findByIdAndClinicId(request.patientId, clinicId)
            ?: throw NotFoundException("Patient not found")

        // Update appointment status
        request.appointmentId.let { apptId ->
            appointmentRepository.findById(apptId).ifPresent { appt ->
                appt.status = AppointmentStatus.IN_CONSULTATION
                appointmentRepository.save(appt)
            }
        }

        // Calculate visit number (count previous completed consultations)
        val visitNumber = consultationRepository.countByPatientId(clinicId, request.patientId) + 1

        val consultation = consultationRepository.save(
            Consultation(
                clinicId = clinicId,
                appointmentId = request.appointmentId,
                patientId = request.patientId,
                doctorId = doctorId,
                visitNumber = visitNumber,
                chiefComplaint = request.chiefComplaint,
                vitals = request.vitals?.toMap() ?: emptyMap()
            )
        )

        auditService.log(clinicId, doctorId, "START_CONSULTATION", "consultation", consultation.id)
        return consultation.toResponse(patient.fullName, null, emptyList())
    }

    // -----------------------------------------------
    // Update consultation notes (auto-saves as doctor types)
    // -----------------------------------------------

    fun update(clinicId: UUID, id: UUID, request: UpdateConsultationRequest, doctorId: UUID): ConsultationResponse {
        val consultation = consultationRepository.findByIdAndClinicId(id, clinicId)
            ?: throw NotFoundException("Consultation not found")

        if (consultation.doctorId != doctorId) {
            throw ForbiddenException("You can only update your own consultations")
        }

        request.chiefComplaint?.let { consultation.chiefComplaint = it }
        request.history?.let { consultation.history = it }
        request.examination?.let { consultation.examination = it }
        request.diagnosis?.let { consultation.diagnosis = it }
        request.diagnosisCodes?.let { consultation.diagnosisCodes = it.toTypedArray() }
        request.treatmentPlan?.let { consultation.treatmentPlan = it }
        request.notes?.let { consultation.notes = it }
        request.vitals?.let { consultation.vitals = it.toMap() }
        request.followUpDate?.let { consultation.followUpDate = it }
        request.followUpNotes?.let { consultation.followUpNotes = it }
        consultation.updatedAt = Instant.now()

        val saved = consultationRepository.save(consultation)
        val patient = patientRepository.findById(consultation.patientId).orElse(null)
        val prescriptions = prescriptionRepository.findByConsultationId(id)
        return saved.toResponse(patient?.fullName ?: "Unknown", null, prescriptions)
    }

    // -----------------------------------------------
    // Complete consultation (discharge patient)
    // -----------------------------------------------

    fun complete(clinicId: UUID, id: UUID, doctorId: UUID): ConsultationResponse {
        val consultation = consultationRepository.findByIdAndClinicId(id, clinicId)
            ?: throw NotFoundException("Consultation not found")

        // Mark appointment as completed
        consultation.appointmentId?.let { apptId ->
            appointmentRepository.findById(apptId).ifPresent { appt ->
                appt.status = AppointmentStatus.COMPLETED
                appointmentRepository.save(appt)
            }
        }

        // Schedule follow-up if specified
        if (consultation.followUpDate != null) {
            // TODO: trigger CRM follow-up reminder creation
        }

        auditService.log(clinicId, doctorId, "COMPLETE_CONSULTATION", "consultation", consultation.id)

        val patient = patientRepository.findById(consultation.patientId).orElse(null)
        val prescriptions = prescriptionRepository.findByConsultationId(id)
        return consultation.toResponse(patient?.fullName ?: "Unknown", null, prescriptions)
    }

    // -----------------------------------------------
    // Prescriptions
    // -----------------------------------------------

    fun addPrescription(clinicId: UUID, request: AddPrescriptionRequest, doctorId: UUID): PrescriptionResponse {
        val consultation = consultationRepository.findByIdAndClinicId(request.consultationId, clinicId)
            ?: throw NotFoundException("Consultation not found")

        val prescription = prescriptionRepository.save(
            Prescription(
                clinicId = clinicId,
                consultationId = consultation.id,
                patientId = consultation.patientId,
                doctorId = doctorId,
                medications = request.medications.map { med ->
                    mapOf(
                        "drugId" to med.drugId?.toString(),
                        "drugName" to med.drugName,
                        "dosage" to med.dosage,
                        "frequency" to med.frequency,
                        "duration" to med.duration,
                        "route" to med.route,
                        "instructions" to med.instructions,
                        "quantity" to med.quantity
                    )
                },
                notes = request.notes
            )
        )

        auditService.log(clinicId, doctorId, "ADD_PRESCRIPTION", "prescription", prescription.id)
        return prescription.toResponse()
    }

    @Transactional(readOnly = true)
    fun getPatientTimeline(clinicId: UUID, patientId: UUID, pageable: Pageable): Page<ConsultationResponse> {
        return consultationRepository.findByPatientId(clinicId, patientId, pageable).map { c ->
            val prescriptions = prescriptionRepository.findByConsultationId(c.id)
            c.toResponse("", null, prescriptions)
        }
    }

    // -----------------------------------------------
    // Helpers
    // -----------------------------------------------

    private fun VitalsData.toMap(): Map<String, Any?> = mapOf(
        "systolicBp" to systolicBp, "diastolicBp" to diastolicBp,
        "pulse" to pulse, "temperature" to temperature,
        "weight" to weight, "height" to height,
        "spo2" to spo2, "rbs" to rbs
    ).filterValues { it != null }

    private fun Consultation.toResponse(
        patientName: String, doctorName: String?, prescriptions: List<Prescription>
    ) = ConsultationResponse(
        id = id, appointmentId = appointmentId, patientId = patientId,
        patientName = patientName, doctorId = doctorId, doctorName = doctorName,
        visitNumber = visitNumber, chiefComplaint = chiefComplaint, history = history,
        examination = examination, diagnosis = diagnosis,
        diagnosisCodes = diagnosisCodes.toList(), treatmentPlan = treatmentPlan,
        notes = notes, vitals = vitals, followUpDate = followUpDate,
        followUpNotes = followUpNotes, createdAt = createdAt,
        prescriptions = prescriptions.map { it.toResponse() }
    )

    private fun Prescription.toResponse() = PrescriptionResponse(
        id = id, medications = medications, notes = notes,
        isDispensed = isDispensed, createdAt = createdAt
    )
}


// =============================================
// controller/ConsultationController.kt
// =============================================
package com.cliniq.domain.consultation.controller

import com.cliniq.domain.consultation.dto.*
import com.cliniq.domain.consultation.service.ConsultationService
import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.shared.response.ApiResponse
import com.cliniq.shared.response.PageMeta
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/consultations")
class ConsultationController(private val consultationService: ConsultationService) {

    @PostMapping("/start")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun start(
        @Valid @RequestBody request: StartConsultationRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.status(201).body(
        ApiResponse.success(consultationService.start(auth.clinicId!!, request, auth.userId))
    )

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: UpdateConsultationRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.ok(ApiResponse.success(consultationService.update(auth.clinicId!!, id, request, auth.userId)))

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun complete(
        @PathVariable id: UUID,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.ok(ApiResponse.success(consultationService.complete(auth.clinicId!!, id, auth.userId)))

    @PostMapping("/prescriptions")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun addPrescription(
        @Valid @RequestBody request: AddPrescriptionRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.status(201).body(
        ApiResponse.success(consultationService.addPrescription(auth.clinicId!!, request, auth.userId))
    )

    @GetMapping("/patient/{patientId}/timeline")
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE','CLINIC_ADMIN','SUPER_ADMIN')")
    fun patientTimeline(
        @PathVariable patientId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<List<ConsultationResponse>>> {
        val result = consultationService.getPatientTimeline(auth.clinicId!!, patientId, PageRequest.of(page, size))
        return ResponseEntity.ok(ApiResponse.success(result.content, PageMeta(page, size, result.totalElements, result.totalPages)))
    }
}
