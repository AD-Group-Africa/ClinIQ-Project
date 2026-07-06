package com.cliniq.domain.lab.repository

import com.cliniq.domain.lab.entity.LabRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LabRequestRepository : JpaRepository<LabRequest, UUID> {
    fun findByIdAndClinicId(id: UUID, clinicId: UUID): LabRequest?

    @Query("SELECT r FROM LabRequest r WHERE r.clinicId = :clinicId AND r.status IN ('REQUESTED','SAMPLE_COLLECTED','PROCESSING') ORDER BY r.createdAt ASC")
    fun findPending(clinicId: UUID, pageable: Pageable): Page<LabRequest>

    @Query("SELECT r FROM LabRequest r WHERE r.clinicId = :clinicId AND r.patientId = :patientId ORDER BY r.createdAt DESC")
    fun findByPatient(clinicId: UUID, patientId: UUID, pageable: Pageable): Page<LabRequest>

    fun countByClinicId(clinicId: UUID): Long
}
