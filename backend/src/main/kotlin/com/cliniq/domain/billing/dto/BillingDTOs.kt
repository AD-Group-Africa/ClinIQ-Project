package com.cliniq.domain.billing.dto

import com.cliniq.domain.billing.entity.InvoiceStatus
import com.cliniq.domain.billing.entity.PaymentMethod
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Schema(description = "Invoice creation request")
data class CreateInvoiceRequest(
    @field:NotNull @Schema(description = "Patient ID", example = "11111111-1111-1111-1111-111111111111") val patientId: UUID,
    val appointmentId: UUID? = null,
    @field:NotEmpty @Schema(description = "Line items") val lineItems: List<LineItemRequest>,
    @Schema(description = "Discount amount", defaultValue = "0") val discount: BigDecimal = BigDecimal.ZERO,
    @Schema(description = "Optional notes") val notes: String? = null,
    @Schema(description = "Due date") val dueDate: LocalDate? = null
)

@Schema(description = "Line item for invoice")
data class LineItemRequest(
    @field:NotBlank @Schema(description = "Description", example = "Consultation fee") val description: String,
    @field:Min(1) @Schema(description = "Quantity", example = "2") val quantity: Int = 1,
    @field:DecimalMin("0.0") @Schema(description = "Unit price", example = "500.00") val unitPrice: BigDecimal
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
