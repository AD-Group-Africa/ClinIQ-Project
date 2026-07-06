// =============================================================
// ClinIQ — Analytics Service + CRM Module
// Analytics: Spring Boot service over the 5 SQL views in V5
// CRM: Campaign management, follow-up automation, at-risk outreach
// =============================================================


// =============================================
// domain/analytics/dto/AnalyticsDTOs.kt
// com.cliniq.domain.analytics.dto
// =============================================
package com.cliniq.domain.analytics.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class DashboardSummary(
    // Today
    val todayTotal: Long,
    val todayCompleted: Long,
    val todayNoShows: Long,
    val todayRemaining: Long,
    val todayCheckedIn: Long,
    val noShowRatePct: Double,
    // Month revenue
    val monthRevenue: BigDecimal,
    val monthCollected: BigDecimal,
    val outstandingBalance: BigDecimal,
    // Patients
    val newPatientsThisMonth: Long,
    val returningPatientsThisMonth: Long,
    val retentionRatePct: Double,
    // Inventory
    val lowStockAlerts: Int,
    val pendingLabRequests: Int
)

data class WeeklyTrendPoint(
    val weekStart: LocalDate,
    val total: Long,
    val completed: Long,
    val noShows: Long,
    val completionRatePct: Double
)

data class DoctorUtilizationRow(
    val doctorId: UUID,
    val doctorName: String,
    val specialization: String?,
    val consultationsDone: Long,
    val noShows: Long,
    val completionRatePct: Double,
    val revenueGenerated: BigDecimal
)

data class PatientSegmentCount(
    val segment: String,        // VIP | LOYAL | RETURNING | NEW
    val count: Long,
    val totalSpend: BigDecimal,
    val avgSpend: BigDecimal
)

data class AtRiskPatient(
    val patientId: UUID,
    val patientName: String,
    val phone: String,
    val email: String?,
    val lastVisit: LocalDate,
    val daysInactive: Int,
    val totalVisits: Long,
    val churnRisk: String       // COOLING | AT_RISK | LOST
)

data class NoShowByDay(
    val dayOfWeek: Int,
    val dayName: String,
    val total: Long,
    val noShows: Long,
    val ratePct: Double
)

data class RevenueByMethod(
    val method: String,
    val transactionCount: Long,
    val totalAmount: BigDecimal,
    val pct: Double
)


// =============================================
// domain/analytics/service/AnalyticsService.kt
// =============================================
package com.cliniq.domain.analytics.service

import com.cliniq.domain.analytics.dto.*
import com.cliniq.domain.pharmacy.service.PharmacyService
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional(readOnly = true)
class AnalyticsService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val pharmacyService: PharmacyService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    // ------------------------------------------------
    // Admin dashboard: unified payload
    // ------------------------------------------------
    fun getDashboardSummary(clinicId: UUID): DashboardSummary {
        val params = MapSqlParameterSource("clinicId", clinicId)

        val today = jdbc.queryForMap("""
            SELECT
                COUNT(*) AS total,
                COUNT(*) FILTER (WHERE status = 'COMPLETED')  AS completed,
                COUNT(*) FILTER (WHERE status = 'NO_SHOW')    AS no_shows,
                COUNT(*) FILTER (WHERE status = 'CHECKED_IN') AS checked_in,
                COUNT(*) FILTER (WHERE status IN ('SCHEDULED','CONFIRMED','CHECKED_IN','IN_CONSULTATION')) AS remaining,
                ROUND(
                    COUNT(*) FILTER (WHERE status='NO_SHOW')::NUMERIC /
                    NULLIF(COUNT(*) FILTER (WHERE status NOT IN ('SCHEDULED','CONFIRMED','CHECKED_IN','IN_CONSULTATION')),0)*100
                ,1) AS no_show_rate
            FROM appointments
            WHERE clinic_id = :clinicId AND appointment_date = CURRENT_DATE
        """, params)

        val revenue = jdbc.queryForMap("""
            SELECT
                COALESCE(SUM(total), 0) AS gross,
                COALESCE(SUM(paid_amount), 0) AS collected,
                COALESCE(SUM(total - paid_amount), 0) AS outstanding
            FROM invoices
            WHERE clinic_id = :clinicId
              AND DATE_TRUNC('month', issued_at) = DATE_TRUNC('month', NOW())
        """, params)

        val retention = try {
            jdbc.queryForMap("""
                SELECT
                    COALESCE(new_patients, 0) AS new_patients,
                    COALESCE(returning_patients, 0) AS returning_patients,
                    COALESCE(retention_rate_pct, 0) AS retention_rate_pct
                FROM v_patient_lifetime_value_monthly
                WHERE clinic_id = :clinicId
                  AND visit_month = DATE_TRUNC('month', CURRENT_DATE)::DATE
                LIMIT 1
            """, params)
        } catch (_: Exception) {
            mapOf("new_patients" to 0L, "returning_patients" to 0L, "retention_rate_pct" to 0.0)
        }

        val pendingLab = (jdbc.queryForObject(
            "SELECT COUNT(*) FROM lab_requests WHERE clinic_id = :clinicId AND status NOT IN ('COMPLETED','CANCELLED')",
            params, Long::class.java
        ) ?: 0L).toInt()

        val stockAlerts = try { pharmacyService.getStockAlerts(clinicId).alerts.size } catch (_: Exception) { 0 }

        return DashboardSummary(
            todayTotal       = num(today["total"]),
            todayCompleted   = num(today["completed"]),
            todayNoShows     = num(today["no_shows"]),
            todayRemaining   = num(today["remaining"]),
            todayCheckedIn   = num(today["checked_in"]),
            noShowRatePct    = dbl(today["no_show_rate"]),
            monthRevenue     = dec(revenue["gross"]),
            monthCollected   = dec(revenue["collected"]),
            outstandingBalance = dec(revenue["outstanding"]),
            newPatientsThisMonth      = num(retention["new_patients"]),
            returningPatientsThisMonth = num(retention["returning_patients"]),
            retentionRatePct = dbl(retention["retention_rate_pct"]),
            lowStockAlerts   = stockAlerts,
            pendingLabRequests = pendingLab
        )
    }

    // ------------------------------------------------
    // Weekly trends (last N weeks)
    // ------------------------------------------------
    fun getWeeklyTrends(clinicId: UUID, weeks: Int = 12): List<WeeklyTrendPoint> {
        return jdbc.query("""
            SELECT
                DATE_TRUNC('week', appointment_date)::DATE AS week_start,
                COUNT(*) AS total,
                COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed,
                COUNT(*) FILTER (WHERE status = 'NO_SHOW') AS no_shows,
                ROUND(
                    COUNT(*) FILTER (WHERE status='COMPLETED')::NUMERIC /
                    NULLIF(COUNT(*),0)*100
                ,1) AS completion_rate
            FROM appointments
            WHERE clinic_id = :clinicId
              AND appointment_date >= CURRENT_DATE - INTERVAL '1 week' * :weeks
            GROUP BY DATE_TRUNC('week', appointment_date)
            ORDER BY week_start DESC
        """, MapSqlParameterSource()
            .addValue("clinicId", clinicId)
            .addValue("weeks", weeks)
        ) { rs, _ ->
            WeeklyTrendPoint(
                weekStart = rs.getDate("week_start").toLocalDate(),
                total = rs.getLong("total"),
                completed = rs.getLong("completed"),
                noShows = rs.getLong("no_shows"),
                completionRatePct = rs.getDouble("completion_rate")
            )
        }
    }

    // ------------------------------------------------
    // Doctor utilization
    // ------------------------------------------------
    fun getDoctorUtilization(clinicId: UUID): List<DoctorUtilizationRow> {
        return jdbc.query("""
            SELECT
                a.doctor_id,
                u.first_name || ' ' || u.last_name AS doctor_name,
                d.specialization,
                COUNT(a.id) AS total,
                COUNT(a.id) FILTER (WHERE a.status = 'COMPLETED') AS completed,
                COUNT(a.id) FILTER (WHERE a.status = 'NO_SHOW') AS no_shows,
                ROUND(COUNT(a.id) FILTER (WHERE a.status='COMPLETED')::NUMERIC / NULLIF(COUNT(a.id),0)*100,1) AS completion_pct,
                COALESCE(SUM(i.paid_amount) FILTER (WHERE i.status='PAID'), 0) AS revenue
            FROM appointments a
            JOIN doctors d ON d.id = a.doctor_id
            JOIN users u ON u.id = d.user_id
            LEFT JOIN invoices i ON i.appointment_id = a.id
            WHERE a.clinic_id = :clinicId
              AND a.appointment_date >= CURRENT_DATE - INTERVAL '30 days'
            GROUP BY a.doctor_id, u.first_name, u.last_name, d.specialization
            ORDER BY completed DESC
        """, MapSqlParameterSource("clinicId", clinicId)
        ) { rs, _ ->
            DoctorUtilizationRow(
                doctorId = UUID.fromString(rs.getString("doctor_id")),
                doctorName = rs.getString("doctor_name"),
                specialization = rs.getString("specialization"),
                consultationsDone = rs.getLong("completed"),
                noShows = rs.getLong("no_shows"),
                completionRatePct = rs.getDouble("completion_pct"),
                revenueGenerated = rs.getBigDecimal("revenue")
            )
        }
    }

    // ------------------------------------------------
    // Patient segments
    // ------------------------------------------------
    fun getPatientSegments(clinicId: UUID): List<PatientSegmentCount> {
        return jdbc.query("""
            SELECT
                patient_segment AS segment,
                COUNT(*) AS count,
                COALESCE(SUM(total_spend), 0) AS total_spend,
                COALESCE(AVG(total_spend), 0) AS avg_spend
            FROM v_patient_lifetime_value
            WHERE clinic_id = :clinicId
            GROUP BY patient_segment
            ORDER BY count DESC
        """, MapSqlParameterSource("clinicId", clinicId)
        ) { rs, _ ->
            val total = rs.getBigDecimal("total_spend")
            PatientSegmentCount(
                segment = rs.getString("segment"),
                count = rs.getLong("count"),
                totalSpend = total,
                avgSpend = rs.getBigDecimal("avg_spend").setScale(0, RoundingMode.HALF_UP)
            )
        }
    }

    // ------------------------------------------------
    // At-risk patients
    // ------------------------------------------------
    fun getAtRiskPatients(clinicId: UUID, limit: Int = 50): List<AtRiskPatient> {
        return jdbc.query("""
            SELECT patient_id, patient_name, phone, email,
                   last_visit, days_inactive, total_visits, churn_risk
            FROM v_at_risk_patients
            WHERE clinic_id = :clinicId
            ORDER BY days_inactive DESC
            LIMIT :limit
        """, MapSqlParameterSource()
            .addValue("clinicId", clinicId)
            .addValue("limit", limit)
        ) { rs, _ ->
            AtRiskPatient(
                patientId = UUID.fromString(rs.getString("patient_id")),
                patientName = rs.getString("patient_name"),
                phone = rs.getString("phone"),
                email = rs.getString("email"),
                lastVisit = rs.getDate("last_visit").toLocalDate(),
                daysInactive = rs.getInt("days_inactive"),
                totalVisits = rs.getLong("total_visits"),
                churnRisk = rs.getString("churn_risk")
            )
        }
    }

    // ------------------------------------------------
    // No-show by day of week
    // ------------------------------------------------
    fun getNoShowByDay(clinicId: UUID): List<NoShowByDay> {
        return jdbc.query("""
            SELECT
                EXTRACT(DOW FROM appointment_date)::INT AS dow,
                TO_CHAR(appointment_date, 'Day') AS day_name,
                COUNT(*) AS total,
                COUNT(*) FILTER (WHERE status = 'NO_SHOW') AS no_shows,
                ROUND(COUNT(*) FILTER (WHERE status='NO_SHOW')::NUMERIC / NULLIF(COUNT(*),0)*100,1) AS rate_pct
            FROM appointments
            WHERE clinic_id = :clinicId
              AND appointment_date >= CURRENT_DATE - INTERVAL '90 days'
              AND status NOT IN ('SCHEDULED','CONFIRMED','CHECKED_IN','IN_CONSULTATION')
            GROUP BY EXTRACT(DOW FROM appointment_date), TO_CHAR(appointment_date,'Day')
            ORDER BY dow
        """, MapSqlParameterSource("clinicId", clinicId)
        ) { rs, _ ->
            NoShowByDay(
                dayOfWeek = rs.getInt("dow"),
                dayName = rs.getString("day_name").trim(),
                total = rs.getLong("total"),
                noShows = rs.getLong("no_shows"),
                ratePct = rs.getDouble("rate_pct")
            )
        }
    }

    // ------------------------------------------------
    // Refresh materialized view (scheduled)
    // ------------------------------------------------
    @Scheduled(cron = "0 0 */4 * * *")
    fun refreshViews() {
        try {
            jdbc.jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_appointment_summary")
            log.info("Materialized views refreshed")
        } catch (e: Exception) {
            log.error("View refresh failed", e)
        }
    }

    // ------------------------------------------------
    // Helpers
    // ------------------------------------------------
    private fun num(v: Any?) = (v as? Number)?.toLong() ?: 0L
    private fun dbl(v: Any?) = (v as? Number)?.toDouble() ?: 0.0
    private fun dec(v: Any?) = (v as? BigDecimal) ?: BigDecimal.ZERO
}


// =============================================
// domain/analytics/controller/AnalyticsController.kt
// =============================================
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
    fun weeklyTrends(
        @RequestParam(defaultValue = "12") weeks: Int,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.ok(ApiResponse.success(analyticsService.getWeeklyTrends(auth.clinicId!!, weeks)))

    @GetMapping("/doctors/utilization")
    fun doctorUtilization(@AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(analyticsService.getDoctorUtilization(auth.clinicId!!)))

    @GetMapping("/patients/segments")
    fun patientSegments(@AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(analyticsService.getPatientSegments(auth.clinicId!!)))

    @GetMapping("/patients/at-risk")
    fun atRisk(
        @RequestParam(defaultValue = "50") limit: Int,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.ok(ApiResponse.success(analyticsService.getAtRiskPatients(auth.clinicId!!, limit)))

    @GetMapping("/no-shows/by-day")
    fun noShowByDay(@AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(analyticsService.getNoShowByDay(auth.clinicId!!)))
}


// =============================================================
// CRM MODULE
// =============================================================

// =============================================
// domain/crm/dto/CrmDTOs.kt
// =============================================
package com.cliniq.domain.crm.dto

import com.cliniq.domain.communication.entity.CommChannel
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateCampaignRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
    @field:NotNull val channel: CommChannel,
    @field:NotBlank val messageTemplate: String,
    val targetSegment: CampaignTarget = CampaignTarget(),
    val scheduledAt: Instant? = null
)

data class CampaignTarget(
    val segment: String? = null,        // VIP | LOYAL | RETURNING | NEW | ALL
    val minDaysInactive: Int? = null,
    val tags: List<String> = emptyList(),
    val maxPatients: Int = 500
)

data class CampaignResponse(
    val id: UUID,
    val name: String,
    val channel: CommChannel,
    val messageTemplate: String,
    val status: String,
    val targetSegment: Map<String, Any>,
    val scheduledAt: Instant?,
    val sentCount: Int,
    val deliveredCount: Int,
    val failedCount: Int,
    val createdAt: Instant
)

data class FollowUpRequest(
    @field:NotNull val patientId: UUID,
    @field:NotBlank val message: String,
    val channel: CommChannel = CommChannel.SMS,
    val scheduledAt: Instant? = null
)


// =============================================
// domain/crm/service/CrmService.kt
// =============================================
package com.cliniq.domain.crm.service

import com.cliniq.domain.analytics.service.AnalyticsService
import com.cliniq.domain.communication.entity.CommChannel
import com.cliniq.domain.communication.entity.CommType
import com.cliniq.domain.communication.service.NotificationService
import com.cliniq.domain.crm.dto.*
import com.cliniq.domain.patient.repository.PatientRepository
import com.cliniq.infrastructure.sms.AfricasTalkingService
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import jakarta.persistence.*

// CRM Campaign entity
@Entity
@Table(name = "crm_campaigns")
data class CrmCampaign(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),
    @Column(name = "clinic_id") val clinicId: UUID,
    @Column var name: String,
    @Column var description: String? = null,
    @Enumerated(EnumType.STRING) var channel: CommChannel,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_segment", columnDefinition = "jsonb") var targetSegment: Map<String, Any> = emptyMap(),
    @Column(name = "message_template", columnDefinition = "TEXT") var messageTemplate: String,
    @Column(name = "scheduled_at") var scheduledAt: Instant? = null,
    @Column var status: String = "DRAFT",
    @Column(name = "sent_count") var sentCount: Int = 0,
    @Column(name = "delivered_count") var deliveredCount: Int = 0,
    @Column(name = "failed_count") var failedCount: Int = 0,
    @Column(name = "created_by") val createdBy: UUID? = null,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now()
)

@org.springframework.data.jpa.repository.JpaRepository
interface CrmCampaignRepository : org.springframework.data.jpa.repository.JpaRepository<CrmCampaign, UUID> {
    fun findByClinicIdOrderByCreatedAtDesc(clinicId: UUID): List<CrmCampaign>
    fun findByStatusAndScheduledAtBefore(status: String, now: Instant): List<CrmCampaign>
}

@Service
@Transactional
class CrmService(
    private val campaignRepository: CrmCampaignRepository,
    private val patientRepository: PatientRepository,
    private val analyticsService: AnalyticsService,
    private val smsService: AfricasTalkingService,
    private val jdbc: NamedParameterJdbcTemplate
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    // ------------------------------------------------
    // Campaign management
    // ------------------------------------------------

    fun createCampaign(clinicId: UUID, request: CreateCampaignRequest, createdBy: UUID): CampaignResponse {
        val campaign = campaignRepository.save(
            CrmCampaign(
                clinicId = clinicId,
                name = request.name,
                description = request.description,
                channel = request.channel,
                messageTemplate = request.messageTemplate,
                targetSegment = mapOf(
                    "segment" to (request.targetSegment.segment ?: "ALL"),
                    "minDaysInactive" to (request.targetSegment.minDaysInactive ?: 0),
                    "tags" to request.targetSegment.tags,
                    "maxPatients" to request.targetSegment.maxPatients
                ),
                scheduledAt = request.scheduledAt,
                status = if (request.scheduledAt != null) "SCHEDULED" else "DRAFT",
                createdBy = createdBy
            )
        )
        return campaign.toResponse()
    }

    fun sendCampaignNow(clinicId: UUID, campaignId: UUID): CampaignResponse {
        val campaign = campaignRepository.findById(campaignId).orElseThrow()
        require(campaign.clinicId == clinicId) { "Access denied" }

        campaign.status = "RUNNING"
        campaignRepository.save(campaign)

        val targets = resolveTargets(clinicId, campaign)
        log.info("Campaign '{}': sending to {} patients via {}", campaign.name, targets.size, campaign.channel)

        var sent = 0; var failed = 0
        targets.forEach { (phone, name) ->
            val personalised = campaign.messageTemplate
                .replace("{name}", name.split(" ").first())
                .replace("{clinicName}", "")
            try {
                smsService.send(phone, personalised)
                sent++
            } catch (e: Exception) {
                log.warn("Campaign send failed to {}: {}", phone.takeLast(4), e.message)
                failed++
            }
        }

        campaign.sentCount = sent
        campaign.failedCount = failed
        campaign.deliveredCount = sent  // Africa's Talking doesn't have real-time DLR here
        campaign.status = "COMPLETED"
        campaign.updatedAt = Instant.now()
        return campaignRepository.save(campaign).toResponse()
    }

    fun listCampaigns(clinicId: UUID): List<CampaignResponse> =
        campaignRepository.findByClinicIdOrderByCreatedAtDesc(clinicId).map { it.toResponse() }

    // ------------------------------------------------
    // Scheduled: auto-send campaigns whose scheduledAt has passed
    // ------------------------------------------------
    @Scheduled(cron = "0 */15 * * * *")  // every 15 minutes
    fun processScheduledCampaigns() {
        val due = campaignRepository.findByStatusAndScheduledAtBefore("SCHEDULED", Instant.now())
        if (due.isEmpty()) return
        log.info("Processing {} scheduled campaigns", due.size)
        due.forEach { c ->
            try { sendCampaignNow(c.clinicId, c.id) }
            catch (e: Exception) { log.error("Failed to process campaign {}", c.id, e) }
        }
    }

    // ------------------------------------------------
    // Scheduled: daily at-risk patient outreach
    // ------------------------------------------------
    @Scheduled(cron = "0 0 9 * * MON")  // Every Monday at 9 AM
    fun sendAtRiskOutreach() {
        log.info("Running weekly at-risk patient outreach")

        // Get all active clinics
        val clinicIds = jdbc.queryForList(
            "SELECT id FROM clinics WHERE is_active = TRUE",
            emptyMap<String, Any>(),
            UUID::class.java
        )

        clinicIds.forEach { clinicId ->
            try {
                val atRisk = analyticsService.getAtRiskPatients(clinicId, limit = 100)
                    .filter { it.churnRisk == "AT_RISK" || it.churnRisk == "COOLING" }
                    .take(50)

                if (atRisk.isEmpty()) return@forEach

                atRisk.forEach { patient ->
                    val msg = "Hi ${patient.patientName.split(" ").first()}! We miss you at our clinic. Book your next appointment today — reply BOOK or call us."
                    try {
                        smsService.send(patient.phone, msg)
                        log.debug("Outreach sent to {}", patient.phone.takeLast(4))
                    } catch (e: Exception) {
                        log.warn("Outreach failed to {}: {}", patient.phone.takeLast(4), e.message)
                    }
                }
                log.info("Clinic {}: at-risk outreach sent to {} patients", clinicId, atRisk.size)
            } catch (e: Exception) {
                log.error("At-risk outreach failed for clinic {}", clinicId, e)
            }
        }
    }

    // ------------------------------------------------
    // Helpers
    // ------------------------------------------------

    private fun resolveTargets(clinicId: UUID, campaign: CrmCampaign): List<Pair<String, String>> {
        val segment = campaign.targetSegment["segment"] as? String ?: "ALL"
        val maxPt = (campaign.targetSegment["maxPatients"] as? Int) ?: 500

        val sql = when (segment) {
            "ALL" -> """
                SELECT p.phone, p.first_name || ' ' || p.last_name AS name
                FROM patients p WHERE p.clinic_id = :clinicId AND p.phone IS NOT NULL
                LIMIT :max
            """
            "AT_RISK" -> """
                SELECT p.phone, p.first_name || ' ' || p.last_name AS name
                FROM v_at_risk_patients r JOIN patients p ON p.id = r.patient_id
                WHERE r.clinic_id = :clinicId AND r.churn_risk IN ('AT_RISK','COOLING')
                LIMIT :max
            """
            else -> """
                SELECT p.phone, p.first_name || ' ' || p.last_name AS name
                FROM v_patient_lifetime_value v JOIN patients p ON p.id = v.patient_id
                WHERE v.clinic_id = :clinicId AND v.patient_segment = :segment
                LIMIT :max
            """
        }

        return jdbc.query(
            sql,
            MapSqlParameterSource().addValue("clinicId", clinicId)
                .addValue("max", maxPt).addValue("segment", segment)
        ) { rs, _ -> Pair(rs.getString("phone"), rs.getString("name")) }
    }

    private fun CrmCampaign.toResponse() = CampaignResponse(
        id = id, name = name, channel = channel, messageTemplate = messageTemplate,
        status = status, targetSegment = targetSegment, scheduledAt = scheduledAt,
        sentCount = sentCount, deliveredCount = deliveredCount, failedCount = failedCount,
        createdAt = createdAt
    )
}


// =============================================
// domain/crm/controller/CrmController.kt
// =============================================
package com.cliniq.domain.crm.controller

import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.domain.crm.dto.CreateCampaignRequest
import com.cliniq.domain.crm.service.CrmService
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
    fun createCampaign(
        @Valid @RequestBody request: CreateCampaignRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.status(201).body(
        ApiResponse.success(crmService.createCampaign(auth.clinicId!!, request, auth.userId))
    )

    @GetMapping("/campaigns")
    fun listCampaigns(@AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(crmService.listCampaigns(auth.clinicId!!)))

    @PostMapping("/campaigns/{id}/send")
    fun sendNow(
        @PathVariable id: UUID,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.ok(ApiResponse.success(crmService.sendCampaignNow(auth.clinicId!!, id)))
}
