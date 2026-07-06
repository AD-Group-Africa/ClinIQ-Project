package com.cliniq.domain.pharmacy.dto

import com.cliniq.domain.pharmacy.entity.DispensingUnit
import com.cliniq.domain.pharmacy.entity.DrugCategory
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateDrugRequest(
    @field:NotBlank val name: String,
    val genericName: String? = null, val brandName: String? = null,
    val category: DrugCategory = DrugCategory.OTHER,
    val unit: DispensingUnit = DispensingUnit.TABLETS,
    val strength: String? = null, val description: String? = null,
    val requiresPrescription: Boolean = true, val isControlled: Boolean = false,
    @field:Min(0) val reorderLevel: Int = 10,
    @field:DecimalMin("0.0") val sellingPrice: BigDecimal = BigDecimal.ZERO
)

data class AddStockRequest(
    @field:NotNull val drugId: UUID, val supplierId: UUID? = null,
    @field:NotBlank val batchNumber: String,
    @field:Min(1) val quantity: Int,
    @field:DecimalMin("0.0") val unitCost: BigDecimal = BigDecimal.ZERO,
    @field:Future val expiryDate: LocalDate, val notes: String? = null
)

data class DispenseRequest(
    @field:NotNull val drugId: UUID, @field:NotNull val patientId: UUID,
    val consultationId: UUID? = null, @field:Min(1) val quantity: Int,
    val instructions: String? = null
)

data class DrugStockResponse(
    val drugId: UUID, val name: String, val genericName: String?,
    val category: DrugCategory, val unit: DispensingUnit, val strength: String?,
    val totalStock: Int, val reorderLevel: Int, val sellingPrice: BigDecimal,
    val stockStatus: String, val batchesExpiringSoon: Int,
    val nearestExpiry: LocalDate?, val batches: List<BatchSummary> = emptyList()
)

data class BatchSummary(
    val id: UUID, val batchNumber: String, val quantityRemaining: Int,
    val expiryDate: LocalDate, val unitCost: BigDecimal,
    val isExpired: Boolean, val isExpiringSoon: Boolean
)

data class DispenseResponse(
    val id: UUID, val drugName: String, val patientId: UUID,
    val quantity: Int, val totalPrice: BigDecimal,
    val instructions: String?, val dispensedAt: Instant
)

data class StockAlertSummary(
    val lowStockCount: Int, val outOfStockCount: Int,
    val expiringSoonCount: Int, val expiredCount: Int,
    val alerts: List<StockAlert>
)

data class StockAlert(
    val drugId: UUID, val drugName: String, val alertType: String,
    val currentStock: Int, val reorderLevel: Int, val expiryDate: LocalDate?
)
