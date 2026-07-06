package com.cliniq.domain.analytics.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class DashboardSummary(
    val todayTotal: Long, val todayCompleted: Long, val todayNoShows: Long,
    val todayRemaining: Long, val todayCheckedIn: Long, val noShowRatePct: Double,
    val monthRevenue: BigDecimal, val monthCollected: BigDecimal, val outstandingBalance: BigDecimal,
    val newPatientsThisMonth: Long, val returningPatientsThisMonth: Long, val retentionRatePct: Double,
    val lowStockAlerts: Int, val pendingLabRequests: Int
)

data class WeeklyTrendPoint(
    val weekStart: LocalDate, val total: Long, val completed: Long,
    val noShows: Long, val completionRatePct: Double
)

data class DoctorUtilizationRow(
    val doctorId: UUID, val doctorName: String, val specialization: String?,
    val consultationsDone: Long, val noShows: Long, val completionRatePct: Double,
    val revenueGenerated: BigDecimal
)

data class PatientSegmentCount(
    val segment: String, val count: Long, val totalSpend: BigDecimal, val avgSpend: BigDecimal
)

data class AtRiskPatient(
    val patientId: UUID, val patientName: String, val phone: String,
    val email: String?, val lastVisit: LocalDate, val daysInactive: Int,
    val totalVisits: Long, val churnRisk: String
)

data class NoShowByDay(
    val dayOfWeek: Int, val dayName: String, val total: Long, val noShows: Long, val ratePct: Double
)

data class RevenueByMethod(
    val method: String, val transactionCount: Long, val totalAmount: BigDecimal, val pct: Double
)
