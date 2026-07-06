package com.cliniq.domain.lab.dto

import com.cliniq.domain.lab.entity.LabPriority
import com.cliniq.domain.lab.entity.LabRequestStatus
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateLabTestRequest(
    @field:NotBlank val name: String, val code: String? = null,
    val category: String? = null, val description: String? = null,
    val normalRange: String? = null, @field:DecimalMin("0.0") val price: BigDecimal = BigDecimal.ZERO,
    val turnaroundHours: Int = 24
)
data class CreateLabRequestRequest(
    @field:NotNull val patientId: UUID, val consultationId: UUID? = null,
    @field:NotEmpty val testIds: List<UUID>, val priority: LabPriority = LabPriority.ROUTINE,
    val clinicalNotes: String? = null, val sampleType: String? = null
)
data class EnterResultRequest(
    @field:NotNull val requestId: UUID, @field:NotEmpty val results: List<ResultEntry>
)
data class ResultEntry(
    @field:NotNull val testId: UUID?, @field:NotBlank val testName: String,
    val value: String?, val unit: String?, val normalRange: String?,
    val isAbnormal: Boolean = false, val interpretation: String?
)
data class LabRequestResponse(
    val id: UUID, val requestNumber: String?, val patientId: UUID,
    val patientName: String, val doctorName: String?, val status: LabRequestStatus,
    val priority: LabPriority, val tests: List<Map<String, Any>>,
    val totalPrice: BigDecimal, val sampleType: String?,
    val sampleCollectedAt: Instant?, val dueAt: Instant?,
    val completedAt: Instant?, val createdAt: Instant,
    val results: List<LabResultResponse> = emptyList()
)
data class LabResultResponse(
    val id: UUID, val testName: String, val value: String?, val unit: String?,
    val normalRange: String?, val isAbnormal: Boolean,
    val interpretation: String?, val resultPdfUrl: String?, val createdAt: Instant
)
