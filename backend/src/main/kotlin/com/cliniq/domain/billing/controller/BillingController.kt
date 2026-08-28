package com.cliniq.domain.billing.controller

import com.cliniq.domain.billing.dto.*
import com.cliniq.domain.billing.entity.InvoiceStatus
import com.cliniq.domain.billing.service.BillingService
import com.cliniq.domain.billing.service.MpesaService
import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.shared.response.ApiResponse
import com.cliniq.shared.exceptions.BadRequestException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Billing", description = "Invoices, payments and revenue reporting")
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    `in` = SecuritySchemeIn.HEADER
)
class BillingController(
    private val billingService: BillingService,
    private val mpesaService: MpesaService
) {

    @Operation(
        summary = "Create a new invoice",
        description = "Creates a new invoice for a patient with line items",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','RECEPTIONIST','FINANCE','SUPER_ADMIN')")
    fun createInvoice(
        @Valid @RequestBody request: CreateInvoiceRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.status(201).body(
        ApiResponse.success(billingService.createInvoice(auth.clinicId!!, request, auth.userId))
    )

    @Operation(
        summary = "Get invoice by ID",
        description = "Retrieves a specific invoice by its unique identifier",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','RECEPTIONIST','FINANCE','SUPER_ADMIN')")
    fun getInvoice(
        @Parameter(description = "The UUID of the invoice to retrieve") @PathVariable id: UUID,
        @Parameter(hidden = true) @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.ok(ApiResponse.success(
        billingService.getInvoice(auth.clinicId!!, id)
    ))

    @Operation(
        summary = "Record a payment",
        description = "Records a payment received against an invoice (cash, card, bank transfer, insurance). " +
            "Server-side only: the frontend can never mark a payment as successful on its own.",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','RECEPTIONIST','FINANCE','SUPER_ADMIN')")
    fun recordPayment(
        @Valid @RequestBody request: RecordPaymentRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.status(201).body(
        ApiResponse.success(billingService.recordPayment(auth.clinicId!!, request, auth.userId))
    )

    @Operation(
        summary = "Initiate M-Pesa payment",
        description = "Initiates an M-Pesa STK push payment for an invoice",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @PostMapping("/mpesa/initiate")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','RECEPTIONIST','FINANCE','SUPER_ADMIN')")
    fun initiateMpesa(
        @Valid @RequestBody request: InitiateMpesaRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.ok(ApiResponse.success(billingService.initiateMpesaPayment(auth.clinicId!!, request)))

    @Operation(
        summary = "Handle M-Pesa callback",
        description = "Webhook called by M-Pesa with the STK push result. Validates HMAC token when " +
            "MPESA_CALLBACK_SECRET is configured. Malformed callbacks are rejected; duplicate receipts are idempotent.",
        security = []
    )
    @PostMapping("/mpesa/callback")
    fun mpesaCallback(
        @RequestBody body: Map<String, Any>,
        @Parameter(description = "The invoice ID to update with callback results") @RequestParam invoiceId: UUID,
        @Parameter(description = "HMAC callback token") @RequestParam(required = false) token: String?
    ): ResponseEntity<Map<String, String>> {
        if (!mpesaService.isValidCallbackToken(invoiceId, token)) {
            return ResponseEntity.status(401).body(mapOf("ResultCode" to "1", "ResultDesc" to "Invalid callback token"))
        }
        // Validate body shape
        if (!body.containsKey("Body")) {
            return ResponseEntity.badRequest().body(mapOf("ResultCode" to "1", "ResultDesc" to "Malformed body"))
        }
        val stkCallback = (body["Body"] as? Map<*, *>)?.get("stkCallback") as? Map<*, *>
            ?: return ResponseEntity.badRequest().body(mapOf("ResultCode" to "1", "ResultDesc" to "Malformed stkCallback"))
        val resultCode = when (val rc = stkCallback["ResultCode"]) {
            is Int -> rc
            is Number -> rc.toInt()
            is String -> rc.toIntOrNull() ?: 1
            else -> 1
        }
        val resultDesc = stkCallback["ResultDesc"] as? String ?: "Unknown"
        val metadata = (stkCallback["CallbackMetadata"] as? Map<*, *>)?.get("Item") as? List<*>

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

    @Operation(
        summary = "List invoices with pagination",
        description = "Retrieves a paginated list of invoices for the authenticated clinic. " +
            "Supports `?page=0&size=20&sort=issuedAt,desc` and optional `?status=ISSUED|PAID|PARTIAL|OVERDUE|CANCELLED`.",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','RECEPTIONIST','FINANCE','SUPER_ADMIN')")
    fun listInvoices(
        pageable: Pageable,
        @Parameter(description = "Optional status filter", example = "ISSUED")
        @RequestParam(required = false) status: String?,
        @Parameter(hidden = true) @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<Any>> {
        val page = if (status.isNullOrBlank()) {
            billingService.listInvoices(auth.clinicId!!, pageable)
        } else {
            val parsed = try {
                InvoiceStatus.valueOf(status.uppercase())
            } catch (e: IllegalArgumentException) {
                throw BadRequestException("Unknown status: $status")
            }
            billingService.listInvoicesByStatus(auth.clinicId!!, parsed, pageable)
        }
        return ResponseEntity.ok(ApiResponse.success(page))
    }

    @Operation(
        summary = "Get monthly revenue report",
        description = "Retrieves a revenue report for the specified year/month",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @GetMapping("/reports/revenue")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','FINANCE','SUPER_ADMIN')")
    fun revenueReport(
        @Parameter(description = "The year for the report (defaults to current year)", example = "2026")
        @RequestParam(defaultValue = "0") year: Int,

        @Parameter(description = "The month for the report (1-12, defaults to current month)", example = "8")
        @RequestParam(defaultValue = "0") month: Int,

        @Parameter(hidden = true) @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<RevenueReport>> {
        val now = java.time.LocalDate.now()
        val y = if (year == 0) now.year else year
        val m = if (month == 0) now.monthValue else month
        return ResponseEntity.ok(ApiResponse.success(billingService.getMonthlyRevenue(auth.clinicId!!, y, m)))
    }
}