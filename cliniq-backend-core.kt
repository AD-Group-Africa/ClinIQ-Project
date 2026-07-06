// =============================================================
// CliniqApplication.kt
// =============================================================
package com.cliniq

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class CliniqApplication

fun main(args: Array<String>) {
    runApplication<CliniqApplication>(*args)
}


// =============================================================
// shared/exceptions/Exceptions.kt
// =============================================================
package com.cliniq.shared.exceptions

class NotFoundException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)
class ForbiddenException(message: String) : RuntimeException(message)
class BadRequestException(message: String) : RuntimeException(message)
class UnauthorizedException(message: String) : RuntimeException(message)


// =============================================================
// shared/audit/AuditService.kt
// =============================================================
package com.cliniq.shared.audit

import com.cliniq.domain.auth.entity.AuditLog
import com.cliniq.domain.auth.repository.AuditLogRepository
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuditService(private val auditLogRepository: AuditLogRepository) {

    @Async
    fun log(
        clinicId: UUID,
        userId: UUID?,
        action: String,
        entityType: String? = null,
        entityId: UUID? = null,
        oldValues: Map<String, Any?>? = null,
        newValues: Map<String, Any?>? = null,
        ipAddress: String? = null
    ) {
        auditLogRepository.save(
            AuditLog(
                clinicId = clinicId,
                userId = userId,
                action = action,
                entityType = entityType,
                entityId = entityId,
                oldValues = oldValues,
                newValues = newValues,
                ipAddress = ipAddress
            )
        )
    }
}


// =============================================================
// shared/context/ClinicContext.kt
// =============================================================
package com.cliniq.shared.context

import java.util.UUID

object ClinicContext {
    private val clinicId = ThreadLocal<UUID?>()

    fun set(id: UUID) = clinicId.set(id)
    fun get(): UUID = clinicId.get() ?: error("No clinic context set")
    fun clear() = clinicId.remove()
}


// =============================================================
// shared/response/ApiResponse.kt
// =============================================================
package com.cliniq.shared.response

import java.time.Instant

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
    val meta: PageMeta? = null
) {
    companion object {
        fun <T> success(data: T, meta: PageMeta? = null) =
            ApiResponse(true, data, meta = meta)
        fun <T> error(code: String, message: String) =
            ApiResponse<T>(false, error = ApiError(code, message))
    }
}

data class ApiError(
    val code: String,
    val message: String?,
    val details: Any? = null,
    val timestamp: Instant = Instant.now()
)

data class PageMeta(
    val page: Int,
    val size: Int,
    val total: Long,
    val totalPages: Int
)


// =============================================================
// domain/auth/entity/User.kt
// =============================================================
package com.cliniq.domain.auth.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id")
    val clinicId: UUID? = null,

    @Column(unique = true, nullable = false)
    val email: String,

    @Column
    var phone: String? = null,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(name = "first_name", nullable = false)
    var firstName: String,

    @Column(name = "last_name", nullable = false)
    var lastName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: UserRole,

    @Column(name = "avatar_url")
    var avatarUrl: String? = null,

    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "is_verified")
    var isVerified: Boolean = false,

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
) {
    val fullName get() = "$firstName $lastName"
}

enum class UserRole {
    SUPER_ADMIN, CLINIC_ADMIN, DOCTOR, NURSE, RECEPTIONIST, FINANCE, PATIENT
}


// =============================================================
// domain/auth/entity/AuditLog.kt
// =============================================================
package com.cliniq.domain.auth.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_logs")
data class AuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id")
    val clinicId: UUID,

    @Column(name = "user_id")
    val userId: UUID? = null,

    @Column(nullable = false)
    val action: String,

    @Column(name = "entity_type")
    val entityType: String? = null,

    @Column(name = "entity_id")
    val entityId: UUID? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb")
    val oldValues: Map<String, Any?>? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb")
    val newValues: Map<String, Any?>? = null,

    @Column(name = "ip_address")
    val ipAddress: String? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)


// =============================================================
// domain/clinic/entity/Clinic.kt
// =============================================================
package com.cliniq.domain.clinic.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "clinics")
data class Clinic(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var name: String,

    @Column(unique = true, nullable = false)
    var slug: String,

    @Column
    var phone: String? = null,

    @Column
    var email: String? = null,

    @Column
    var address: String? = null,

    @Column
    var city: String? = null,

    @Column
    var country: String = "KE",

    @Column(name = "logo_url")
    var logoUrl: String? = null,

    @Column
    var website: String? = null,

    @Column
    var timezone: String = "Africa/Nairobi",

    @Column
    var currency: String = "KES",

    @Column(name = "subscription_tier")
    var subscriptionTier: String = "starter",

    @Column(name = "is_active")
    var isActive: Boolean = true,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var settings: Map<String, Any> = emptyMap(),

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
)


// =============================================================
// domain/patient/entity/Patient.kt
// =============================================================
package com.cliniq.domain.patient.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "patients")
data class Patient(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @Column(name = "user_id")
    var userId: UUID? = null,

    @Column(name = "patient_number", unique = true)
    var patientNumber: String? = null,

    @Column(name = "first_name", nullable = false)
    var firstName: String,

    @Column(name = "last_name", nullable = false)
    var lastName: String,

    @Column(nullable = false)
    var phone: String,

    @Column
    var email: String? = null,

    @Column(name = "date_of_birth")
    var dateOfBirth: LocalDate? = null,

    @Enumerated(EnumType.STRING)
    var gender: GenderType? = null,

    @Column(name = "national_id")
    var nationalId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group")
    var bloodGroup: BloodGroupType = BloodGroupType.UNKNOWN,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    var allergies: Array<String> = emptyArray(),

    @Column(name = "emergency_contact_name")
    var emergencyContactName: String? = null,

    @Column(name = "emergency_contact_phone")
    var emergencyContactPhone: String? = null,

    @Column(name = "insurance_provider")
    var insuranceProvider: String? = null,

    @Column(name = "insurance_number")
    var insuranceNumber: String? = null,

    @Column
    var address: String? = null,

    @Column
    var notes: String? = null,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    var tags: Array<String> = emptyArray(),

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
) {
    val fullName get() = "$firstName $lastName"
    val age get() = dateOfBirth?.let {
        java.time.Period.between(it, LocalDate.now()).years
    }
}

enum class GenderType { MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY }
enum class BloodGroupType {
    `A+`, `A-`, `B+`, `B-`, `AB+`, `AB-`, `O+`, `O-`, UNKNOWN
}


// =============================================================
// domain/patient/service/PatientService.kt
// =============================================================
package com.cliniq.domain.patient.service

import com.cliniq.domain.patient.dto.*
import com.cliniq.domain.patient.entity.Patient
import com.cliniq.domain.patient.repository.PatientRepository
import com.cliniq.shared.exceptions.ConflictException
import com.cliniq.shared.exceptions.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class PatientService(
    private val patientRepository: PatientRepository
) {

    fun create(clinicId: UUID, request: CreatePatientRequest): PatientResponse {
        if (patientRepository.existsByClinicIdAndPhone(clinicId, request.phone)) {
            throw ConflictException("Patient with phone ${request.phone} already exists")
        }

        val patient = Patient(
            clinicId = clinicId,
            firstName = request.firstName,
            lastName = request.lastName,
            phone = request.phone,
            email = request.email,
            dateOfBirth = request.dateOfBirth,
            gender = request.gender,
            nationalId = request.nationalId,
            address = request.address,
            emergencyContactName = request.emergencyContactName,
            emergencyContactPhone = request.emergencyContactPhone,
            insuranceProvider = request.insuranceProvider,
            insuranceNumber = request.insuranceNumber
        )

        val saved = patientRepository.save(patient)
        saved.patientNumber = generatePatientNumber(clinicId, saved.id)
        return patientRepository.save(saved).toResponse()
    }

    @Transactional(readOnly = true)
    fun search(clinicId: UUID, query: String, pageable: Pageable): Page<PatientResponse> {
        return patientRepository.search(clinicId, query, pageable).map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun findById(clinicId: UUID, id: UUID): PatientResponse {
        return patientRepository.findByIdAndClinicId(id, clinicId)?.toResponse()
            ?: throw NotFoundException("Patient not found")
    }

    fun update(clinicId: UUID, id: UUID, request: UpdatePatientRequest): PatientResponse {
        val patient = patientRepository.findByIdAndClinicId(id, clinicId)
            ?: throw NotFoundException("Patient not found")

        request.firstName?.let { patient.firstName = it }
        request.lastName?.let { patient.lastName = it }
        request.phone?.let { patient.phone = it }
        request.email?.let { patient.email = it }
        request.dateOfBirth?.let { patient.dateOfBirth = it }
        request.gender?.let { patient.gender = it }
        request.address?.let { patient.address = it }
        request.allergies?.let { patient.allergies = it.toTypedArray() }
        request.notes?.let { patient.notes = it }
        request.tags?.let { patient.tags = it.toTypedArray() }

        return patientRepository.save(patient).toResponse()
    }

    private fun generatePatientNumber(clinicId: UUID, patientId: UUID): String {
        val year = java.time.Year.now().value
        val count = patientRepository.countByClinicId(clinicId)
        return "CLQ-$year-${count.toString().padStart(4, '0')}"
    }
}


// =============================================================
// domain/patient/controller/PatientController.kt
// =============================================================
package com.cliniq.domain.patient.controller

import com.cliniq.domain.patient.dto.*
import com.cliniq.domain.patient.service.PatientService
import com.cliniq.shared.response.ApiResponse
import com.cliniq.shared.response.PageMeta
import com.cliniq.shared.security.JwtAuthenticationToken
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/patients")
class PatientController(private val patientService: PatientService) {

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun create(
        @Valid @RequestBody request: CreatePatientRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<PatientResponse>> {
        return ResponseEntity.status(201).body(
            ApiResponse.success(patientService.create(auth.clinicId, request))
        )
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun search(
        @RequestParam(defaultValue = "") q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<List<PatientResponse>>> {
        val result = patientService.search(auth.clinicId, q, PageRequest.of(page, size))
        return ResponseEntity.ok(
            ApiResponse.success(
                result.content,
                PageMeta(page, size, result.totalElements, result.totalPages)
            )
        )
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun findById(
        @PathVariable id: UUID,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<PatientResponse>> {
        return ResponseEntity.ok(
            ApiResponse.success(patientService.findById(auth.clinicId, id))
        )
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: UpdatePatientRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<PatientResponse>> {
        return ResponseEntity.ok(
            ApiResponse.success(patientService.update(auth.clinicId, id, request))
        )
    }
}
