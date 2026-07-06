package com.cliniq.domain.patient.entity

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class Gender { MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY }
enum class BloodGroup { A_POS, A_NEG, B_POS, B_NEG, AB_POS, AB_NEG, O_POS, O_NEG, UNKNOWN }

@Entity
@Table(name = "patients")
data class Patient(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @Column(name = "user_id")
    val userId: UUID? = null,

    @Column(name = "patient_number", unique = true)
    var patientNumber: String? = null,

    @Column(name = "first_name", nullable = false) var firstName: String,
    @Column(name = "last_name", nullable = false)  var lastName: String,
    @Column(nullable = false) var phone: String,
    @Column var email: String? = null,

    @Column(name = "date_of_birth") var dateOfBirth: LocalDate? = null,

    @Enumerated(EnumType.STRING) var gender: Gender? = null,

    @Column(name = "national_id") var nationalId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group")
    var bloodGroup: BloodGroup = BloodGroup.UNKNOWN,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    var allergies: Array<String> = emptyArray(),

    @Column(name = "emergency_contact_name")  var emergencyContactName: String? = null,
    @Column(name = "emergency_contact_phone") var emergencyContactPhone: String? = null,

    @Column(name = "insurance_provider") var insuranceProvider: String? = null,
    @Column(name = "insurance_number")   var insuranceNumber: String? = null,

    @Column var address: String? = null,
    @Column var notes: String? = null,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    var tags: Array<String> = emptyArray(),

    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now()
) {
    val fullName get() = "$firstName $lastName"
}
