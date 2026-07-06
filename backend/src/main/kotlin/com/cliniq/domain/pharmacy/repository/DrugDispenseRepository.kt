package com.cliniq.domain.pharmacy.repository

import com.cliniq.domain.pharmacy.entity.DrugDispense
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DrugDispenseRepository : JpaRepository<DrugDispense, UUID> {
    fun findByPatientIdAndClinicIdOrderByDispensedAtDesc(patientId: UUID, clinicId: UUID): List<DrugDispense>
}
