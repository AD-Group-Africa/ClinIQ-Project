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

    fun getDashboardSummary(clinicId: UUID): DashboardSummary {
        val p = MapSqlParameterSource("clinicId", clinicId)
        val today = jdbc.queryForMap("""
            SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE status='COMPLETED') AS completed,
                   COUNT(*) FILTER (WHERE status='NO_SHOW') AS no_shows,
                   COUNT(*) FILTER (WHERE status='CHECKED_IN') AS checked_in,
                   COUNT(*) FILTER (WHERE status IN ('SCHEDULED','CONFIRMED','CHECKED_IN','IN_CONSULTATION')) AS remaining,
                   ROUND(COUNT(*) FILTER (WHERE status='NO_SHOW')::NUMERIC / NULLIF(COUNT(*) FILTER (WHERE status NOT IN ('SCHEDULED','CONFIRMED','CHECKED_IN','IN_CONSULTATION')),0)*100,1) AS no_show_rate
            FROM appointments WHERE clinic_id = :clinicId AND appointment_date = CURRENT_DATE
        """, p)
        val revenue = jdbc.queryForMap("""
            SELECT COALESCE(SUM(total),0) AS gross, COALESCE(SUM(paid_amount),0) AS collected,
                   COALESCE(SUM(total - paid_amount),0) AS outstanding
            FROM invoices WHERE clinic_id = :clinicId AND DATE_TRUNC('month', issued_at) = DATE_TRUNC('month', NOW())
        """, p)
        val ret = try {
            jdbc.queryForMap("""
                SELECT COALESCE(new_patients,0) AS new_p, COALESCE(returning_patients,0) AS ret_p,
                       COALESCE(retention_rate_pct,0) AS ret_rate
                FROM v_patient_lifetime_value_monthly WHERE clinic_id = :clinicId AND visit_month = DATE_TRUNC('month',CURRENT_DATE)::DATE LIMIT 1
            """, p)
        } catch (_: Exception) {
            mapOf("new_p" to 0L, "ret_p" to 0L, "ret_rate" to 0.0)
        }
        val pendingLab = (jdbc.queryForObject(
            "SELECT COUNT(*) FROM lab_requests WHERE clinic_id = :clinicId AND status NOT IN ('COMPLETED','CANCELLED')", p, Long::class.java
        ) ?: 0L).toInt()
        val stockAlerts = try { pharmacyService.getStockAlerts(clinicId).alerts.size } catch (_: Exception) { 0 }

        return DashboardSummary(
            todayTotal = num(today["total"]), todayCompleted = num(today["completed"]),
            todayNoShows = num(today["no_shows"]), todayRemaining = num(today["remaining"]),
            todayCheckedIn = num(today["checked_in"]), noShowRatePct = dbl(today["no_show_rate"]),
            monthRevenue = dec(revenue["gross"]), monthCollected = dec(revenue["collected"]),
            outstandingBalance = dec(revenue["outstanding"]),
            newPatientsThisMonth = num(ret["new_p"]), returningPatientsThisMonth = num(ret["ret_p"]),
            retentionRatePct = dbl(ret["ret_rate"]),
            lowStockAlerts = stockAlerts, pendingLabRequests = pendingLab
        )
    }

    fun getWeeklyTrends(clinicId: UUID, weeks: Int = 12): List<WeeklyTrendPoint> =
        jdbc.query("""
            SELECT DATE_TRUNC('week', appointment_date)::DATE AS week_start, COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE status='COMPLETED') AS completed,
                   COUNT(*) FILTER (WHERE status='NO_SHOW') AS no_shows,
                   ROUND(COUNT(*) FILTER (WHERE status='COMPLETED')::NUMERIC / NULLIF(COUNT(*),0)*100,1) AS completion_rate
            FROM appointments WHERE clinic_id = :clinicId AND appointment_date >= CURRENT_DATE - INTERVAL '1 week' * :weeks
            GROUP BY DATE_TRUNC('week', appointment_date) ORDER BY week_start DESC
        """, MapSqlParameterSource().addValue("clinicId", clinicId).addValue("weeks", weeks)
        ) { rs, _ ->
            WeeklyTrendPoint(rs.getDate("week_start").toLocalDate(), rs.getLong("total"), rs.getLong("completed"),
                rs.getLong("no_shows"), rs.getDouble("completion_rate"))
        }

    fun getDoctorUtilization(clinicId: UUID): List<DoctorUtilizationRow> =
        jdbc.query("""
            SELECT a.doctor_id, u.first_name || ' ' || u.last_name AS doctor_name, d.specialization,
                   COUNT(a.id) AS total, COUNT(a.id) FILTER (WHERE a.status='COMPLETED') AS completed,
                   COUNT(a.id) FILTER (WHERE a.status='NO_SHOW') AS no_shows,
                   ROUND(COUNT(a.id) FILTER (WHERE a.status='COMPLETED')::NUMERIC / NULLIF(COUNT(a.id),0)*100,1) AS comp_pct,
                   COALESCE(SUM(i.paid_amount) FILTER (WHERE i.status='PAID'),0) AS revenue
            FROM appointments a JOIN doctors d ON d.id = a.doctor_id JOIN users u ON u.id = d.user_id
            LEFT JOIN invoices i ON i.appointment_id = a.id
            WHERE a.clinic_id = :clinicId AND a.appointment_date >= CURRENT_DATE - INTERVAL '30 days'
            GROUP BY a.doctor_id, u.first_name, u.last_name, d.specialization ORDER BY completed DESC
        """, MapSqlParameterSource("clinicId", clinicId)
        ) { rs, _ ->
            DoctorUtilizationRow(UUID.fromString(rs.getString("doctor_id")), rs.getString("doctor_name"),
                rs.getString("specialization"), rs.getLong("completed"), rs.getLong("no_shows"),
                rs.getDouble("comp_pct"), rs.getBigDecimal("revenue"))
        }

    fun getPatientSegments(clinicId: UUID): List<PatientSegmentCount> =
        jdbc.query("""
            SELECT patient_segment AS segment, COUNT(*) AS count, COALESCE(SUM(total_spend),0) AS total_spend,
                   COALESCE(AVG(total_spend),0) AS avg_spend
            FROM v_patient_lifetime_value WHERE clinic_id = :clinicId GROUP BY patient_segment ORDER BY count DESC
        """, MapSqlParameterSource("clinicId", clinicId)
        ) { rs, _ ->
            PatientSegmentCount(rs.getString("segment"), rs.getLong("count"),
                rs.getBigDecimal("total_spend"), rs.getBigDecimal("avg_spend").setScale(0, RoundingMode.HALF_UP))
        }

    fun getAtRiskPatients(clinicId: UUID, limit: Int = 50): List<AtRiskPatient> =
        jdbc.query("""
            SELECT patient_id, patient_name, phone, email, last_visit, days_inactive, total_visits, churn_risk
            FROM v_at_risk_patients WHERE clinic_id = :clinicId ORDER BY days_inactive DESC LIMIT :limit
        """, MapSqlParameterSource().addValue("clinicId", clinicId).addValue("limit", limit)
        ) { rs, _ ->
            AtRiskPatient(UUID.fromString(rs.getString("patient_id")), rs.getString("patient_name"),
                rs.getString("phone"), rs.getString("email"), rs.getDate("last_visit").toLocalDate(),
                rs.getInt("days_inactive"), rs.getLong("total_visits"), rs.getString("churn_risk"))
        }

    fun getNoShowByDay(clinicId: UUID): List<NoShowByDay> =
        jdbc.query("""
            SELECT EXTRACT(DOW FROM appointment_date)::INT AS dow, TO_CHAR(appointment_date,'Day') AS day_name,
                   COUNT(*) AS total, COUNT(*) FILTER (WHERE status='NO_SHOW') AS no_shows,
                   ROUND(COUNT(*) FILTER (WHERE status='NO_SHOW')::NUMERIC / NULLIF(COUNT(*),0)*100,1) AS rate_pct
            FROM appointments WHERE clinic_id = :clinicId AND appointment_date >= CURRENT_DATE - INTERVAL '90 days'
            AND status NOT IN ('SCHEDULED','CONFIRMED','CHECKED_IN','IN_CONSULTATION')
            GROUP BY EXTRACT(DOW FROM appointment_date), TO_CHAR(appointment_date,'Day') ORDER BY dow
        """, MapSqlParameterSource("clinicId", clinicId)
        ) { rs, _ ->
            NoShowByDay(rs.getInt("dow"), rs.getString("day_name").trim(), rs.getLong("total"),
                rs.getLong("no_shows"), rs.getDouble("rate_pct"))
        }

    @Scheduled(cron = "0 0 */4 * * *") fun refreshViews() {
        try { jdbc.jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_appointment_summary"); log.info("Views refreshed") }
        catch (e: Exception) { log.error("View refresh failed", e) }
    }

    private fun num(v: Any?) = (v as? Number)?.toLong() ?: 0L
    private fun dbl(v: Any?) = (v as? Number)?.toDouble() ?: 0.0
    private fun dec(v: Any?) = (v as? BigDecimal) ?: BigDecimal.ZERO
}
