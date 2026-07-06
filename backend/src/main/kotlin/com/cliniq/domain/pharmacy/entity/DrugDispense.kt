package com.cliniq.domain.pharmacy.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "drug_dispenses")
data class DrugDispense(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID = UUID.randomUUID(),
    @Column(name = "clinic_id", nullable = false) val clinicId: UUID,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "drug_id") val drug: Drug,
    @Column(name = "batch_id") val batchId: UUID? = null,
    @Column(name = "consultation_id") val consultationId: UUID? = null,
    @Column(name = "patient_id", nullable = false) val patientId: UUID,
    @Column(name = "prescribed_by") val prescribedBy: UUID? = null,
    @Column(name = "dispensed_by") val dispensedBy: UUID? = null,
    @Column(nullable = false) val quantity: Int,
    @Column(name = "unit_price") val unitPrice: BigDecimal = BigDecimal.ZERO,
    @Column(name = "total_price") val totalPrice: BigDecimal = BigDecimal.ZERO,
    @Column var instructions: String? = null,
    @Column(name = "dispensed_at") val dispensedAt: Instant = Instant.now()
)
