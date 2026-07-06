package com.cliniq.domain.billing.controller

import com.cliniq.domain.billing.dto.*
import com.cliniq.domain.billing.service.BillingService
import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.shared.response.ApiResponse
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
