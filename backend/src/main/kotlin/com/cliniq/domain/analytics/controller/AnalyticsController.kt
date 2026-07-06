package com.cliniq.domain.analytics.controller

import com.cliniq.domain.analytics.service.AnalyticsService
import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.shared.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAnyRole('CLINIC_ADMIN','DOCTOR','FINANCE','SUPER_ADMIN')")
class AnalyticsController(private val analyticsService: AnalyticsService) {

    @GetMapping("/dashboard")
    fun dashboard(@AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(analyticsService.getDashboardSummary(auth.clinicId!!)))

    @GetMapping("/trends/weekly")
    fun weeklyTrends(@RequestParam(defaultValue = "12") weeks: Int, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(analyticsService.getWeeklyTrends(auth.clinicId!!, weeks)))

    @GetMapping("/doctors/utilization")
    fun doctorUtilization(@AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(analyticsService.getDoctorUtilization(auth.clinicId!!)))

    @GetMapping("/patients/segments")
    fun patientSegments(@AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(analyticsService.getPatientSegments(auth.clinicId!!)))

    @GetMapping("/patients/at-risk")
    fun atRisk(@RequestParam(defaultValue = "50") limit: Int, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(analyticsService.getAtRiskPatients(auth.clinicId!!, limit)))

    @GetMapping("/no-shows/by-day")
    fun noShowByDay(@AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(analyticsService.getNoShowByDay(auth.clinicId!!)))
}
