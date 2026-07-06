package com.cliniq.domain.crm.dto

import com.cliniq.domain.communication.entity.CommChannel
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateCampaignRequest(
    @field:NotBlank val name: String, val description: String? = null,
    @field:NotNull val channel: CommChannel, @field:NotBlank val messageTemplate: String,
    val targetSegment: CampaignTarget = CampaignTarget(), val scheduledAt: Instant? = null
)
data class CampaignTarget(
    val segment: String? = null, val minDaysInactive: Int? = null,
    val tags: List<String> = emptyList(), val maxPatients: Int = 500
)
data class CampaignResponse(
    val id: UUID, val name: String, val channel: CommChannel,
    val messageTemplate: String, val status: String, val targetSegment: Map<String, Any>,
    val scheduledAt: Instant?, val sentCount: Int, val deliveredCount: Int,
    val failedCount: Int, val createdAt: Instant
)
data class FollowUpRequest(
    @field:NotNull val patientId: UUID, @field:NotBlank val message: String,
    val channel: CommChannel = CommChannel.SMS, val scheduledAt: Instant? = null
)
