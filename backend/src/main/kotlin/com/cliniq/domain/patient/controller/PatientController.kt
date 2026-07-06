package com.cliniq.domain.patient.controller

import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.domain.patient.dto.*
import com.cliniq.domain.patient.service.PatientService
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
@RequestMapping("/api/v1/patients")
class PatientController(private val service: PatientService) {

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','CLINIC_ADMIN','DOCTOR','SUPER_ADMIN')")
    fun create(@Valid @RequestBody req: CreatePatientRequest, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.status(201).body(ApiResponse.success(service.create(auth.clinicId!!, req, auth.userId)))

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','CLINIC_ADMIN','DOCTOR','FINANCE','SUPER_ADMIN')")
    fun search(
        @RequestParam(defaultValue = "") q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<List<PatientResponse>>> {
        val result = service.search(auth.clinicId!!, q, PageRequest.of(page, size))
        return ResponseEntity.ok(ApiResponse.success(result.content, PageMeta(page, size, result.totalElements, result.totalPages)))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','CLINIC_ADMIN','DOCTOR','FINANCE','SUPER_ADMIN')")
    fun getById(@PathVariable id: UUID, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(service.getById(auth.clinicId!!, id)))

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','CLINIC_ADMIN','DOCTOR','SUPER_ADMIN')")
    fun update(@PathVariable id: UUID, @RequestBody req: UpdatePatientRequest, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(service.update(auth.clinicId!!, id, req, auth.userId)))
}
