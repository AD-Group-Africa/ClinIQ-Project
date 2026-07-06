package com.cliniq.domain.billing.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "invoices")
data class Invoice(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @Column(name = "patient_id", nullable = false)
    val patientId: UUID,

    @Column(name = "appointment_id")
    val appointmentId: UUID? = null,

    @Column(name = "invoice_number", unique = true, nullable = false)
    var invoiceNumber: String,

    @Column(name = "issued_at")
    val issuedAt: Instant = Instant.now(),

    @Column(name = "due_date")
    var dueDate: LocalDate? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var lineItems: List<Map<String, Any>> = emptyList(),

    @Column
    var subtotal: BigDecimal = BigDecimal.ZERO,

    @Column
    var discount: BigDecimal = BigDecimal.ZERO,

    @Column
    var tax: BigDecimal = BigDecimal.ZERO,

    @Column
    var total: BigDecimal = BigDecimal.ZERO,

    @Column(name = "paid_amount")
    var paidAmount: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    var status: InvoiceStatus = InvoiceStatus.DRAFT,

    @Column
    var notes: String? = null,

    @Column(name = "created_by")
    val createdBy: UUID? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
) {
    val balance get() = total.subtract(paidAmount)
    val isPaid get() = status == InvoiceStatus.PAID
}

enum class InvoiceStatus {
    DRAFT, ISSUED, PAID, PARTIAL, OVERDUE, CANCELLED, REFUNDED
}

@Entity
@Table(name = "payments")
data class Payment(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @Column(name = "invoice_id", nullable = false)
    val invoiceId: UUID,

    @Column(name = "patient_id")
    val patientId: UUID? = null,

    @Column(nullable = false)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    val method: PaymentMethod,

    @Column
    var reference: String? = null,

    @Column(name = "mpesa_phone")
    var mpesaPhone: String? = null,

    @Column
    var notes: String? = null,

    @Column(name = "paid_at")
    val paidAt: Instant = Instant.now(),

    @Column(name = "recorded_by")
    val recordedBy: UUID? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)

enum class PaymentMethod {
    CASH, MPESA, CARD, INSURANCE, BANK_TRANSFER, ONLINE
}
