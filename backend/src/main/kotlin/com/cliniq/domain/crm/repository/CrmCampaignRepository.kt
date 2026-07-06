package com.cliniq.domain.crm.repository

import com.cliniq.domain.crm.entity.CrmCampaign
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface CrmCampaignRepository : JpaRepository<CrmCampaign, UUID> {
    fun findByClinicIdOrderByCreatedAtDesc(clinicId: UUID): List<CrmCampaign>
    fun findByStatusAndScheduledAtBefore(status: String, now: Instant): List<CrmCampaign>
}
