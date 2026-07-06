package com.cliniq.domain.lab.controller

import com.cliniq.domain.lab.dto.*
import com.cliniq.domain.lab.entity.LabRequestStatus
import com.cliniq.domain.lab.service.LabService
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
@RequestMapping("/api/v1/lab")
class LabController(private val labService: LabService) {

    @PostMapping("/tests")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','SUPER_ADMIN')")
    fun addTest(@Valid @RequestBody request: CreateLabTestRequest, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.status(201).body(ApiResponse.success(labService.addTest(auth.clinicId!!, request)))

    @GetMapping("/tests")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','DOCTOR','NURSE','LAB_TECH','SUPER_ADMIN')")
    fun listTests(
        @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "50") size: Int,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<List<Any>>> {
        val result = labService.getTestCatalog(auth.clinicId!!, PageRequest.of(page, size))
        return ResponseEntity.ok(ApiResponse.success(result.content, PageMeta(page, size, result.totalElements, result.totalPages)))
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun createRequest(@Valid @RequestBody request: CreateLabRequestRequest, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.status(201).body(ApiResponse.success(labService.createRequest(auth.clinicId!!, request, auth.userId)))

    @GetMapping("/requests/pending")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','LAB_TECH','SUPER_ADMIN')")
    fun pendingRequests(
        @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<List<LabRequestResponse>>> {
        val result = labService.getPendingRequests(auth.clinicId!!, PageRequest.of(page, size))
        return ResponseEntity.ok(ApiResponse.success(result.content, PageMeta(page, size, result.totalElements, result.totalPages)))
    }

    @GetMapping("/requests/patient/{patientId}")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN','LAB_TECH','SUPER_ADMIN')")
    fun patientRequests(
        @PathVariable patientId: UUID, @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int, @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<List<LabRequestResponse>>> {
        val result = labService.getRequestsByPatient(auth.clinicId!!, patientId, PageRequest.of(page, size))
        return ResponseEntity.ok(ApiResponse.success(result.content, PageMeta(page, size, result.totalElements, result.totalPages)))
    }

    @PatchMapping("/requests/{id}/status")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','LAB_TECH','SUPER_ADMIN')")
    fun updateStatus(@PathVariable id: UUID, @RequestParam status: LabRequestStatus, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(labService.updateStatus(auth.clinicId!!, id, status, auth.userId)))

    @PostMapping("/results")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','LAB_TECH','SUPER_ADMIN')")
    fun enterResults(@Valid @RequestBody request: EnterResultRequest, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(labService.enterResults(auth.clinicId!!, request, auth.userId)))
}
