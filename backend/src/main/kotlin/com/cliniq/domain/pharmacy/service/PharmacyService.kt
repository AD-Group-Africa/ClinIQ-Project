package com.cliniq.domain.pharmacy.service

import com.cliniq.domain.pharmacy.dto.*
import com.cliniq.domain.pharmacy.entity.*
import com.cliniq.domain.pharmacy.repository.*
import com.cliniq.shared.audit.AuditService
import com.cliniq.shared.exceptions.*
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional
class PharmacyService(
    private val drugRepository: DrugRepository,
    private val batchRepository: DrugBatchRepository,
    private val dispenseRepository: DrugDispenseRepository,
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val auditService: AuditService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun addDrug(clinicId: UUID, request: CreateDrugRequest, createdBy: UUID): DrugStockResponse {
        val drug = drugRepository.save(
            Drug(
                clinicId = clinicId, name = request.name, genericName = request.genericName,
                brandName = request.brandName, category = request.category, unit = request.unit,
                strength = request.strength, description = request.description,
                requiresPrescription = request.requiresPrescription,
                isControlled = request.isControlled, reorderLevel = request.reorderLevel,
                sellingPrice = request.sellingPrice
            )
        )
        auditService.log(clinicId, createdBy, "ADD_DRUG", "drug", drug.id)
        return getStockSummary(clinicId, drug.id)
    }

    @Transactional(readOnly = true)
    fun searchDrugs(clinicId: UUID, query: String, pageable: Pageable): Page<DrugStockResponse> {
        val drugs = if (query.isBlank()) {
            drugRepository.findByClinicIdAndIsActiveTrue(clinicId, pageable)
        } else {
            drugRepository.search(clinicId, query, pageable)
        }
        return drugs.map { drug ->
            val stock = batchRepository.totalAvailableStock(clinicId, drug.id) ?: 0
            val expiringSoon = batchRepository.findExpiringSoon(
                clinicId, LocalDate.now().plusDays(30)
            ).count { it.drug.id == drug.id }

            DrugStockResponse(
                drugId = drug.id, name = drug.name, genericName = drug.genericName,
                category = drug.category, unit = drug.unit, strength = drug.strength,
                totalStock = stock, reorderLevel = drug.reorderLevel,
                sellingPrice = drug.sellingPrice,
                stockStatus = when {
                    stock == 0 -> "OUT_OF_STOCK"
                    stock <= drug.reorderLevel -> "LOW_STOCK"
                    else -> "IN_STOCK"
                },
                batchesExpiringSoon = expiringSoon, nearestExpiry = null
            )
        }
    }

    fun addStock(clinicId: UUID, request: AddStockRequest, receivedBy: UUID): BatchSummary {
        val drug = drugRepository.findByIdAndClinicId(request.drugId, clinicId)
            ?: throw NotFoundException("Drug not found")
        if (request.expiryDate.isBefore(LocalDate.now()))
            throw BadRequestException("Cannot add stock with past expiry date")

        val batch = batchRepository.save(
            DrugBatch(
                clinicId = clinicId, drug = drug, supplierId = request.supplierId,
                batchNumber = request.batchNumber, quantityReceived = request.quantity,
                quantityRemaining = request.quantity, unitCost = request.unitCost,
                expiryDate = request.expiryDate, receivedBy = receivedBy, notes = request.notes
            )
        )
        auditService.log(clinicId, receivedBy, "ADD_STOCK", "drug_batch", batch.id,
            newValues = mapOf("drug" to drug.name, "quantity" to request.quantity, "batch" to request.batchNumber))
        log.info("Stock added: {} × {} units, expires {}", drug.name, request.quantity, request.expiryDate)
        return batch.toBatchSummary()
    }

    fun dispense(clinicId: UUID, request: DispenseRequest, dispensedBy: UUID): DispenseResponse {
        val drug = drugRepository.findByIdAndClinicId(request.drugId, clinicId)
            ?: throw NotFoundException("Drug not found")
        val availableBatches = batchRepository.findAvailableBatches(clinicId, drug.id)
        val totalAvailable = availableBatches.sumOf { it.quantityRemaining }
        if (totalAvailable < request.quantity)
            throw ConflictException("Insufficient stock for ${drug.name}. Available: $totalAvailable, Requested: ${request.quantity}")

        var remaining = request.quantity
        var batchUsed: DrugBatch? = null
        for (batch in availableBatches) {
            if (remaining <= 0) break
            val deduct = minOf(remaining, batch.quantityRemaining)
            batch.quantityRemaining -= deduct
            batchRepository.save(batch)
            remaining -= deduct
            batchUsed = batch
        }

        val totalPrice = drug.sellingPrice.multiply(BigDecimal(request.quantity))
        val dispense = dispenseRepository.save(
            DrugDispense(
                clinicId = clinicId, drug = drug, batchId = batchUsed?.id,
                consultationId = request.consultationId, patientId = request.patientId,
                dispensedBy = dispensedBy, quantity = request.quantity,
                unitPrice = drug.sellingPrice, totalPrice = totalPrice,
                instructions = request.instructions
            )
        )
        auditService.log(clinicId, dispensedBy, "DISPENSE_DRUG", "drug_dispense", dispense.id,
            newValues = mapOf("drug" to drug.name, "quantity" to request.quantity, "patient" to request.patientId))
        return DispenseResponse(
            id = dispense.id, drugName = drug.name, patientId = dispense.patientId,
            quantity = dispense.quantity, totalPrice = dispense.totalPrice,
            instructions = dispense.instructions, dispensedAt = dispense.dispensedAt
        )
    }

    @Transactional(readOnly = true)
    fun getStockAlerts(clinicId: UUID): StockAlertSummary {
        val alerts = jdbcTemplate.queryForList("""
            SELECT drug_id, name, total_stock, reorder_level, stock_status,
                   batches_expiring_soon, nearest_expiry
            FROM v_drug_stock
            WHERE clinic_id = :clinicId
            AND (stock_status IN ('LOW_STOCK','OUT_OF_STOCK') OR batches_expiring_soon > 0)
            ORDER BY stock_status DESC, total_stock ASC
        """, MapSqlParameterSource("clinicId", clinicId))

        val alertList = alerts.flatMap { row ->
            val results = mutableListOf<StockAlert>()
            val status = row["stock_status"] as String
            if (status in listOf("LOW_STOCK", "OUT_OF_STOCK")) {
                results.add(StockAlert(
                    drugId = UUID.fromString(row["drug_id"].toString()),
                    drugName = row["name"] as String,
                    alertType = status,
                    currentStock = (row["total_stock"] as Number).toInt(),
                    reorderLevel = (row["reorder_level"] as Number).toInt(),
                    expiryDate = null
                ))
            }
            val expSoon = (row["batches_expiring_soon"] as Number).toInt()
            if (expSoon > 0) {
                results.add(StockAlert(
                    drugId = UUID.fromString(row["drug_id"].toString()),
                    drugName = row["name"] as String,
                    alertType = "EXPIRING_SOON",
                    currentStock = (row["total_stock"] as Number).toInt(),
                    reorderLevel = (row["reorder_level"] as Number).toInt(),
                    expiryDate = (row["nearest_expiry"] as? java.sql.Date)?.toLocalDate()
                ))
            }
            results
        }
        return StockAlertSummary(
            lowStockCount = alertList.count { it.alertType == "LOW_STOCK" },
            outOfStockCount = alertList.count { it.alertType == "OUT_OF_STOCK" },
            expiringSoonCount = alertList.count { it.alertType == "EXPIRING_SOON" },
            expiredCount = alertList.count { it.alertType == "EXPIRED" },
            alerts = alertList
        )
    }

    @Transactional(readOnly = true)
    fun getStockSummary(clinicId: UUID, drugId: UUID): DrugStockResponse {
        val drug = drugRepository.findByIdAndClinicId(drugId, clinicId)
            ?: throw NotFoundException("Drug not found")
        val batches = batchRepository.findAvailableBatches(clinicId, drugId)
        val totalStock = batches.sumOf { it.quantityRemaining }
        return DrugStockResponse(
            drugId = drug.id, name = drug.name, genericName = drug.genericName,
            category = drug.category, unit = drug.unit, strength = drug.strength,
            totalStock = totalStock, reorderLevel = drug.reorderLevel,
            sellingPrice = drug.sellingPrice,
            stockStatus = when {
                totalStock == 0 -> "OUT_OF_STOCK"
                totalStock <= drug.reorderLevel -> "LOW_STOCK"
                else -> "IN_STOCK"
            },
            batchesExpiringSoon = batches.count { it.isExpiringSoon },
            nearestExpiry = batches.minOfOrNull { it.expiryDate },
            batches = batches.map { it.toBatchSummary() }
        )
    }

    private fun DrugBatch.toBatchSummary() = BatchSummary(
        id = id, batchNumber = batchNumber, quantityRemaining = quantityRemaining,
        expiryDate = expiryDate, unitCost = unitCost, isExpired = isExpired,
        isExpiringSoon = isExpiringSoon
    )
}
