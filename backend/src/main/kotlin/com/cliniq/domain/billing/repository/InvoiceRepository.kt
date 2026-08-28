package com.cliniq.domain.billing.repository

import com.cliniq.domain.billing.entity.Invoice
import com.cliniq.domain.billing.entity.InvoiceStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Repository
interface InvoiceRepository : JpaRepository<Invoice, UUID> {

    fun findByIdAndClinicId(id: UUID, clinicId: UUID): Invoice?

    @Query("SELECT i FROM Invoice i WHERE i.clinicId = :clinicId AND i.patientId = :patientId ORDER BY i.issuedAt DESC")
    fun findByPatient(clinicId: UUID, patientId: UUID, pageable: Pageable): Page<Invoice>

    @Query("SELECT i FROM Invoice i WHERE i.clinicId = :clinicId AND i.status = :status ORDER BY i.issuedAt DESC")
    fun findByStatus(clinicId: UUID, status: InvoiceStatus, pageable: Pageable): Page<Invoice>

    @Query("SELECT COALESCE(SUM(i.total), 0) FROM Invoice i WHERE i.clinicId = :clinicId AND i.issuedAt >= :from AND i.issuedAt < :to")
    fun sumRevenue(clinicId: UUID, from: Instant, to: Instant): BigDecimal

    @Query("SELECT COALESCE(SUM(i.paidAmount), 0) FROM Invoice i WHERE i.clinicId = :clinicId AND i.issuedAt >= :from AND i.issuedAt < :to")
    fun sumCollected(clinicId: UUID, from: Instant, to: Instant): BigDecimal

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.clinicId = :clinicId AND i.status = :status")
    fun countByStatus(clinicId: UUID, status: InvoiceStatus): Long

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(i.invoiceNumber, LENGTH(:prefix)+1) AS INTEGER)), 0) FROM Invoice i WHERE i.clinicId = :clinicId AND i.invoiceNumber LIKE CONCAT(:prefix,'%')")
    fun findMaxSequence(clinicId: UUID, prefix: String): Int?

    @Query("SELECT i FROM Invoice i WHERE i.clinicId = :clinicId ORDER BY i.issuedAt DESC")
    fun findByClinicId(clinicId: UUID, pageable: Pageable): Page<Invoice>

    @Query(value = "SELECT i FROM Invoice i WHERE i.clinicId = :clinicId AND (i.invoiceNumber LIKE CONCAT('%', :filter, '%') OR i.patientId IN (SELECT p.id FROM patients p WHERE p.clinicId = :clinicId))", nativeQuery = true)
    fun findByClinicIdAndFilter(clinicId: UUID, filter: String, pageable: Pageable): Page<Invoice>
}