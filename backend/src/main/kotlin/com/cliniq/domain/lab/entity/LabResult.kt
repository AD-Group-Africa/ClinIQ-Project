package com.cliniq.domain.lab.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "lab_results")
data class LabResult(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID = UUID.randomUUID(),
    @Column(name = "clinic_id", nullable = false) val clinicId: UUID,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "request_id") val request: LabRequest,
    @Column(name = "test_id") val testId: UUID? = null,
    @Column(name = "test_name", nullable = false) val testName: String,
    @Column var value: String? = null,
    @Column var unit: String? = null,
    @Column(name = "normal_range") var normalRange: String? = null,
    @Column(name = "is_abnormal") var isAbnormal: Boolean = false,
    @Column var interpretation: String? = null,
    @Column(name = "performed_by") var performedBy: UUID? = null,
    @Column(name = "verified_by") var verifiedBy: UUID? = null,
    @Column(name = "result_pdf_url") var resultPdfUrl: String? = null,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now()
)
