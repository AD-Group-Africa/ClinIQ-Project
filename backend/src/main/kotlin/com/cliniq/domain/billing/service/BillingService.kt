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
        val tax = BigDecimal.ZERO
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

    @Transactional(readOnly = true)
    fun getInvoice(clinicId: UUID, invoiceId: UUID): InvoiceResponse {
        val invoice = invoiceRepository.findByIdAndClinicId(invoiceId, clinicId)
            ?: throw NotFoundException("Invoice not found")
        val patient = patientRepository.findById(invoice.patientId).orElse(null)
        val payments = paymentRepository.findByInvoiceId(invoiceId)
        return invoice.toResponse(patient?.fullName ?: "Unknown", payments)
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
            topPatients = emptyList()
        )
    }

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
