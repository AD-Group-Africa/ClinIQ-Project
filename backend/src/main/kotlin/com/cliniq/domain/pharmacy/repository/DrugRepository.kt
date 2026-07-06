package com.cliniq.domain.pharmacy.repository

import com.cliniq.domain.pharmacy.entity.Drug
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DrugRepository : JpaRepository<Drug, UUID> {
    fun findByClinicIdAndIsActiveTrue(clinicId: UUID, pageable: Pageable): Page<Drug>

    @Query("""
        SELECT d FROM Drug d
        WHERE d.clinicId = :clinicId AND d.isActive = TRUE
        AND (LOWER(d.name) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(d.genericName) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(d.brandName) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY d.name
    """)
    fun search(clinicId: UUID, q: String, pageable: Pageable): Page<Drug>

    fun findByIdAndClinicId(id: UUID, clinicId: UUID): Drug?
}
