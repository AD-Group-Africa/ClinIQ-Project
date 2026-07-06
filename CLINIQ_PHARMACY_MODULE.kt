// =============================================================
// ClinIQ — Pharmacy & Inventory Module
// Package: com.cliniq.domain.pharmacy
// =============================================================
// Files:
//   entity/Drug.kt
//   entity/DrugBatch.kt
//   entity/DrugDispense.kt
//   entity/Supplier.kt
//   entity/PurchaseOrder.kt
//   dto/PharmacyDTOs.kt
//   repository/DrugRepository.kt
//   repository/DrugBatchRepository.kt
//   service/PharmacyService.kt
//   service/InventoryAlertService.kt
//   controller/PharmacyController.kt
// =============================================================
// DB migration: add to V5__pharmacy_lab.sql
// =============================================================


// =============================================
// V5__pharmacy_lab.sql (Flyway migration)
// =============================================
/*
CREATE TYPE drug_category AS ENUM (
    'ANALGESIC','ANTIBIOTIC','ANTIFUNGAL','ANTIVIRAL','ANTIPARASITIC',
    'ANTIHYPERTENSIVE','ANTIDIABETIC','ANTIHISTAMINE','ANTACID',
    'VITAMIN','VACCINE','CONTRACEPTIVE','NSAID','STEROID','OTHER'
);
CREATE TYPE dispensing_unit AS ENUM (
    'TABLETS','CAPSULES','SYRUP_ML','INJECTION_VIALS','CREAM_TUBE',
    'DROPS','SACHETS','INHALER','SUPPOSITORY','PATCH'
);
CREATE TYPE order_status AS ENUM (
    'DRAFT','ORDERED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED'
);

CREATE TABLE drugs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    name            VARCHAR(300) NOT NULL,
    generic_name    VARCHAR(300),
    brand_name      VARCHAR(300),
    category        drug_category DEFAULT 'OTHER',
    unit            dispensing_unit DEFAULT 'TABLETS',
    strength        VARCHAR(100),       -- e.g. "500mg", "250mg/5ml"
    description     TEXT,
    requires_prescription BOOLEAN DEFAULT TRUE,
    is_controlled   BOOLEAN DEFAULT FALSE,  -- controlled substance flag
    reorder_level   INTEGER DEFAULT 10,     -- trigger low-stock alert
    selling_price   DECIMAL(10,2) DEFAULT 0,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE suppliers (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    contact_name    VARCHAR(200),
    phone           VARCHAR(20),
    email           VARCHAR(200),
    address         TEXT,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE drug_batches (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    drug_id         UUID REFERENCES drugs(id) ON DELETE CASCADE,
    supplier_id     UUID REFERENCES suppliers(id) ON DELETE SET NULL,
    batch_number    VARCHAR(100) NOT NULL,
    quantity_received INTEGER NOT NULL,
    quantity_remaining INTEGER NOT NULL,
    unit_cost       DECIMAL(10,2) DEFAULT 0,
    expiry_date     DATE NOT NULL,
    received_at     TIMESTAMPTZ DEFAULT NOW(),
    received_by     UUID REFERENCES users(id),
    notes           TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE drug_dispenses (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    drug_id         UUID REFERENCES drugs(id) ON DELETE CASCADE,
    batch_id        UUID REFERENCES drug_batches(id) ON DELETE SET NULL,
    consultation_id UUID REFERENCES consultations(id) ON DELETE SET NULL,
    patient_id      UUID REFERENCES patients(id) ON DELETE CASCADE,
    prescribed_by   UUID REFERENCES users(id),  -- doctor
    dispensed_by    UUID REFERENCES users(id),  -- pharmacist
    quantity        INTEGER NOT NULL,
    unit_price      DECIMAL(10,2),
    total_price     DECIMAL(10,2),
    instructions    TEXT,
    dispensed_at    TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE purchase_orders (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    supplier_id     UUID REFERENCES suppliers(id) ON DELETE SET NULL,
    order_number    VARCHAR(50) UNIQUE,
    status          order_status DEFAULT 'DRAFT',
    items           JSONB NOT NULL DEFAULT '[]',
    -- [{drug_id, drug_name, quantity_ordered, quantity_received, unit_cost}]
    total_amount    DECIMAL(12,2) DEFAULT 0,
    ordered_at      TIMESTAMPTZ,
    received_at     TIMESTAMPTZ,
    notes           TEXT,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- FAST STOCK QUERIES
CREATE INDEX idx_drug_batches_drug ON drug_batches(clinic_id, drug_id, expiry_date);
CREATE INDEX idx_drug_batches_expiry ON drug_batches(clinic_id, expiry_date)
    WHERE quantity_remaining > 0;
CREATE INDEX idx_dispenses_patient ON drug_dispenses(clinic_id, patient_id, dispensed_at DESC);
CREATE INDEX idx_dispenses_drug ON drug_dispenses(clinic_id, drug_id, dispensed_at DESC);

-- Stock level view
CREATE VIEW v_drug_stock AS
SELECT
    d.clinic_id,
    d.id AS drug_id,
    d.name,
    d.generic_name,
    d.category,
    d.unit,
    d.strength,
    d.reorder_level,
    d.selling_price,
    COALESCE(SUM(b.quantity_remaining), 0) AS total_stock,
    COUNT(b.id) FILTER (WHERE b.expiry_date < CURRENT_DATE + INTERVAL '30 days'
        AND b.quantity_remaining > 0) AS batches_expiring_soon,
    MIN(b.expiry_date) FILTER (WHERE b.quantity_remaining > 0) AS nearest_expiry,
    CASE
        WHEN COALESCE(SUM(b.quantity_remaining), 0) = 0 THEN 'OUT_OF_STOCK'
        WHEN COALESCE(SUM(b.quantity_remaining), 0) <= d.reorder_level THEN 'LOW_STOCK'
        ELSE 'IN_STOCK'
    END AS stock_status
FROM drugs d
LEFT JOIN drug_batches b ON b.drug_id = d.id AND b.clinic_id = d.clinic_id
WHERE d.is_active = TRUE
GROUP BY d.clinic_id, d.id, d.name, d.generic_name, d.category, d.unit,
         d.strength, d.reorder_level, d.selling_price;
*/


// =============================================
// entity/Drug.kt
// =============================================
package com.cliniq.domain.pharmacy.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "drugs")
data class Drug(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @Column(nullable = false)
    var name: String,

    @Column(name = "generic_name")
    var genericName: String? = null,

    @Column(name = "brand_name")
    var brandName: String? = null,

    @Enumerated(EnumType.STRING)
    var category: DrugCategory = DrugCategory.OTHER,

    @Enumerated(EnumType.STRING)
    var unit: DispensingUnit = DispensingUnit.TABLETS,

    @Column
    var strength: String? = null,

    @Column
    var description: String? = null,

    @Column(name = "requires_prescription")
    var requiresPrescription: Boolean = true,

    @Column(name = "is_controlled")
    var isControlled: Boolean = false,

    @Column(name = "reorder_level")
    var reorderLevel: Int = 10,

    @Column(name = "selling_price")
    var sellingPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
)

enum class DrugCategory {
    ANALGESIC, ANTIBIOTIC, ANTIFUNGAL, ANTIVIRAL, ANTIPARASITIC,
    ANTIHYPERTENSIVE, ANTIDIABETIC, ANTIHISTAMINE, ANTACID,
    VITAMIN, VACCINE, CONTRACEPTIVE, NSAID, STEROID, OTHER
}

enum class DispensingUnit {
    TABLETS, CAPSULES, SYRUP_ML, INJECTION_VIALS, CREAM_TUBE,
    DROPS, SACHETS, INHALER, SUPPOSITORY, PATCH
}


// =============================================
// entity/DrugBatch.kt
// =============================================
package com.cliniq.domain.pharmacy.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "drug_batches")
data class DrugBatch(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drug_id")
    val drug: Drug,

    @Column(name = "supplier_id")
    var supplierId: UUID? = null,

    @Column(name = "batch_number", nullable = false)
    val batchNumber: String,

    @Column(name = "quantity_received", nullable = false)
    val quantityReceived: Int,

    @Column(name = "quantity_remaining", nullable = false)
    var quantityRemaining: Int,

    @Column(name = "unit_cost")
    val unitCost: BigDecimal = BigDecimal.ZERO,

    @Column(name = "expiry_date", nullable = false)
    val expiryDate: LocalDate,

    @Column(name = "received_by")
    val receivedBy: UUID? = null,

    @Column
    var notes: String? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
) {
    val isExpired get() = expiryDate.isBefore(LocalDate.now())
    val isExpiringSoon get() = expiryDate.isBefore(LocalDate.now().plusDays(30))
}


// =============================================
// entity/DrugDispense.kt
// =============================================
package com.cliniq.domain.pharmacy.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "drug_dispenses")
data class DrugDispense(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drug_id")
    val drug: Drug,

    @Column(name = "batch_id")
    val batchId: UUID? = null,

    @Column(name = "consultation_id")
    val consultationId: UUID? = null,

    @Column(name = "patient_id", nullable = false)
    val patientId: UUID,

    @Column(name = "prescribed_by")
    val prescribedBy: UUID? = null,

    @Column(name = "dispensed_by")
    val dispensedBy: UUID? = null,

    @Column(nullable = false)
    val quantity: Int,

    @Column(name = "unit_price")
    val unitPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_price")
    val totalPrice: BigDecimal = BigDecimal.ZERO,

    @Column
    var instructions: String? = null,

    @Column(name = "dispensed_at")
    val dispensedAt: Instant = Instant.now()
)


// =============================================
// dto/PharmacyDTOs.kt
// =============================================
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
    val genericName: String? = null,
    val brandName: String? = null,
    val category: DrugCategory = DrugCategory.OTHER,
    val unit: DispensingUnit = DispensingUnit.TABLETS,
    val strength: String? = null,
    val description: String? = null,
    val requiresPrescription: Boolean = true,
    val isControlled: Boolean = false,
    @field:Min(0) val reorderLevel: Int = 10,
    @field:DecimalMin("0.0") val sellingPrice: BigDecimal = BigDecimal.ZERO
)

data class AddStockRequest(
    @field:NotNull val drugId: UUID,
    val supplierId: UUID? = null,
    @field:NotBlank val batchNumber: String,
    @field:Min(1) val quantity: Int,
    @field:DecimalMin("0.0") val unitCost: BigDecimal = BigDecimal.ZERO,
    @field:Future val expiryDate: LocalDate,
    val notes: String? = null
)

data class DispenseRequest(
    @field:NotNull val drugId: UUID,
    @field:NotNull val patientId: UUID,
    val consultationId: UUID? = null,
    @field:Min(1) val quantity: Int,
    val instructions: String? = null
)

data class DrugStockResponse(
    val drugId: UUID,
    val name: String,
    val genericName: String?,
    val category: DrugCategory,
    val unit: DispensingUnit,
    val strength: String?,
    val totalStock: Int,
    val reorderLevel: Int,
    val sellingPrice: BigDecimal,
    val stockStatus: String,     // IN_STOCK | LOW_STOCK | OUT_OF_STOCK
    val batchesExpiringSoon: Int,
    val nearestExpiry: LocalDate?,
    val batches: List<BatchSummary> = emptyList()
)

data class BatchSummary(
    val id: UUID,
    val batchNumber: String,
    val quantityRemaining: Int,
    val expiryDate: LocalDate,
    val unitCost: BigDecimal,
    val isExpired: Boolean,
    val isExpiringSoon: Boolean
)

data class DispenseResponse(
    val id: UUID,
    val drugName: String,
    val patientId: UUID,
    val quantity: Int,
    val totalPrice: BigDecimal,
    val instructions: String?,
    val dispensedAt: Instant
)

data class StockAlertSummary(
    val lowStockCount: Int,
    val outOfStockCount: Int,
    val expiringSoonCount: Int,
    val expiredCount: Int,
    val alerts: List<StockAlert>
)

data class StockAlert(
    val drugId: UUID,
    val drugName: String,
    val alertType: String,   // LOW_STOCK | OUT_OF_STOCK | EXPIRING_SOON | EXPIRED
    val currentStock: Int,
    val reorderLevel: Int,
    val expiryDate: LocalDate?
)


// =============================================
// repository/DrugRepository.kt
// =============================================
package com.cliniq.domain.pharmacy.repository

import com.cliniq.domain.pharmacy.entity.Drug
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DrugRepository : JpaRepository<Drug, UUID> {

    fun findByClinicIdAndIsActiveTrue(clinicId: UUID, pageable: Pageable): Page<Drug>

    @Query("""
        SELECT d FROM Drug d
        WHERE d.clinicId = :clinicId
        AND d.isActive = TRUE
        AND (LOWER(d.name) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(d.genericName) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(d.brandName) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY d.name
    """)
    fun search(clinicId: UUID, q: String, pageable: Pageable): Page<Drug>

    fun findByIdAndClinicId(id: UUID, clinicId: UUID): Drug?
}


// =============================================
// repository/DrugBatchRepository.kt
// =============================================
package com.cliniq.domain.pharmacy.repository

import com.cliniq.domain.pharmacy.entity.DrugBatch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
interface DrugBatchRepository : JpaRepository<DrugBatch, UUID> {

    // Batches for a drug, ordered by expiry (FEFO — first expired first out)
    @Query("""
        SELECT b FROM DrugBatch b
        WHERE b.drug.id = :drugId
        AND b.clinicId = :clinicId
        AND b.quantityRemaining > 0
        AND b.expiryDate > CURRENT_DATE
        ORDER BY b.expiryDate ASC
    """)
    fun findAvailableBatches(clinicId: UUID, drugId: UUID): List<DrugBatch>

    @Query("""
        SELECT b FROM DrugBatch b
        WHERE b.clinicId = :clinicId
        AND b.expiryDate < :threshold
        AND b.quantityRemaining > 0
        ORDER BY b.expiryDate ASC
    """)
    fun findExpiringSoon(clinicId: UUID, threshold: LocalDate): List<DrugBatch>

    @Query("""
        SELECT SUM(b.quantityRemaining) FROM DrugBatch b
        WHERE b.drug.id = :drugId
        AND b.clinicId = :clinicId
        AND b.quantityRemaining > 0
        AND b.expiryDate > CURRENT_DATE
    """)
    fun totalAvailableStock(clinicId: UUID, drugId: UUID): Int?
}


// =============================================
// service/PharmacyService.kt
// =============================================
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

    // -----------------------------------------------
    // Drug catalog management
    // -----------------------------------------------

    fun addDrug(clinicId: UUID, request: CreateDrugRequest, createdBy: UUID): DrugStockResponse {
        val drug = drugRepository.save(
            Drug(
                clinicId = clinicId,
                name = request.name,
                genericName = request.genericName,
                brandName = request.brandName,
                category = request.category,
                unit = request.unit,
                strength = request.strength,
                description = request.description,
                requiresPrescription = request.requiresPrescription,
                isControlled = request.isControlled,
                reorderLevel = request.reorderLevel,
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
                drugId = drug.id,
                name = drug.name,
                genericName = drug.genericName,
                category = drug.category,
                unit = drug.unit,
                strength = drug.strength,
                totalStock = stock,
                reorderLevel = drug.reorderLevel,
                sellingPrice = drug.sellingPrice,
                stockStatus = when {
                    stock == 0 -> "OUT_OF_STOCK"
                    stock <= drug.reorderLevel -> "LOW_STOCK"
                    else -> "IN_STOCK"
                },
                batchesExpiringSoon = expiringSoon,
                nearestExpiry = null
            )
        }
    }

    // -----------------------------------------------
    // Stock management (FEFO: first expired, first out)
    // -----------------------------------------------

    fun addStock(clinicId: UUID, request: AddStockRequest, receivedBy: UUID): BatchSummary {
        val drug = drugRepository.findByIdAndClinicId(request.drugId, clinicId)
            ?: throw NotFoundException("Drug not found")

        if (request.expiryDate.isBefore(LocalDate.now())) {
            throw BadRequestException("Cannot add stock with past expiry date")
        }

        val batch = batchRepository.save(
            DrugBatch(
                clinicId = clinicId,
                drug = drug,
                supplierId = request.supplierId,
                batchNumber = request.batchNumber,
                quantityReceived = request.quantity,
                quantityRemaining = request.quantity,
                unitCost = request.unitCost,
                expiryDate = request.expiryDate,
                receivedBy = receivedBy,
                notes = request.notes
            )
        )

        auditService.log(
            clinicId, receivedBy, "ADD_STOCK", "drug_batch", batch.id,
            newValues = mapOf("drug" to drug.name, "quantity" to request.quantity, "batch" to request.batchNumber)
        )

        log.info("Stock added: {} × {} units, expires {}", drug.name, request.quantity, request.expiryDate)

        return batch.toBatchSummary()
    }

    fun dispense(clinicId: UUID, request: DispenseRequest, dispensedBy: UUID): DispenseResponse {
        val drug = drugRepository.findByIdAndClinicId(request.drugId, clinicId)
            ?: throw NotFoundException("Drug not found")

        // Find available batches (FEFO order)
        val availableBatches = batchRepository.findAvailableBatches(clinicId, drug.id)
        val totalAvailable = availableBatches.sumOf { it.quantityRemaining }

        if (totalAvailable < request.quantity) {
            throw ConflictException(
                "Insufficient stock for ${drug.name}. Available: $totalAvailable, Requested: ${request.quantity}"
            )
        }

        // Deduct from batches in FEFO order
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
                clinicId = clinicId,
                drug = drug,
                batchId = batchUsed?.id,
                consultationId = request.consultationId,
                patientId = request.patientId,
                dispensedBy = dispensedBy,
                quantity = request.quantity,
                unitPrice = drug.sellingPrice,
                totalPrice = totalPrice,
                instructions = request.instructions
            )
        )

        auditService.log(
            clinicId, dispensedBy, "DISPENSE_DRUG", "drug_dispense", dispense.id,
            newValues = mapOf("drug" to drug.name, "quantity" to request.quantity, "patient" to request.patientId)
        )

        return DispenseResponse(
            id = dispense.id,
            drugName = drug.name,
            patientId = dispense.patientId,
            quantity = dispense.quantity,
            totalPrice = dispense.totalPrice,
            instructions = dispense.instructions,
            dispensedAt = dispense.dispensedAt
        )
    }

    // -----------------------------------------------
    // Stock level report (uses the database view)
    // -----------------------------------------------

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
            val expiringSoon = (row["batches_expiring_soon"] as Number).toInt()
            if (expiringSoon > 0) {
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
            drugId = drug.id,
            name = drug.name,
            genericName = drug.genericName,
            category = drug.category,
            unit = drug.unit,
            strength = drug.strength,
            totalStock = totalStock,
            reorderLevel = drug.reorderLevel,
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
        id = id,
        batchNumber = batchNumber,
        quantityRemaining = quantityRemaining,
        expiryDate = expiryDate,
        unitCost = unitCost,
        isExpired = isExpired,
        isExpiringSoon = isExpiringSoon
    )
}


// =============================================
// service/InventoryAlertService.kt
// =============================================
package com.cliniq.domain.pharmacy.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class InventoryAlertService(
    private val pharmacyService: PharmacyService,
    private val clinicRepository: com.cliniq.domain.clinic.repository.ClinicRepository,
    private val notificationService: com.cliniq.domain.communication.service.NotificationService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Daily check at 7 AM — sends stock alerts to clinic admins.
     */
    @Scheduled(cron = "0 0 7 * * *")
    @Transactional(readOnly = true)
    fun sendDailyInventoryAlerts() {
        val clinics = clinicRepository.findAllActive()
        log.info("Running inventory alerts for {} clinics", clinics.size)

        clinics.forEach { clinic ->
            try {
                val summary = pharmacyService.getStockAlerts(clinic.id)
                if (summary.alerts.isNotEmpty()) {
                    log.info(
                        "Clinic {}: {} low stock, {} out of stock, {} expiring soon",
                        clinic.name, summary.lowStockCount, summary.outOfStockCount, summary.expiringSoonCount
                    )
                    notificationService.sendInventoryAlert(clinic, summary)
                }
            } catch (e: Exception) {
                log.error("Failed inventory alert for clinic {}", clinic.id, e)
            }
        }
    }
}


// =============================================
// controller/PharmacyController.kt
// =============================================
package com.cliniq.domain.pharmacy.controller

import com.cliniq.domain.pharmacy.dto.*
import com.cliniq.domain.pharmacy.service.PharmacyService
import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.shared.response.ApiResponse
import com.cliniq.shared.response.PageMeta
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/pharmacy")
class PharmacyController(private val pharmacyService: PharmacyService) {

    // Catalog
    @PostMapping("/drugs")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','SUPER_ADMIN')")
    fun addDrug(
        @Valid @RequestBody request: CreateDrugRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.status(201).body(
        ApiResponse.success(pharmacyService.addDrug(auth.clinicId!!, request, auth.userId))
    )

    @GetMapping("/drugs")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','DOCTOR','NURSE','RECEPTIONIST','SUPER_ADMIN')")
    fun listDrugs(
        @RequestParam(defaultValue = "") q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<List<DrugStockResponse>>> {
        val result = pharmacyService.searchDrugs(auth.clinicId!!, q, PageRequest.of(page, size))
        return ResponseEntity.ok(ApiResponse.success(
            result.content,
            PageMeta(page, size, result.totalElements, result.totalPages)
        ))
    }

    @GetMapping("/drugs/{id}/stock")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','DOCTOR','NURSE','SUPER_ADMIN')")
    fun drugStock(
        @PathVariable id: UUID,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.ok(ApiResponse.success(pharmacyService.getStockSummary(auth.clinicId!!, id)))

    // Stock management
    @PostMapping("/stock/add")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','SUPER_ADMIN')")
    fun addStock(
        @Valid @RequestBody request: AddStockRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.status(201).body(
        ApiResponse.success(pharmacyService.addStock(auth.clinicId!!, request, auth.userId))
    )

    // Dispensing
    @PostMapping("/dispense")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','NURSE','DOCTOR','SUPER_ADMIN')")
    fun dispense(
        @Valid @RequestBody request: DispenseRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.status(201).body(
        ApiResponse.success(pharmacyService.dispense(auth.clinicId!!, request, auth.userId))
    )

    // Alerts
    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','SUPER_ADMIN')")
    fun alerts(@AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(pharmacyService.getStockAlerts(auth.clinicId!!)))
}
