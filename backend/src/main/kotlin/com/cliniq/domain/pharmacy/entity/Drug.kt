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
    @Column(name = "clinic_id", nullable = false) val clinicId: UUID,
    @Column(nullable = false) var name: String,
    @Column(name = "generic_name") var genericName: String? = null,
    @Column(name = "brand_name") var brandName: String? = null,
    @Enumerated(EnumType.STRING) var category: DrugCategory = DrugCategory.OTHER,
    @Enumerated(EnumType.STRING) var unit: DispensingUnit = DispensingUnit.TABLETS,
    @Column var strength: String? = null,
    @Column var description: String? = null,
    @Column(name = "requires_prescription") var requiresPrescription: Boolean = true,
    @Column(name = "is_controlled") var isControlled: Boolean = false,
    @Column(name = "reorder_level") var reorderLevel: Int = 10,
    @Column(name = "selling_price") var sellingPrice: BigDecimal = BigDecimal.ZERO,
    @Column(name = "is_active") var isActive: Boolean = true,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now()
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
