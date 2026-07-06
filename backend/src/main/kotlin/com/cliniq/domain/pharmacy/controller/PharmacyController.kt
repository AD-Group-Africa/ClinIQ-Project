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

    @PostMapping("/drugs")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','SUPER_ADMIN')")
    fun addDrug(
        @Valid @RequestBody request: CreateDrugRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.status(201).body(ApiResponse.success(pharmacyService.addDrug(auth.clinicId!!, request, auth.userId)))

    @GetMapping("/drugs")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','DOCTOR','NURSE','RECEPTIONIST','SUPER_ADMIN')")
    fun listDrugs(
        @RequestParam(defaultValue = "") q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<List<DrugStockResponse>>> {
        val result = pharmacyService.searchDrugs(auth.clinicId!!, q, PageRequest.of(page, size))
        return ResponseEntity.ok(ApiResponse.success(result.content, PageMeta(page, size, result.totalElements, result.totalPages)))
    }

    @GetMapping("/drugs/{id}/stock")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','DOCTOR','NURSE','SUPER_ADMIN')")
    fun drugStock(@PathVariable id: UUID, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(pharmacyService.getStockSummary(auth.clinicId!!, id)))

    @PostMapping("/stock/add")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','SUPER_ADMIN')")
    fun addStock(@Valid @RequestBody request: AddStockRequest, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.status(201).body(ApiResponse.success(pharmacyService.addStock(auth.clinicId!!, request, auth.userId)))

    @PostMapping("/dispense")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','NURSE','DOCTOR','SUPER_ADMIN')")
    fun dispense(@Valid @RequestBody request: DispenseRequest, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.status(201).body(ApiResponse.success(pharmacyService.dispense(auth.clinicId!!, request, auth.userId)))

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','SUPER_ADMIN')")
    fun alerts(@AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(pharmacyService.getStockAlerts(auth.clinicId!!)))
}
