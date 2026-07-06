-- =============================================================
-- ClinIQ — Analytics Module
-- File: V4__analytics_views.sql
-- All views are clinic-scoped. Always filter by clinic_id.
-- =============================================================


-- =====================================================
-- DAILY APPOINTMENT SUMMARY
-- Used by: admin dashboard header stats
-- =====================================================
CREATE MATERIALIZED VIEW mv_daily_appointment_summary AS
SELECT
    clinic_id,
    appointment_date                                                     AS report_date,
    COUNT(*)                                                             AS total_appointments,
    COUNT(*) FILTER (WHERE status = 'COMPLETED')                        AS completed,
    COUNT(*) FILTER (WHERE status = 'NO_SHOW')                          AS no_shows,
    COUNT(*) FILTER (WHERE status = 'CANCELLED')                        AS cancelled,
    COUNT(*) FILTER (WHERE status IN ('SCHEDULED','CONFIRMED','CHECKED_IN','IN_CONSULTATION')) AS remaining,
    ROUND(
        COUNT(*) FILTER (WHERE status = 'NO_SHOW')::NUMERIC /
        NULLIF(COUNT(*) FILTER (WHERE status NOT IN ('SCHEDULED','CONFIRMED','CHECKED_IN','IN_CONSULTATION')), 0) * 100,
    1)                                                                   AS no_show_rate_pct,
    COUNT(DISTINCT doctor_id)                                            AS active_doctors,
    COUNT(DISTINCT patient_id)                                           AS unique_patients
FROM appointments
GROUP BY clinic_id, appointment_date;

CREATE UNIQUE INDEX ON mv_daily_appointment_summary (clinic_id, report_date);
CREATE INDEX ON mv_daily_appointment_summary (clinic_id, report_date DESC);


-- =====================================================
-- WEEKLY APPOINTMENT TRENDS (last 12 weeks)
-- Used by: admin analytics chart
-- =====================================================
CREATE VIEW v_weekly_appointment_trends AS
SELECT
    clinic_id,
    DATE_TRUNC('week', appointment_date)::DATE                          AS week_start,
    COUNT(*)                                                             AS total,
    COUNT(*) FILTER (WHERE status = 'COMPLETED')                        AS completed,
    COUNT(*) FILTER (WHERE status = 'NO_SHOW')                          AS no_shows,
    ROUND(AVG(EXTRACT(EPOCH FROM (a.updated_at - a.created_at))/60), 0) AS avg_booking_lead_minutes
FROM appointments a
WHERE appointment_date >= CURRENT_DATE - INTERVAL '12 weeks'
GROUP BY clinic_id, DATE_TRUNC('week', appointment_date)
ORDER BY week_start DESC;


-- =====================================================
-- REVENUE ANALYTICS
-- Used by: finance dashboard, admin analytics
-- =====================================================
CREATE VIEW v_revenue_summary AS
SELECT
    i.clinic_id,
    DATE_TRUNC('month', i.issued_at)::DATE                              AS month,
    COUNT(DISTINCT i.id)                                                 AS total_invoices,
    COUNT(DISTINCT i.id) FILTER (WHERE i.status = 'PAID')               AS paid_invoices,
    COUNT(DISTINCT i.id) FILTER (WHERE i.status IN ('ISSUED','OVERDUE')) AS outstanding_invoices,
    SUM(i.total)                                                         AS gross_revenue,
    SUM(i.paid_amount)                                                   AS collected_revenue,
    SUM(i.total - i.paid_amount)                                         AS outstanding_balance,
    SUM(i.total) FILTER (WHERE i.status = 'PAID')                        AS fully_paid_revenue
FROM invoices i
GROUP BY i.clinic_id, DATE_TRUNC('month', i.issued_at);


-- =====================================================
-- REVENUE BY PAYMENT METHOD
-- =====================================================
CREATE VIEW v_revenue_by_payment_method AS
SELECT
    p.clinic_id,
    DATE_TRUNC('month', p.paid_at)::DATE AS month,
    p.method,
    COUNT(*)                             AS transaction_count,
    SUM(p.amount)                        AS total_amount
FROM payments p
GROUP BY p.clinic_id, DATE_TRUNC('month', p.paid_at), p.method
ORDER BY month DESC, total_amount DESC;


-- =====================================================
-- DOCTOR UTILIZATION
-- Used by: admin analytics, HR planning
-- =====================================================
CREATE VIEW v_doctor_utilization AS
SELECT
    a.clinic_id,
    a.doctor_id,
    u.first_name || ' ' || u.last_name                                  AS doctor_name,
    d.specialization,
    DATE_TRUNC('week', a.appointment_date)::DATE                        AS week_start,
    COUNT(*)                                                             AS total_slots_used,
    COUNT(*) FILTER (WHERE a.status = 'COMPLETED')                      AS consultations_done,
    COUNT(*) FILTER (WHERE a.status = 'NO_SHOW')                        AS no_shows,
    ROUND(
        COUNT(*) FILTER (WHERE a.status = 'COMPLETED')::NUMERIC /
        NULLIF(COUNT(*), 0) * 100,
    1)                                                                   AS completion_rate_pct,
    SUM(i.total) FILTER (WHERE i.status = 'PAID')                        AS revenue_generated
FROM appointments a
JOIN doctors d ON d.id = a.doctor_id
JOIN users u ON u.id = d.user_id
LEFT JOIN invoices i ON i.appointment_id = a.id
WHERE a.appointment_date >= CURRENT_DATE - INTERVAL '12 weeks'
GROUP BY a.clinic_id, a.doctor_id, u.first_name, u.last_name, d.specialization,
         DATE_TRUNC('week', a.appointment_date);


-- =====================================================
-- PATIENT RETENTION (returning vs new)
-- Used by: CRM dashboard, retention analytics
-- =====================================================
CREATE VIEW v_patient_retention AS
WITH patient_visits AS (
    SELECT
        clinic_id,
        patient_id,
        appointment_date,
        ROW_NUMBER() OVER (PARTITION BY clinic_id, patient_id ORDER BY appointment_date) AS visit_number,
        DATE_TRUNC('month', appointment_date)::DATE AS visit_month
    FROM appointments
    WHERE status = 'COMPLETED'
)
SELECT
    clinic_id,
    visit_month,
    COUNT(DISTINCT patient_id)                                             AS total_patients,
    COUNT(DISTINCT patient_id) FILTER (WHERE visit_number = 1)            AS new_patients,
    COUNT(DISTINCT patient_id) FILTER (WHERE visit_number > 1)            AS returning_patients,
    ROUND(
        COUNT(DISTINCT patient_id) FILTER (WHERE visit_number > 1)::NUMERIC /
        NULLIF(COUNT(DISTINCT patient_id), 0) * 100,
    1)                                                                     AS retention_rate_pct
FROM patient_visits
GROUP BY clinic_id, visit_month
ORDER BY visit_month DESC;


-- =====================================================
-- NO-SHOW ANALYTICS (by doctor, by day-of-week, by type)
-- Used by: operational insights
-- =====================================================
CREATE VIEW v_no_show_analytics AS
SELECT
    clinic_id,
    EXTRACT(DOW FROM appointment_date)::INT                              AS day_of_week,
    TO_CHAR(appointment_date, 'Day')                                     AS day_name,
    type,
    doctor_id,
    COUNT(*)                                                             AS total,
    COUNT(*) FILTER (WHERE status = 'NO_SHOW')                          AS no_shows,
    ROUND(
        COUNT(*) FILTER (WHERE status = 'NO_SHOW')::NUMERIC /
        NULLIF(COUNT(*), 0) * 100,
    1)                                                                   AS no_show_rate_pct
FROM appointments
WHERE appointment_date >= CURRENT_DATE - INTERVAL '90 days'
  AND status NOT IN ('SCHEDULED', 'CONFIRMED', 'CHECKED_IN', 'IN_CONSULTATION')
GROUP BY clinic_id, EXTRACT(DOW FROM appointment_date), TO_CHAR(appointment_date,'Day'), type, doctor_id
ORDER BY no_show_rate_pct DESC;


-- =====================================================
-- PATIENT LIFETIME VALUE
-- Used by: CRM segmentation, VIP patient tracking
-- =====================================================
CREATE VIEW v_patient_lifetime_value AS
SELECT
    p.clinic_id,
    p.id                                                                  AS patient_id,
    p.first_name || ' ' || p.last_name                                   AS patient_name,
    p.phone,
    p.tags,
    COUNT(DISTINCT a.id)                                                  AS total_visits,
    MAX(a.appointment_date)                                               AS last_visit_date,
    MIN(a.appointment_date)                                               AS first_visit_date,
    CURRENT_DATE - MAX(a.appointment_date)                                AS days_since_last_visit,
    COALESCE(SUM(i.paid_amount), 0)                                       AS total_spend,
    COALESCE(AVG(i.paid_amount) FILTER (WHERE i.paid_amount > 0), 0)     AS avg_spend_per_visit,
    CASE
        WHEN COUNT(DISTINCT a.id) >= 10 AND COALESCE(SUM(i.paid_amount),0) > 50000 THEN 'VIP'
        WHEN COUNT(DISTINCT a.id) >= 5  THEN 'LOYAL'
        WHEN COUNT(DISTINCT a.id) >= 2  THEN 'RETURNING'
        ELSE 'NEW'
    END                                                                    AS patient_segment
FROM patients p
LEFT JOIN appointments a ON a.patient_id = p.id AND a.status = 'COMPLETED'
LEFT JOIN invoices i ON i.patient_id = p.id AND i.status = 'PAID'
GROUP BY p.clinic_id, p.id, p.first_name, p.last_name, p.phone, p.tags;


-- =====================================================
-- AT-RISK PATIENTS (overdue for follow-up)
-- Used by: CRM follow-up campaigns
-- =====================================================
CREATE VIEW v_at_risk_patients AS
SELECT
    p.clinic_id,
    p.id                                AS patient_id,
    p.first_name || ' ' || p.last_name AS patient_name,
    p.phone,
    p.email,
    MAX(a.appointment_date)             AS last_visit,
    CURRENT_DATE - MAX(a.appointment_date) AS days_inactive,
    COUNT(DISTINCT a.id)                AS total_visits,
    CASE
        WHEN CURRENT_DATE - MAX(a.appointment_date) BETWEEN 30 AND 60   THEN 'COOLING'
        WHEN CURRENT_DATE - MAX(a.appointment_date) BETWEEN 61 AND 120  THEN 'AT_RISK'
        WHEN CURRENT_DATE - MAX(a.appointment_date) > 120               THEN 'LOST'
    END                                  AS churn_risk
FROM patients p
JOIN appointments a ON a.patient_id = p.id AND a.status = 'COMPLETED'
GROUP BY p.clinic_id, p.id, p.first_name, p.last_name, p.phone, p.email
HAVING CURRENT_DATE - MAX(a.appointment_date) >= 30
ORDER BY days_inactive DESC;


-- =====================================================
-- REFRESH POLICY for materialized view
-- Run via pg_cron or Spring scheduler
-- =====================================================
-- SELECT cron.schedule('refresh-daily-summary', '0 */4 * * *',
--   'REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_appointment_summary');


-- =====================================================
-- QUICK OPERATIONAL QUERIES (ad-hoc, not views)
-- =====================================================

-- Today's appointment completion rate
-- SELECT
--     COUNT(*) FILTER (WHERE status = 'COMPLETED') * 100.0 / NULLIF(COUNT(*),0) AS completion_pct
-- FROM appointments
-- WHERE clinic_id = :clinicId AND appointment_date = CURRENT_DATE;

-- Peak hour analysis
-- SELECT
--     EXTRACT(HOUR FROM start_time)::INT AS hour,
--     COUNT(*) AS appointment_count
-- FROM appointments
-- WHERE clinic_id = :clinicId
--   AND appointment_date >= CURRENT_DATE - INTERVAL '30 days'
--   AND status = 'COMPLETED'
-- GROUP BY EXTRACT(HOUR FROM start_time)
-- ORDER BY appointment_count DESC;


/*
===============================================================
Spring Boot Analytics Service — AnalyticsService.kt
Package: com.cliniq.domain.analytics
===============================================================
*/

// =============================================
// dto/AnalyticsDTOs.kt
// =============================================
// package com.cliniq.domain.analytics.dto
//
// import java.math.BigDecimal
// import java.time.LocalDate
// import java.util.UUID
//
// data class DashboardSummary(
//     val todayTotal: Long,
//     val todayCompleted: Long,
//     val todayNoShows: Long,
//     val todayRemaining: Long,
//     val noShowRatePct: Double,
//     val monthRevenue: BigDecimal,
//     val monthCollected: BigDecimal,
//     val outstandingBalance: BigDecimal,
//     val newPatientsThisMonth: Long,
//     val returningPatientsThisMonth: Long,
//     val retentionRatePct: Double
// )
//
// data class WeeklyTrendPoint(
//     val weekStart: LocalDate,
//     val total: Long,
//     val completed: Long,
//     val noShows: Long
// )
//
// data class DoctorUtilizationRow(
//     val doctorId: UUID,
//     val doctorName: String,
//     val specialization: String?,
//     val consultationsDone: Long,
//     val completionRatePct: Double,
//     val revenueGenerated: BigDecimal
// )
//
// data class PatientSegmentCount(
//     val segment: String,
//     val count: Long,
//     val totalSpend: BigDecimal
// )
//
// data class AtRiskPatient(
//     val patientId: UUID,
//     val patientName: String,
//     val phone: String,
//     val email: String?,
//     val lastVisit: LocalDate,
//     val daysInactive: Int,
//     val churnRisk: String
// )

/* =============================================
   AnalyticsService.kt
   ============================================= */

// Note: paste below into com/cliniq/domain/analytics/service/AnalyticsService.kt

/*
@Service
@Transactional(readOnly = true)
class AnalyticsService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    // -----------------------------------------------
    // Admin dashboard: single consolidated payload
    // -----------------------------------------------
    fun getDashboardSummary(clinicId: UUID): DashboardSummary {
        val params = MapSqlParameterSource("clinicId", clinicId)

        // Today's appointment row
        val today = jdbcTemplate.queryForMap("""
            SELECT
                COUNT(*) AS total,
                COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed,
                COUNT(*) FILTER (WHERE status = 'NO_SHOW') AS no_shows,
                COUNT(*) FILTER (WHERE status IN ('SCHEDULED','CONFIRMED','CHECKED_IN','IN_CONSULTATION')) AS remaining,
                ROUND(
                    COUNT(*) FILTER (WHERE status='NO_SHOW')::NUMERIC /
                    NULLIF(COUNT(*) FILTER(WHERE status NOT IN ('SCHEDULED','CONFIRMED','CHECKED_IN','IN_CONSULTATION')),0)*100,
                1) AS no_show_rate
            FROM appointments
            WHERE clinic_id = :clinicId AND appointment_date = CURRENT_DATE
        """, params)

        // Month revenue row
        val revenue = jdbcTemplate.queryForMap("""
            SELECT
                COALESCE(SUM(total), 0) AS gross,
                COALESCE(SUM(paid_amount), 0) AS collected,
                COALESCE(SUM(total - paid_amount), 0) AS outstanding
            FROM invoices
            WHERE clinic_id = :clinicId
              AND DATE_TRUNC('month', issued_at) = DATE_TRUNC('month', NOW())
        """, params)

        // Retention row (this month)
        val retention = jdbcTemplate.queryForMap("""
            SELECT
                COALESCE(new_patients, 0) AS new_patients,
                COALESCE(returning_patients, 0) AS returning_patients,
                COALESCE(retention_rate_pct, 0) AS retention_rate_pct
            FROM v_patient_retention
            WHERE clinic_id = :clinicId
              AND visit_month = DATE_TRUNC('month', CURRENT_DATE)::DATE
        """, params)

        return DashboardSummary(
            todayTotal = (today["total"] as Long?) ?: 0L,
            todayCompleted = (today["completed"] as Long?) ?: 0L,
            todayNoShows = (today["no_shows"] as Long?) ?: 0L,
            todayRemaining = (today["remaining"] as Long?) ?: 0L,
            noShowRatePct = ((today["no_show_rate"] as Number?)?.toDouble()) ?: 0.0,
            monthRevenue = (revenue["gross"] as BigDecimal?) ?: BigDecimal.ZERO,
            monthCollected = (revenue["collected"] as BigDecimal?) ?: BigDecimal.ZERO,
            outstandingBalance = (revenue["outstanding"] as BigDecimal?) ?: BigDecimal.ZERO,
            newPatientsThisMonth = (retention["new_patients"] as Long?) ?: 0L,
            returningPatientsThisMonth = (retention["returning_patients"] as Long?) ?: 0L,
            retentionRatePct = ((retention["retention_rate_pct"] as Number?)?.toDouble()) ?: 0.0
        )
    }

    fun getWeeklyTrends(clinicId: UUID, weeks: Int = 12): List<WeeklyTrendPoint> {
        return jdbcTemplate.query("""
            SELECT week_start, total, completed, no_shows
            FROM v_weekly_appointment_trends
            WHERE clinic_id = :clinicId
            ORDER BY week_start DESC
            LIMIT :weeks
        """, MapSqlParameterSource()
            .addValue("clinicId", clinicId)
            .addValue("weeks", weeks)
        ) { rs, _ ->
            WeeklyTrendPoint(
                weekStart = rs.getDate("week_start").toLocalDate(),
                total = rs.getLong("total"),
                completed = rs.getLong("completed"),
                noShows = rs.getLong("no_shows")
            )
        }
    }

    fun getDoctorUtilization(clinicId: UUID): List<DoctorUtilizationRow> {
        return jdbcTemplate.query("""
            SELECT
                doctor_id,
                doctor_name,
                specialization,
                SUM(consultations_done) AS consultations_done,
                ROUND(AVG(completion_rate_pct), 1) AS completion_rate_pct,
                COALESCE(SUM(revenue_generated), 0) AS revenue_generated
            FROM v_doctor_utilization
            WHERE clinic_id = :clinicId
            GROUP BY doctor_id, doctor_name, specialization
            ORDER BY consultations_done DESC
        """, MapSqlParameterSource("clinicId", clinicId)
        ) { rs, _ ->
            DoctorUtilizationRow(
                doctorId = UUID.fromString(rs.getString("doctor_id")),
                doctorName = rs.getString("doctor_name"),
                specialization = rs.getString("specialization"),
                consultationsDone = rs.getLong("consultations_done"),
                completionRatePct = rs.getDouble("completion_rate_pct"),
                revenueGenerated = rs.getBigDecimal("revenue_generated")
            )
        }
    }

    fun getPatientSegments(clinicId: UUID): List<PatientSegmentCount> {
        return jdbcTemplate.query("""
            SELECT
                patient_segment AS segment,
                COUNT(*) AS count,
                COALESCE(SUM(total_spend), 0) AS total_spend
            FROM v_patient_lifetime_value
            WHERE clinic_id = :clinicId
            GROUP BY patient_segment
            ORDER BY count DESC
        """, MapSqlParameterSource("clinicId", clinicId)
        ) { rs, _ ->
            PatientSegmentCount(
                segment = rs.getString("segment"),
                count = rs.getLong("count"),
                totalSpend = rs.getBigDecimal("total_spend")
            )
        }
    }

    fun getAtRiskPatients(clinicId: UUID, limit: Int = 50): List<AtRiskPatient> {
        return jdbcTemplate.query("""
            SELECT patient_id, patient_name, phone, email, last_visit, days_inactive, churn_risk
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
                churnRisk = rs.getString("churn_risk")
            )
        }
    }

    fun getNoShowByDayOfWeek(clinicId: UUID): List<Map<String, Any>> {
        return jdbcTemplate.queryForList("""
            SELECT day_name, SUM(no_shows) AS no_shows, SUM(total) AS total,
                   ROUND(AVG(no_show_rate_pct), 1) AS rate_pct
            FROM v_no_show_analytics
            WHERE clinic_id = :clinicId
            GROUP BY day_of_week, day_name
            ORDER BY day_of_week
        """, MapSqlParameterSource("clinicId", clinicId))
    }

    fun refreshMaterializedViews() {
        jdbcTemplate.jdbcTemplate.execute(
            "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_appointment_summary"
        )
    }
}
*/

/* =============================================
   AnalyticsController.kt
   ============================================= */
/*
@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAnyRole('CLINIC_ADMIN', 'DOCTOR', 'SUPER_ADMIN')")
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
    fun atRiskPatients(
        @RequestParam(defaultValue = "50") limit: Int,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.ok(ApiResponse.success(analyticsService.getAtRiskPatients(auth.clinicId!!, limit)))

    @GetMapping("/no-shows/by-day")
    fun noShowByDay(@AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(analyticsService.getNoShowByDayOfWeek(auth.clinicId!!)))
}
*/

/* =============================================
   Scheduled view refresh — AnalyticsScheduler.kt
   ============================================= */
/*
@Component
class AnalyticsScheduler(private val analyticsService: AnalyticsService) {

    private val log = LoggerFactory.getLogger(this::class.java)

    // Refresh materialized view every 4 hours
    @Scheduled(cron = "0 0 */4 * * *")
    fun refreshViews() {
        log.info("Refreshing analytics materialized views")
        try {
            analyticsService.refreshMaterializedViews()
            log.info("Views refreshed successfully")
        } catch (e: Exception) {
            log.error("Failed to refresh views", e)
        }
    }
}
*/
