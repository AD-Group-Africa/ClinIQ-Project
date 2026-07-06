package com.cliniq.domain.consultation.repository

import com.cliniq.domain.consultation.entity.Consultation
import com.cliniq.domain.consultation.entity.Prescription
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ConsultationRepository : JpaRepository<Consultation, UUID> {
    fun findByIdAndClinicId(id: UUID, clinicId: UUID): Consultation?
    @Query("SELECT c FROM Consultation c WHERE c.clinicId = :clinicId AND c.patientId = :patientId ORDER BY c.createdAt DESC")
    fun findByPatientId(clinicId: UUID, patientId: UUID, pageable: Pageable): Page<Consultation>
    @Query("SELECT COUNT(c) FROM Consultation c WHERE c.clinicId = :clinicId AND c.patientId = :patientId")
    fun countByPatientId(clinicId: UUID, patientId: UUID): Int
}

@Repository
interface PrescriptionRepository : JpaRepository<Prescription, UUID> {
    @Query("SELECT p FROM Prescription p WHERE p.consultationId = :consultationId")
    fun findByConsultationId(consultationId: UUID): List<Prescription>
    @Query("SELECT p FROM Prescription p WHERE p.clinicId = :clinicId AND p.patientId = :patientId ORDER BY p.createdAt DESC")
    fun findByPatient(clinicId: UUID, patientId: UUID): List<Prescription>
}
