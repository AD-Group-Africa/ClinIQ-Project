package com.cliniq.domain.appointment.controller

import com.cliniq.domain.appointment.dto.*
import com.cliniq.domain.appointment.service.AppointmentService
import com.cliniq.domain.appointment.service.QueueService
import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.shared.response.ApiResponse
import com.cliniq.shared.response.PageMeta
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/appointments")
class AppointmentController(
    private val service: AppointmentService,
    private val queueService: QueueService
) {
    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','CLINIC_ADMIN','DOCTOR','SUPER_ADMIN')")
    fun create(@Valid @RequestBody req: CreateAppointmentRequest, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.status(201).body(ApiResponse.success(service.createAppointment(auth.clinicId!!, req, auth.userId)))

    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun todayQueue(@AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(service.getTodayQueue(auth.clinicId!!)))

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun liveQueue(@AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(queueService.getLiveQueue(auth.clinicId!!)))

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun patientHistory(@PathVariable patientId: UUID, @RequestParam(defaultValue="0") page: Int,
                       @RequestParam(defaultValue="10") size: Int, @AuthenticationPrincipal auth: JwtAuthenticationToken): ResponseEntity<ApiResponse<List<AppointmentResponse>>> {
        val result = service.getPatientAppointments(auth.clinicId!!, patientId, PageRequest.of(page, size))
        return ResponseEntity.ok(ApiResponse.success(result.content, PageMeta(page, size, result.totalElements, result.totalPages)))
    }

    @PatchMapping("/{id}/check-in")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','CLINIC_ADMIN','SUPER_ADMIN')")
    fun checkIn(@PathVariable id: UUID, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(service.checkIn(auth.clinicId!!, id, auth.userId)))

    @PatchMapping("/{id}/start-consultation")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun startConsultation(@PathVariable id: UUID, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(service.startConsultation(auth.clinicId!!, id, auth.userId)))

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun complete(@PathVariable id: UUID, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(service.complete(auth.clinicId!!, id, auth.userId)))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','CLINIC_ADMIN','SUPER_ADMIN')")
    fun cancel(@PathVariable id: UUID, @RequestBody body: CancelRequest, @AuthenticationPrincipal auth: JwtAuthenticationToken): ResponseEntity<ApiResponse<Unit>> {
        service.cancel(auth.clinicId!!, id, body, auth.userId); return ResponseEntity.ok(ApiResponse.success(Unit))
    }

    @GetMapping("/schedule/slots")
    fun slots(@RequestParam doctorId: UUID, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
              @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(service.getAvailableSlots(auth.clinicId!!, doctorId, date).map { mapOf("startTime" to it.startTime.toString(), "endTime" to it.endTime.toString()) }))
}
