// =============================================================
// ClinIQ — Billing & Payments Module
// Covers: Invoice creation, Payment recording, M-Pesa Daraja
//         STK push, Revenue reports, Invoice PDF
// =============================================================

// =============================================
// entity/Invoice.kt
// com.cliniq.domain.billing.entity
// =============================================
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

    /**
     * Line items stored as JSONB:
     * [{description, quantity, unitPrice, total}]
     */
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
    var reference: String? = null,     // M-Pesa transaction code

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


// =============================================
// dto/BillingDTOs.kt
// =============================================
package com.cliniq.domain.billing.dto

import com.cliniq.domain.billing.entity.InvoiceStatus
import com.cliniq.domain.billing.entity.PaymentMethod
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateInvoiceRequest(
    @field:NotNull val patientId: UUID,
    val appointmentId: UUID? = null,
    @field:NotEmpty val lineItems: List<LineItemRequest>,
    val discount: BigDecimal = BigDecimal.ZERO,
    val notes: String? = null,
    val dueDate: LocalDate? = null
)

data class LineItemRequest(
    @field:NotBlank val description: String,
    @field:Min(1) val quantity: Int = 1,
    @field:DecimalMin("0.0") val unitPrice: BigDecimal
)

data class RecordPaymentRequest(
    @field:NotNull val invoiceId: UUID,
    @field:DecimalMin("0.01") val amount: BigDecimal,
    val method: PaymentMethod = PaymentMethod.CASH,
    val reference: String? = null,
    val mpesaPhone: String? = null,
    val notes: String? = null
)

data class InitiateMpesaRequest(
    @field:NotNull val invoiceId: UUID,
    @field:NotBlank val phone: String,
    @field:DecimalMin("1.0") val amount: BigDecimal
)

data class InvoiceResponse(
    val id: UUID,
    val invoiceNumber: String,
    val patientId: UUID,
    val patientName: String,
    val appointmentId: UUID?,
    val lineItems: List<Map<String, Any>>,
    val subtotal: BigDecimal,
    val discount: BigDecimal,
    val tax: BigDecimal,
    val total: BigDecimal,
    val paidAmount: BigDecimal,
    val balance: BigDecimal,
    val status: InvoiceStatus,
    val notes: String?,
    val dueDate: LocalDate?,
    val issuedAt: Instant,
    val payments: List<PaymentResponse> = emptyList()
)

data class PaymentResponse(
    val id: UUID,
    val amount: BigDecimal,
    val method: PaymentMethod,
    val reference: String?,
    val paidAt: Instant
)

data class RevenueReport(
    val period: String,
    val totalInvoiced: BigDecimal,
    val totalCollected: BigDecimal,
    val outstandingBalance: BigDecimal,
    val invoiceCount: Int,
    val paidCount: Int,
    val byMethod: Map<String, BigDecimal>,
    val topPatients: List<TopPatient>
)

data class TopPatient(
    val patientId: UUID,
    val patientName: String,
    val totalSpend: BigDecimal,
    val visitCount: Int
)

data class MpesaStkPushResponse(
    val success: Boolean,
    val checkoutRequestId: String?,
    val message: String
)


// =============================================
// repository/BillingRepository.kt
// =============================================
package com.cliniq.domain.billing.repository

import com.cliniq.domain.billing.entity.Invoice
import com.cliniq.domain.billing.entity.InvoiceStatus
import com.cliniq.domain.billing.entity.Payment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Repository
interface InvoiceRepository : JpaRepository<Invoice, UUID> {

    fun findByIdAndClinicId(id: UUID, clinicId: UUID): Invoice?

    @Query("SELECT i FROM Invoice i WHERE i.clinicId = :clinicId AND i.patientId = :patientId ORDER BY i.issuedAt DESC")
    fun findByPatient(clinicId: UUID, patientId: UUID, pageable: Pageable): Page<Invoice>

    @Query("SELECT i FROM Invoice i WHERE i.clinicId = :clinicId AND i.status = :status ORDER BY i.issuedAt DESC")
    fun findByStatus(clinicId: UUID, status: InvoiceStatus, pageable: Pageable): Page<Invoice>

    @Query("SELECT COALESCE(SUM(i.total), 0) FROM Invoice i WHERE i.clinicId = :clinicId AND i.issuedAt >= :from AND i.issuedAt < :to")
    fun sumRevenue(clinicId: UUID, from: Instant, to: Instant): BigDecimal

    @Query("SELECT COALESCE(SUM(i.paidAmount), 0) FROM Invoice i WHERE i.clinicId = :clinicId AND i.issuedAt >= :from AND i.issuedAt < :to")
    fun sumCollected(clinicId: UUID, from: Instant, to: Instant): BigDecimal

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.clinicId = :clinicId AND i.status = :status")
    fun countByStatus(clinicId: UUID, status: InvoiceStatus): Long

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(i.invoiceNumber, LENGTH(:prefix)+1) AS INTEGER)), 0) FROM Invoice i WHERE i.clinicId = :clinicId AND i.invoiceNumber LIKE CONCAT(:prefix,'%')")
    fun findMaxSequence(clinicId: UUID, prefix: String): Int?
}

@Repository
interface PaymentRepository : JpaRepository<Payment, UUID> {

    fun findByInvoiceId(invoiceId: UUID): List<Payment>

    @Query("SELECT p FROM Payment p WHERE p.clinicId = :clinicId AND p.paidAt >= :from AND p.paidAt < :to ORDER BY p.paidAt DESC")
    fun findByPeriod(clinicId: UUID, from: Instant, to: Instant): List<Payment>

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.clinicId = :clinicId AND p.method = :method AND p.paidAt >= :from AND p.paidAt < :to")
    fun sumByMethod(clinicId: UUID, method: com.cliniq.domain.billing.entity.PaymentMethod, from: Instant, to: Instant): BigDecimal
}


// =============================================
// service/BillingService.kt
// =============================================
package com.cliniq.domain.billing.service

import com.cliniq.domain.billing.dto.*
import com.cliniq.domain.billing.entity.*
import com.cliniq.domain.billing.repository.InvoiceRepository
import com.cliniq.domain.billing.repository.PaymentRepository
import com.cliniq.domain.patient.repository.PatientRepository
import com.cliniq.shared.audit.AuditService
import com.cliniq.shared.exceptions.*
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

@Service
@Transactional
class BillingService(
    private val invoiceRepository: InvoiceRepository,
    private val paymentRepository: PaymentRepository,
    private val patientRepository: PatientRepository,
    private val mpesaService: MpesaService,
    private val auditService: AuditService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    // -----------------------------------------------
    // Invoice management
    // -----------------------------------------------

    fun createInvoice(clinicId: UUID, request: CreateInvoiceRequest, createdBy: UUID): InvoiceResponse {
        val patient = patientRepository.findByIdAndClinicId(request.patientId, clinicId)
            ?: throw NotFoundException("Patient not found")

        val lineItems = request.lineItems.map { item ->
            val total = item.unitPrice.multiply(BigDecimal(item.quantity))
            mapOf(
                "description" to item.description,
                "quantity" to item.quantity,
                "unitPrice" to item.unitPrice,
                "total" to total
            )
        }

        val subtotal = lineItems.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(item["total"] as BigDecimal)
        }
        val discountedSubtotal = subtotal.subtract(request.discount)
        val tax = BigDecimal.ZERO  // No VAT by default; configure per clinic
        val total = discountedSubtotal.add(tax)

        val invoice = invoiceRepository.save(
            Invoice(
                clinicId = clinicId,
                patientId = request.patientId,
                appointmentId = request.appointmentId,
                invoiceNumber = generateInvoiceNumber(clinicId),
                lineItems = lineItems,
                subtotal = subtotal,
                discount = request.discount,
                tax = tax,
                total = total,
                paidAmount = BigDecimal.ZERO,
                status = InvoiceStatus.ISSUED,
                notes = request.notes,
                dueDate = request.dueDate ?: LocalDate.now().plusDays(7),
                createdBy = createdBy
            )
        )

        auditService.log(clinicId, createdBy, "CREATE_INVOICE", "invoice", invoice.id,
            newValues = mapOf("total" to total, "patient" to patient.fullName))

        log.info("Invoice {} created for {} — KES {}", invoice.invoiceNumber, patient.fullName, total)
        return invoice.toResponse(patient.fullName, emptyList())
    }

    fun recordPayment(clinicId: UUID, request: RecordPaymentRequest, recordedBy: UUID): InvoiceResponse {
        val invoice = invoiceRepository.findByIdAndClinicId(request.invoiceId, clinicId)
            ?: throw NotFoundException("Invoice not found")

        if (request.amount > invoice.balance) {
            throw BadRequestException("Payment amount (${request.amount}) exceeds balance (${invoice.balance})")
        }

        val payment = paymentRepository.save(
            Payment(
                clinicId = clinicId,
                invoiceId = invoice.id,
                patientId = invoice.patientId,
                amount = request.amount,
                method = request.method,
                reference = request.reference,
                mpesaPhone = request.mpesaPhone,
                notes = request.notes,
                recordedBy = recordedBy
            )
        )

        invoice.paidAmount = invoice.paidAmount.add(request.amount)
        invoice.status = when {
            invoice.paidAmount >= invoice.total -> InvoiceStatus.PAID
            invoice.paidAmount > BigDecimal.ZERO -> InvoiceStatus.PARTIAL
            else -> InvoiceStatus.ISSUED
        }
        invoice.updatedAt = Instant.now()
        invoiceRepository.save(invoice)

        auditService.log(clinicId, recordedBy, "RECORD_PAYMENT", "payment", payment.id,
            newValues = mapOf("amount" to request.amount, "method" to request.method, "invoice" to invoice.invoiceNumber))

        val patient = patientRepository.findById(invoice.patientId).orElse(null)
        val payments = paymentRepository.findByInvoiceId(invoice.id)
        return invoice.toResponse(patient?.fullName ?: "Unknown", payments)
    }

    // -----------------------------------------------
    // M-Pesa STK Push
    // -----------------------------------------------

    fun initiateMpesaPayment(clinicId: UUID, request: InitiateMpesaRequest): MpesaStkPushResponse {
        val invoice = invoiceRepository.findByIdAndClinicId(request.invoiceId, clinicId)
            ?: throw NotFoundException("Invoice not found")

        if (request.amount > invoice.balance) {
            throw BadRequestException("Amount exceeds outstanding balance")
        }

        return mpesaService.initiateSTKPush(
            phone = request.phone,
            amount = request.amount.toLong(),
            invoiceId = invoice.id,
            accountRef = invoice.invoiceNumber
        )
    }

    // Called by M-Pesa callback (no auth, verified by transaction code)
    fun handleMpesaCallback(resultCode: Int, resultDesc: String, invoiceId: UUID,
                            amount: BigDecimal?, txRef: String?, phone: String?) {
        if (resultCode != 0) {
            log.warn("M-Pesa payment failed for invoice {}: {} - {}", invoiceId, resultCode, resultDesc)
            return
        }

        val invoice = invoiceRepository.findById(invoiceId).orElse(null) ?: run {
            log.error("M-Pesa callback: invoice {} not found", invoiceId)
            return
        }

        val paidAmount = amount ?: return
        val payment = paymentRepository.save(
            Payment(
                clinicId = invoice.clinicId,
                invoiceId = invoice.id,
                patientId = invoice.patientId,
                amount = paidAmount,
                method = PaymentMethod.MPESA,
                reference = txRef,
                mpesaPhone = phone
            )
        )

        invoice.paidAmount = invoice.paidAmount.add(paidAmount)
        invoice.status = if (invoice.paidAmount >= invoice.total) InvoiceStatus.PAID else InvoiceStatus.PARTIAL
        invoice.updatedAt = Instant.now()
        invoiceRepository.save(invoice)

        log.info("M-Pesa payment recorded: KES {} for invoice {} ({})", paidAmount, invoice.invoiceNumber, txRef)
    }

    // -----------------------------------------------
    // Revenue reports
    // -----------------------------------------------

    @Transactional(readOnly = true)
    fun getMonthlyRevenue(clinicId: UUID, year: Int, month: Int): RevenueReport {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1)
        val from = startDate.atStartOfDay(ZoneId.of("Africa/Nairobi")).toInstant()
        val to = endDate.atStartOfDay(ZoneId.of("Africa/Nairobi")).toInstant()

        val totalInvoiced = invoiceRepository.sumRevenue(clinicId, from, to)
        val totalCollected = invoiceRepository.sumCollected(clinicId, from, to)
        val paid = invoiceRepository.countByStatus(clinicId, InvoiceStatus.PAID)

        val byMethod = PaymentMethod.values().associateWith { method ->
            paymentRepository.sumByMethod(clinicId, method, from, to)
        }.filter { it.value > BigDecimal.ZERO }
            .map { (k, v) -> k.name to v }.toMap()

        return RevenueReport(
            period = "$year-${month.toString().padStart(2, '0')}",
            totalInvoiced = totalInvoiced,
            totalCollected = totalCollected,
            outstandingBalance = totalInvoiced.subtract(totalCollected),
            invoiceCount = invoiceRepository.countByStatus(clinicId, InvoiceStatus.ISSUED).toInt() +
                paid.toInt() + invoiceRepository.countByStatus(clinicId, InvoiceStatus.PARTIAL).toInt(),
            paidCount = paid.toInt(),
            byMethod = byMethod,
            topPatients = emptyList()  // extend with JDBC query if needed
        )
    }

    // -----------------------------------------------
    // Helpers
    // -----------------------------------------------

    private fun generateInvoiceNumber(clinicId: UUID): String {
        val year = LocalDate.now().year
        val prefix = "INV-$year-"
        val maxSeq = invoiceRepository.findMaxSequence(clinicId, prefix) ?: 0
        return "$prefix${(maxSeq + 1).toString().padStart(4, '0')}"
    }

    private fun Invoice.toResponse(patientName: String, payments: List<Payment>) = InvoiceResponse(
        id = id, invoiceNumber = invoiceNumber, patientId = patientId,
        patientName = patientName, appointmentId = appointmentId,
        lineItems = lineItems, subtotal = subtotal, discount = discount,
        tax = tax, total = total, paidAmount = paidAmount, balance = balance,
        status = status, notes = notes, dueDate = dueDate, issuedAt = issuedAt,
        payments = payments.map { p ->
            PaymentResponse(id = p.id, amount = p.amount, method = p.method,
                reference = p.reference, paidAt = p.paidAt)
        }
    )
}


// =============================================
// service/MpesaService.kt
// =============================================
package com.cliniq.domain.billing.service

import com.cliniq.domain.billing.dto.MpesaStkPushResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

@Service
class MpesaService(
    @Value("\${mpesa.consumer-key:}") private val consumerKey: String,
    @Value("\${mpesa.consumer-secret:}") private val consumerSecret: String,
    @Value("\${mpesa.shortcode:174379}") private val shortcode: String,
    @Value("\${mpesa.passkey:}") private val passkey: String,
    @Value("\${mpesa.callback-url:https://example.com/api/v1/billing/mpesa/callback}") private val callbackUrl: String,
    @Value("\${mpesa.sandbox:true}") private val sandbox: Boolean,
    private val restTemplate: RestTemplate
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val baseUrl get() = if (sandbox)
        "https://sandbox.safaricom.co.ke"
    else
        "https://api.safaricom.co.ke"

    fun initiateSTKPush(phone: String, amount: Long, invoiceId: UUID, accountRef: String): MpesaStkPushResponse {
        if (consumerKey.isBlank()) {
            log.warn("M-Pesa not configured — returning mock response")
            return MpesaStkPushResponse(true, "mock_${System.currentTimeMillis()}", "M-Pesa payment prompt sent (mock)")
        }

        return try {
            val token = getAccessToken()
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            val password = Base64.getEncoder().encodeToString("$shortcode$passkey$timestamp".toByteArray())
            val normalizedPhone = normalizePhone(phone)

            val body = mapOf(
                "BusinessShortCode" to shortcode,
                "Password" to password,
                "Timestamp" to timestamp,
                "TransactionType" to "CustomerPayBillOnline",
                "Amount" to amount,
                "PartyA" to normalizedPhone,
                "PartyB" to shortcode,
                "PhoneNumber" to normalizedPhone,
                "CallBackURL" to "$callbackUrl?invoiceId=$invoiceId",
                "AccountReference" to accountRef,
                "TransactionDesc" to "ClinIQ Payment"
            )

            val headers = HttpHeaders().apply {
                setBearerAuth(token)
                contentType = MediaType.APPLICATION_JSON
            }

            val response = restTemplate.postForObject(
                "$baseUrl/mpesa/stkpush/v1/processrequest",
                HttpEntity(body, headers),
                Map::class.java
            )

            val checkoutId = response?.get("CheckoutRequestID") as? String
            MpesaStkPushResponse(true, checkoutId, "Payment prompt sent to $normalizedPhone")
        } catch (e: Exception) {
            log.error("M-Pesa STK push failed", e)
            MpesaStkPushResponse(false, null, "Payment initiation failed: ${e.message}")
        }
    }

    private fun getAccessToken(): String {
        val credentials = Base64.getEncoder()
            .encodeToString("$consumerKey:$consumerSecret".toByteArray())

        val headers = HttpHeaders().apply {
            set("Authorization", "Basic $credentials")
        }

        val response = restTemplate.exchange(
            "$baseUrl/oauth/v1/generate?grant_type=client_credentials",
            org.springframework.http.HttpMethod.GET,
            HttpEntity<Nothing>(headers),
            Map::class.java
        )
        return response.body?.get("access_token") as? String
            ?: throw RuntimeException("Failed to get M-Pesa access token")
    }

    private fun normalizePhone(phone: String): String {
        val digits = phone.replace(Regex("\\D"), "")
        return when {
            digits.startsWith("254") -> digits
            digits.startsWith("07") -> "254${digits.substring(1)}"
            digits.startsWith("01") -> "254${digits.substring(1)}"
            digits.startsWith("7") || digits.startsWith("1") -> "254$digits"
            else -> digits
        }
    }
}


// =============================================
// controller/BillingController.kt
// =============================================
package com.cliniq.domain.billing.controller

import com.cliniq.domain.billing.dto.*
import com.cliniq.domain.billing.service.BillingService
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
@RequestMapping("/api/v1/billing")
class BillingController(private val billingService: BillingService) {

    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','RECEPTIONIST','FINANCE','SUPER_ADMIN')")
    fun createInvoice(
        @Valid @RequestBody request: CreateInvoiceRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.status(201).body(
        ApiResponse.success(billingService.createInvoice(auth.clinicId!!, request, auth.userId))
    )

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','RECEPTIONIST','FINANCE','SUPER_ADMIN')")
    fun getInvoice(
        @PathVariable id: UUID,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.ok(ApiResponse.success(
        billingService.getInvoice(auth.clinicId!!, id)
    ))

    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','RECEPTIONIST','FINANCE','SUPER_ADMIN')")
    fun recordPayment(
        @Valid @RequestBody request: RecordPaymentRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.status(201).body(
        ApiResponse.success(billingService.recordPayment(auth.clinicId!!, request, auth.userId))
    )

    @PostMapping("/mpesa/initiate")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','RECEPTIONIST','FINANCE','SUPER_ADMIN')")
    fun initiateMpesa(
        @Valid @RequestBody request: InitiateMpesaRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.ok(ApiResponse.success(billingService.initiateMpesaPayment(auth.clinicId!!, request)))

    // M-Pesa calls this — no auth, but verify by matching invoice ID
    @PostMapping("/mpesa/callback")
    fun mpesaCallback(
        @RequestBody body: Map<String, Any>,
        @RequestParam invoiceId: UUID
    ): ResponseEntity<Map<String, String>> {
        val stkCallback = (body["Body"] as? Map<*, *>)?.get("stkCallback") as? Map<*, *>
        val resultCode = (stkCallback?.get("ResultCode") as? Int) ?: 1
        val resultDesc = (stkCallback?.get("ResultDesc") as? String) ?: "Unknown"
        val metadata = (stkCallback?.get("CallbackMetadata") as? Map<*, *>)?.get("Item") as? List<*>

        val amount = metadata?.filterIsInstance<Map<*, *>>()
            ?.find { it["Name"] == "Amount" }?.get("Value")
            ?.let { java.math.BigDecimal(it.toString()) }

        val txRef = metadata?.filterIsInstance<Map<*, *>>()
            ?.find { it["Name"] == "MpesaReceiptNumber" }?.get("Value") as? String

        val phone = metadata?.filterIsInstance<Map<*, *>>()
            ?.find { it["Name"] == "PhoneNumber" }?.get("Value") as? String

        billingService.handleMpesaCallback(resultCode, resultDesc, invoiceId, amount, txRef, phone)
        return ResponseEntity.ok(mapOf("ResultCode" to "0", "ResultDesc" to "Success"))
    }

    @GetMapping("/reports/revenue")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','FINANCE','SUPER_ADMIN')")
    fun revenueReport(
        @RequestParam(defaultValue = "0") year: Int,
        @RequestParam(defaultValue = "0") month: Int,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<RevenueReport>> {
        val now = java.time.LocalDate.now()
        val y = if (year == 0) now.year else year
        val m = if (month == 0) now.monthValue else month
        return ResponseEntity.ok(ApiResponse.success(billingService.getMonthlyRevenue(auth.clinicId!!, y, m)))
    }
}
