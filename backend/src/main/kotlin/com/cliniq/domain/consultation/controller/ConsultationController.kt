package com.cliniq.domain.consultation.controller

import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.domain.consultation.dto.*
import com.cliniq.domain.consultation.service.ConsultationService
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
@RequestMapping("/api/v1/consultations")
class ConsultationController(private val service: ConsultationService) {

    @PostMapping("/start")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun start(@Valid @RequestBody req: StartConsultationRequest, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.status(201).body(ApiResponse.success(service.start(auth.clinicId!!, req, auth.userId)))

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun update(@PathVariable id: UUID, @RequestBody req: UpdateConsultationRequest, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(service.update(auth.clinicId!!, id, req, auth.userId)))

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun complete(@PathVariable id: UUID, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(service.complete(auth.clinicId!!, id, auth.userId)))

    @PostMapping("/prescriptions")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun addRx(@Valid @RequestBody req: AddPrescriptionRequest, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.status(201).body(ApiResponse.success(service.addPrescription(auth.clinicId!!, req, auth.userId)))

    @GetMapping("/patient/{patientId}/timeline")
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE','CLINIC_ADMIN','SUPER_ADMIN')")
    fun timeline(@PathVariable patientId: UUID, @RequestParam(defaultValue="0") page: Int, @RequestParam(defaultValue="10") size: Int, @AuthenticationPrincipal auth: JwtAuthenticationToken): ResponseEntity<ApiResponse<List<ConsultationResponse>>> {
        val r = service.getTimeline(auth.clinicId!!, patientId, PageRequest.of(page, size))
        return ResponseEntity.ok(ApiResponse.success(r.content, PageMeta(page, size, r.totalElements, r.totalPages)))
    }
}
