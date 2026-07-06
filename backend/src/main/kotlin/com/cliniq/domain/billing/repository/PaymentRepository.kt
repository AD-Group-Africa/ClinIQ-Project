package com.cliniq.domain.billing.repository

import com.cliniq.domain.billing.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Repository
interface PaymentRepository : JpaRepository<Payment, UUID> {

    fun findByInvoiceId(invoiceId: UUID): List<Payment>

    @Query("SELECT p FROM Payment p WHERE p.clinicId = :clinicId AND p.paidAt >= :from AND p.paidAt < :to ORDER BY p.paidAt DESC")
    fun findByPeriod(clinicId: UUID, from: Instant, to: Instant): List<Payment>

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.clinicId = :clinicId AND p.method = :method AND p.paidAt >= :from AND p.paidAt < :to")
    fun sumByMethod(clinicId: UUID, method: com.cliniq.domain.billing.entity.PaymentMethod, from: Instant, to: Instant): BigDecimal
}
