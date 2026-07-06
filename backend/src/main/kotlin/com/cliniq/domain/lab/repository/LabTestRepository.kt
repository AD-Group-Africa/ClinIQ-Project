package com.cliniq.domain.lab.repository

import com.cliniq.domain.lab.entity.LabTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LabTestRepository : JpaRepository<LabTest, UUID> {
    fun findByClinicIdAndIsActiveTrue(clinicId: UUID, pageable: Pageable): Page<LabTest>
}
