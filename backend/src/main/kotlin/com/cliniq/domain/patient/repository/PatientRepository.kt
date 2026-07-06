package com.cliniq.domain.patient.repository

import com.cliniq.domain.patient.entity.Patient
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PatientRepository : JpaRepository<Patient, UUID> {

    fun findByIdAndClinicId(id: UUID, clinicId: UUID): Patient?

    fun existsByClinicIdAndPhone(clinicId: UUID, phone: String): Boolean

    fun findByClinicIdAndPhone(clinicId: UUID, phone: String): Patient?

    fun countByClinicId(clinicId: UUID): Long

    @Query("""
        SELECT p FROM Patient p
        WHERE p.clinicId = :clinicId
        AND (
            LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%'))
            OR p.phone LIKE CONCAT('%', :q, '%')
            OR LOWER(p.email) LIKE LOWER(CONCAT('%', :q, '%'))
            OR p.patientNumber LIKE CONCAT('%', :q, '%')
        )
        ORDER BY p.createdAt DESC
    """)
    fun search(clinicId: UUID, q: String, pageable: Pageable): Page<Patient>

    @Query("SELECT p FROM Patient p WHERE p.clinicId = :clinicId ORDER BY p.createdAt DESC")
    fun findAllByClinicId(clinicId: UUID, pageable: Pageable): Page<Patient>
}
