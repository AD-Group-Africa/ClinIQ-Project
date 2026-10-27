
package com.cliniq.domain.clinic.controller

import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.domain.clinic.service.DoctorService
import com.cliniq.shared.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/doctors")
class DoctorController(
    private val service: DoctorService
) {
    @GetMapping
    @PreAuthorize(
        "hasAnyRole('RECEPTIONIST','NURSE','DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')"
    )
    fun getAvailableDoctors(
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<List<com.cliniq.domain.clinic.dto.DoctorListResponse>>> {
        val doctors = service.getAvailableDoctors(auth.clinicId!!)
        return ResponseEntity.ok(ApiResponse.success(doctors))
    }
}
