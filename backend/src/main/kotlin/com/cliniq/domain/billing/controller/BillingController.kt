package com.cliniq.domain.billing.controller

import com.cliniq.domain.billing.dto.*
import com.cliniq.domain.billing.service.BillingService
import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.shared.response.ApiResponse
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.ParameterObject
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/billing")
@SecurityScheme(
    name = "Bearer authentication",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
class BillingController(private val billingService: BillingService) {

    @Operation(
        summary = "Create a new invoice",
        description = "Creates a new invoice for a patient's appointment with line items",
        security = arrayOf(@SecurityScheme(name = "Bearer authentication"))
    )
    @ParameterObject
    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','RECEPTIONIST','FINANCE','SUPER_ADMIN')")
    fun createInvoice(
        @Operation(hidden = true)
        @Parameter(hidden = true)
        @Valid @RequestBody request: CreateInvoiceRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.status(201).body(
        ApiResponse.success(billingService.createInvoice(auth.clinicId!!, request, auth.userId))
    )

    @Operation(
        summary = "Get invoice by ID",
        description = "Retrieves a specific invoice by its unique identifier",
        security = arrayOf(@SecurityScheme(name = "Bearer authentication"))
    )
    @ParameterObject
    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','RECEPTIONIST','FINANCE','SUPER_ADMIN')")
    fun getInvoice(
        @Operation(summary = "Invoice ID", description = "The UUID of the invoice to retrieve")
        @Parameter(name = "id", description = "The UUID of the invoice to retrieve", required = true, example = "11111111-1111-1111-1111-111111111111")
        @PathVariable id: UUID,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.ok(ApiResponse.success(
        billingService.getInvoice(auth.clinicId!!, id)
    ))

    @Operation(
        summary = "Record a payment",
        description = "Records a payment received against an invoice",
        security = arrayOf(@SecurityScheme(name = "Bearer authentication"))
    )
    @ParameterObject
    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','RECEPTIONIST','FINANCE','SUPER_ADMIN')")
    fun recordPayment(
        @Operation(hidden = true)
        @Parameter(hidden = true)
        @Valid @RequestBody request: RecordPaymentRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.status(201).body(
        ApiResponse.success(billingService.recordPayment(auth.clinicId!!, request, auth.userId))
    )

    @Operation(
        summary = "Initiate M-Pesa payment",
        description = "Initiates an M-Pesa STK push payment for an invoice",
        security = arrayOf(@SecurityScheme(name = "Bearer authentication"))
    )
    @ParameterObject
    @PostMapping("/mpesa/initiate")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','RECEPTIONIST','FINANCE','SUPER_ADMIN')")
    fun initiateMpesa(
        @Operation(hidden = true)
        @Parameter(hidden = true)
        @Valid @RequestBody request: InitiateMpesaRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.ok(ApiResponse.success(billingService.initiateMpesaPayment(auth.clinicId!!, request)))

    @Operation(
        summary = "Handle M-Pesa callback",
        description = "Handles the M-Pesa STK push callback result",
        security = arrayOf(@SecurityScheme(name = "Bearer authentication"))
    )
    @ParameterObject
    @PostMapping("/mpesa/callback")
    fun mpesaCallback(
        @Operation(summary = "M-Pesa callback body", description = "The POST body from M-Pesa containing STK push results")
        @Parameter(name = "body", description = "M-Pesa callback JSON body", required = true)
        @RequestBody body: Map<String, Any>,
        @Operation(summary = "Invoice ID", description = "The invoice ID to update with callback results")
        @Parameter(name = "invoiceId", description = "The invoice ID to update", required = true, example = "11111111-1111-1111-1111-111111111111")
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

    @Operation(
        summary = "List invoices with pagination",
        description = "Retrieves a paginated list of invoices for the authenticated clinic",
        security = arrayOf(@SecurityScheme(name = "Bearer authentication"))
    )
    @ParameterObject
    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','RECEPTIONIST','FINANCE','SUPER_ADMIN')")
    fun listInvoices(
        @Operation(hidden = true)
        @Parameter(hidden = true)
        @AuthenticationPrincipal auth: JwtAuthenticationToken,
        @ParameterObject
        pageable: Pageable
    ) = ResponseEntity.ok(ApiResponse.success(
        billingService.listInvoices(auth.clinicId!!, pageable)
    ))

    @Operation(
        summary = "Get monthly revenue report",
        description = "Retrieves a revenue report for the specified year/month",
        security = arrayOf(@SecurityScheme(name = "Bearer authentication"))
    )
    @ParameterObject
    @GetMapping("/reports/revenue")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','FINANCE','SUPER_ADMIN')")
    fun revenueReport(
        @Operation(summary = "Year", description = "The year for the report (defaults to current year)")
        @Parameter(name = "year", description = "The year for the report", required = false, defaultValue = "0", example = "2024")
        @RequestParam(defaultValue = "0") year: Int,

        @Operation(summary = "Month", description = "The month for the report (1-12, defaults to current month)")
        @Parameter(name = "month", description = "The month for the report (1-12, defaults to current month)", required = false, defaultValue = "0", example = "1")
        @RequestParam(defaultValue = "0") month: Int,

        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<RevenueReport>> {
        val now = java.time.LocalDate.now()
        val y = if (year == 0) now.year else year
        val m = if (month == 0) now.monthValue else month
        return ResponseEntity.ok(ApiResponse.success(billingService.getMonthlyRevenue(auth.clinicId!!, y, m)))
    }
}