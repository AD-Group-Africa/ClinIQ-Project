package com.cliniq.domain.pharmacy.repository

import com.cliniq.domain.pharmacy.entity.DrugBatch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
interface DrugBatchRepository : JpaRepository<DrugBatch, UUID> {
    @Query("""
        SELECT b FROM DrugBatch b
        WHERE b.drug.id = :drugId AND b.clinicId = :clinicId
        AND b.quantityRemaining > 0 AND b.expiryDate > CURRENT_DATE
        ORDER BY b.expiryDate ASC
    """)
    fun findAvailableBatches(clinicId: UUID, drugId: UUID): List<DrugBatch>

    @Query("""
        SELECT b FROM DrugBatch b
        WHERE b.clinicId = :clinicId AND b.expiryDate < :threshold
        AND b.quantityRemaining > 0
        ORDER BY b.expiryDate ASC
    """)
    fun findExpiringSoon(clinicId: UUID, threshold: LocalDate): List<DrugBatch>

    @Query("""
        SELECT SUM(b.quantityRemaining) FROM DrugBatch b
        WHERE b.drug.id = :drugId AND b.clinicId = :clinicId
        AND b.quantityRemaining > 0 AND b.expiryDate > CURRENT_DATE
    """)
    fun totalAvailableStock(clinicId: UUID, drugId: UUID): Int?
}
