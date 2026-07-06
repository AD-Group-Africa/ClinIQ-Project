package com.cliniq.domain.clinic.repository

import com.cliniq.domain.clinic.entity.Doctor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DoctorRepository : JpaRepository<Doctor, UUID> {
    fun findByIdAndClinicId(id: UUID, clinicId: UUID): Doctor?

    @Query("SELECT d FROM Doctor d WHERE d.clinicId = :clinicId AND d.isAvailable = TRUE ORDER BY d.user.firstName")
    fun findAvailableByClinic(clinicId: UUID): List<Doctor>

    @Query("SELECT d FROM Doctor d WHERE d.clinicId = :clinicId ORDER BY d.user.firstName")
    fun findAllByClinic(clinicId: UUID): List<Doctor>

    @Query("SELECT d FROM Doctor d WHERE d.user.id = :userId")
    fun findByUserId(userId: UUID): Doctor?

    fun countByClinicId(clinicId: UUID): Long
}
