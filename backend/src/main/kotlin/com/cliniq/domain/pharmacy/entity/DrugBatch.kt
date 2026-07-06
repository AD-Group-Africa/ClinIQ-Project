package com.cliniq.domain.pharmacy.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "drug_batches")
data class DrugBatch(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID = UUID.randomUUID(),
    @Column(name = "clinic_id", nullable = false) val clinicId: UUID,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "drug_id") val drug: Drug,
    @Column(name = "supplier_id") var supplierId: UUID? = null,
    @Column(name = "batch_number", nullable = false) val batchNumber: String,
    @Column(name = "quantity_received", nullable = false) val quantityReceived: Int,
    @Column(name = "quantity_remaining", nullable = false) var quantityRemaining: Int,
    @Column(name = "unit_cost") val unitCost: BigDecimal = BigDecimal.ZERO,
    @Column(name = "expiry_date", nullable = false) val expiryDate: LocalDate,
    @Column(name = "received_by") val receivedBy: UUID? = null,
    @Column var notes: String? = null,
    @Column(name = "created_at") val createdAt: Instant = Instant.now()
) {
    val isExpired get() = expiryDate.isBefore(LocalDate.now())
    val isExpiringSoon get() = expiryDate.isBefore(LocalDate.now().plusDays(30))
}
