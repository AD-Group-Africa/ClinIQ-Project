package com.cliniq.domain.patient.dto

import com.cliniq.domain.patient.entity.BloodGroup
import com.cliniq.domain.patient.entity.Gender
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreatePatientRequest(
    @field:NotBlank val firstName: String,
    @field:NotBlank val lastName: String,
    @field:NotBlank val phone: String,
    @field:Email val email: String? = null,
    val dateOfBirth: LocalDate? = null,
    val gender: Gender? = null,
    val nationalId: String? = null,
    val bloodGroup: BloodGroup = BloodGroup.UNKNOWN,
    val allergies: List<String> = emptyList(),
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val insuranceProvider: String? = null,
    val insuranceNumber: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList()
)

data class UpdatePatientRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val dateOfBirth: LocalDate? = null,
    val gender: Gender? = null,
    val bloodGroup: BloodGroup? = null,
    val allergies: List<String>? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val insuranceProvider: String? = null,
    val insuranceNumber: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val tags: List<String>? = null
)

data class PatientResponse(
    val id: UUID,
    val patientNumber: String?,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val email: String?,
    val dateOfBirth: LocalDate?,
    val gender: Gender?,
    val bloodGroup: BloodGroup,
    val allergies: List<String>,
    val emergencyContactName: String?,
    val emergencyContactPhone: String?,
    val insuranceProvider: String?,
    val insuranceNumber: String?,
    val address: String?,
    val notes: String?,
    val tags: List<String>,
    val createdAt: Instant
)
