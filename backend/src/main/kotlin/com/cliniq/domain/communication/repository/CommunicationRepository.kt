package com.cliniq.domain.communication.repository

import com.cliniq.domain.communication.entity.Communication
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface CommunicationRepository : JpaRepository<Communication, UUID> {

    @Query("SELECT c FROM Communication c WHERE c.clinicId = :clinicId AND c.patientId = :patientId ORDER BY c.createdAt DESC")
    fun findByClinicIdAndPatientIdOrderByCreatedAtDesc(clinicId: UUID, patientId: UUID): List<Communication>
}
