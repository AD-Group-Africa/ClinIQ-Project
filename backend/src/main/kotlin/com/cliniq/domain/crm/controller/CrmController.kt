package com.cliniq.domain.crm.controller

import com.cliniq.domain.crm.dto.CreateCampaignRequest
import com.cliniq.domain.crm.service.CrmService
import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.shared.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/crm")
@PreAuthorize("hasAnyRole('CLINIC_ADMIN','SUPER_ADMIN')")
class CrmController(private val crmService: CrmService) {

    @PostMapping("/campaigns")
    fun createCampaign(@Valid @RequestBody request: CreateCampaignRequest, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.status(201).body(ApiResponse.success(crmService.createCampaign(auth.clinicId!!, request, auth.userId)))

    @GetMapping("/campaigns")
    fun listCampaigns(@AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(crmService.listCampaigns(auth.clinicId!!)))

    @PostMapping("/campaigns/{id}/send")
    fun sendNow(@PathVariable id: UUID, @AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(crmService.sendCampaignNow(auth.clinicId!!, id)))
}
