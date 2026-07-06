package com.cliniq.domain.crm.service

import com.cliniq.domain.analytics.service.AnalyticsService
import com.cliniq.domain.communication.entity.CommChannel
import com.cliniq.domain.communication.service.NotificationService
import com.cliniq.domain.crm.dto.*
import com.cliniq.domain.crm.entity.CrmCampaign
import com.cliniq.domain.crm.repository.CrmCampaignRepository
import com.cliniq.domain.communication.service.AfricasTalkingService
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class CrmService(
    private val campaignRepository: CrmCampaignRepository,
    private val analyticsService: AnalyticsService,
    private val smsService: AfricasTalkingService,
    private val jdbc: NamedParameterJdbcTemplate
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun createCampaign(clinicId: UUID, request: CreateCampaignRequest, createdBy: UUID): CampaignResponse {
        val campaign = campaignRepository.save(CrmCampaign(
            clinicId = clinicId, name = request.name, description = request.description,
            channel = request.channel, messageTemplate = request.messageTemplate,
            targetSegment = mapOf(
                "segment" to (request.targetSegment.segment ?: "ALL"),
                "minDaysInactive" to (request.targetSegment.minDaysInactive ?: 0),
                "tags" to request.targetSegment.tags, "maxPatients" to request.targetSegment.maxPatients
            ),
            scheduledAt = request.scheduledAt,
            status = if (request.scheduledAt != null) "SCHEDULED" else "DRAFT",
            createdBy = createdBy
        ))
        return campaign.toResponse()
    }

    fun sendCampaignNow(clinicId: UUID, campaignId: UUID): CampaignResponse {
        val campaign = campaignRepository.findById(campaignId).orElseThrow()
        require(campaign.clinicId == clinicId)
        campaign.status = "RUNNING"; campaignRepository.save(campaign)
        val targets = resolveTargets(clinicId, campaign)
        var sent = 0; var failed = 0
        targets.forEach { (phone, name) ->
            val msg = campaign.messageTemplate.replace("{name}", name.split(" ").first())
            try { smsService.send(phone, msg); sent++ } catch (e: Exception) { log.warn("Send failed to {}: {}", phone.takeLast(4), e.message); failed++ }
        }
        campaign.sentCount = sent; campaign.failedCount = failed; campaign.deliveredCount = sent
        campaign.status = "COMPLETED"; campaign.updatedAt = Instant.now()
        return campaignRepository.save(campaign).toResponse()
    }

    fun listCampaigns(clinicId: UUID): List<CampaignResponse> =
        campaignRepository.findByClinicIdOrderByCreatedAtDesc(clinicId).map { it.toResponse() }

    @Scheduled(cron = "0 */15 * * * *")
    fun processScheduledCampaigns() {
        val due = campaignRepository.findByStatusAndScheduledAtBefore("SCHEDULED", Instant.now())
        due.forEach { c -> try { sendCampaignNow(c.clinicId, c.id) } catch (e: Exception) { log.error("Campaign {} failed", c.id, e) } }
    }

    @Scheduled(cron = "0 0 9 * * MON")
    fun sendAtRiskOutreach() {
        val clinicIds = jdbc.queryForList("SELECT id FROM clinics WHERE is_active = TRUE", emptyMap<String, Any>(), UUID::class.java)
        clinicIds.forEach { clinicId ->
            try {
                val atRisk = analyticsService.getAtRiskPatients(clinicId, 100).filter { it.churnRisk == "AT_RISK" || it.churnRisk == "COOLING" }.take(50)
                atRisk.forEach { pt ->
                    val msg = "Hi ${pt.patientName.split(" ").first()}! We miss you at our clinic. Book your next appointment today — reply BOOK or call us."
                    try { smsService.send(pt.phone, msg) } catch (e: Exception) { log.warn("Outreach fail {}: {}", pt.phone.takeLast(4), e.message) }
                }
                log.info("Clinic {}: outreach sent to {} patients", clinicId, atRisk.size)
            } catch (e: Exception) { log.error("At-risk outreach failed for clinic {}", clinicId, e) }
        }
    }

    private fun resolveTargets(clinicId: UUID, campaign: CrmCampaign): List<Pair<String, String>> {
        val segment = campaign.targetSegment["segment"] as? String ?: "ALL"
        val maxPt = (campaign.targetSegment["maxPatients"] as? Int) ?: 500
        val sql = when (segment) {
            "ALL" -> """SELECT p.phone, p.first_name || ' ' || p.last_name AS name FROM patients p WHERE p.clinic_id = :clinicId AND p.phone IS NOT NULL LIMIT :max"""
            "AT_RISK" -> """SELECT p.phone, p.first_name || ' ' || p.last_name AS name FROM v_at_risk_patients r JOIN patients p ON p.id = r.patient_id WHERE r.clinic_id = :clinicId AND r.churn_risk IN ('AT_RISK','COOLING') LIMIT :max"""
            else -> """SELECT p.phone, p.first_name || ' ' || p.last_name AS name FROM v_patient_lifetime_value v JOIN patients p ON p.id = v.patient_id WHERE v.clinic_id = :clinicId AND v.patient_segment = :segment LIMIT :max"""
        }
        return jdbc.query(sql, MapSqlParameterSource().addValue("clinicId", clinicId).addValue("max", maxPt).addValue("segment", segment)) { rs, _ ->
            Pair(rs.getString("phone"), rs.getString("name"))
        }
    }

    private fun CrmCampaign.toResponse() = CampaignResponse(id = id, name = name, channel = channel, messageTemplate = messageTemplate, status = status, targetSegment = targetSegment, scheduledAt = scheduledAt, sentCount = sentCount, deliveredCount = deliveredCount, failedCount = failedCount, createdAt = createdAt)
}
