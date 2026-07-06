package com.cliniq.domain.lab.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "lab_tests")
data class LabTest(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID = UUID.randomUUID(),
    @Column(name = "clinic_id", nullable = false) val clinicId: UUID,
    @Column(nullable = false) var name: String,
    @Column var code: String? = null,
    @Column var category: String? = null,
    @Column var description: String? = null,
    @Column(name = "normal_range") var normalRange: String? = null,
    @Column var price: BigDecimal = BigDecimal.ZERO,
    @Column(name = "turnaround_hours") var turnaroundHours: Int = 24,
    @Column(name = "is_active") var isActive: Boolean = true,
    @Column(name = "created_at") val createdAt: Instant = Instant.now()
)
