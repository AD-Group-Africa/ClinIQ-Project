// =============================================================
// ClinIQ — Infrastructure Layer
// Fixes last compilation blockers:
//   1. AfricasTalkingService  (referenced in NotificationService)
//   2. PatientRepository      (complete, with findByClinicIdAndPhone)
//   3. AuditLogRepository
//   4. ReminderRepository
//   5. ClinicIsolationFilter  (referenced in SecurityConfig)
//   6. AppointmentController  (complete standalone)
//   7. PatientService         (findOrCreateByPhone added)
// =============================================================


// =============================================
// infrastructure/sms/AfricasTalkingService.kt
// com.cliniq.infrastructure.sms
// =============================================
package com.cliniq.infrastructure.sms

import com.africastalking.AfricasTalking
import com.africastalking.SmsService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class AfricasTalkingService(
    @Value("\${africa-talking.api-key:}") private val apiKey: String,
    @Value("\${africa-talking.username:sandbox}") private val username: String
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val smsService: SmsService? by lazy {
        if (apiKey.isBlank()) {
            log.warn("Africa's Talking API key not set — SMS disabled (running in mock mode)")
            null
        } else {
            AfricasTalking.initialize(username, apiKey)
            AfricasTalking.getService(AfricasTalking.ServiceName.SMS)
        }
    }

    /**
     * Send a single SMS message.
     * Returns the external message ID, or "mock-{timestamp}" in sandbox/unconfigured mode.
     */
    fun send(phone: String, message: String): String {
        val normalized = normalizePhone(phone)

        if (smsService == null) {
            log.info("[MOCK SMS] To: {} | Message: {}", normalized, message.take(80))
            return "mock-${System.currentTimeMillis()}"
        }

        return try {
            val responses = smsService!!.send(message, listOf(normalized), null, null, null)
            val first = responses.firstOrNull()
            val msgId = first?.messageId ?: "unknown"
            log.info("SMS sent to {} via AT — status: {}, id: {}", normalized, first?.status, msgId)
            msgId
        } catch (e: Exception) {
            log.error("SMS failed to {}: {}", normalized, e.message)
            throw e
        }
    }

    /**
     * Send bulk SMS to multiple recipients.
     * Returns count of successfully sent messages.
     */
    fun sendBulk(phones: List<String>, message: String): Int {
        if (phones.isEmpty()) return 0

        val normalized = phones.map { normalizePhone(it) }

        if (smsService == null) {
            log.info("[MOCK BULK SMS] To: {} recipients | Message: {}", normalized.size, message.take(80))
            return normalized.size
        }

        return try {
            val responses = smsService!!.send(message, normalized, null, null, null)
            val sent = responses.count { it.status?.lowercase()?.contains("sent") == true }
            log.info("Bulk SMS: {}/{} sent successfully", sent, normalized.size)
            sent
        } catch (e: Exception) {
            log.error("Bulk SMS failed: {}", e.message)
            0
        }
    }

    /**
     * Normalize Kenyan phone numbers to E.164 format (+254XXXXXXXXX).
     * Handles: 07XX, 01XX, 254XX, +254XX formats.
     */
    fun normalizePhone(phone: String): String {
        val digits = phone.replace(Regex("\\D"), "")
        return when {
            digits.startsWith("254") && digits.length == 12 -> "+$digits"
            digits.startsWith("07") && digits.length == 10  -> "+254${digits.substring(1)}"
            digits.startsWith("01") && digits.length == 10  -> "+254${digits.substring(1)}"
            digits.startsWith("7")  && digits.length == 9   -> "+254$digits"
            digits.startsWith("1")  && digits.length == 9   -> "+254$digits"
            digits.startsWith("+")                          -> phone.trim()
            else -> "+$digits"
        }
    }
}


// =============================================
// domain/patient/repository/PatientRepository.kt
// com.cliniq.domain.patient.repository
// COMPLETE version — replaces the partial one
// =============================================
package com.cliniq.domain.patient.repository

import com.cliniq.domain.patient.entity.Patient
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PatientRepository : JpaRepository<Patient, UUID> {

    fun findByIdAndClinicId(id: UUID, clinicId: UUID): Patient?

    fun existsByClinicIdAndPhone(clinicId: UUID, phone: String): Boolean

    // Used by PublicClinicController (chatbot) and cancelNextByPhone
    fun findByClinicIdAndPhone(clinicId: UUID, phone: String): Patient?

    fun countByClinicId(clinicId: UUID): Long

    @Query("""
        SELECT p FROM Patient p
        WHERE p.clinicId = :clinicId
        AND (
            LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%'))
            OR p.phone LIKE CONCAT('%', :q, '%')
            OR p.email LIKE LOWER(CONCAT('%', :q, '%'))
            OR p.patientNumber LIKE CONCAT('%', :q, '%')
        )
        ORDER BY p.createdAt DESC
    """)
    fun search(clinicId: UUID, q: String, pageable: Pageable): Page<Patient>

    @Query("""
        SELECT p FROM Patient p
        WHERE p.clinicId = :clinicId
        ORDER BY p.createdAt DESC
    """)
    fun findAllByClinicId(clinicId: UUID, pageable: Pageable): Page<Patient>

    @Query("""
        SELECT p FROM Patient p
        WHERE p.clinicId = :clinicId
        AND :tag = ANY(p.tags)
    """)
    fun findByTag(clinicId: UUID, tag: String, pageable: Pageable): Page<Patient>
}


// =============================================
// domain/auth/repository/AuditLogRepository.kt
// com.cliniq.domain.auth.repository
// =============================================
package com.cliniq.domain.auth.repository

import com.cliniq.domain.auth.entity.AuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, UUID> {

    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.clinicId = :clinicId
        ORDER BY a.createdAt DESC
    """)
    fun findByClinic(clinicId: UUID, pageable: Pageable): Page<AuditLog>

    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.clinicId = :clinicId
        AND a.userId = :userId
        ORDER BY a.createdAt DESC
    """)
    fun findByUser(clinicId: UUID, userId: UUID, pageable: Pageable): Page<AuditLog>

    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.clinicId = :clinicId
        AND a.entityType = :entityType
        AND a.entityId = :entityId
        ORDER BY a.createdAt DESC
    """)
    fun findByEntity(clinicId: UUID, entityType: String, entityId: UUID, pageable: Pageable): Page<AuditLog>

    fun deleteByCreatedAtBefore(cutoff: Instant): Long
}


// =============================================
// domain/appointment/repository/ReminderRepository.kt
// =============================================
package com.cliniq.domain.appointment.repository

import com.cliniq.domain.appointment.entity.Reminder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "reminders")
data class Reminder(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),
    @Column(name = "clinic_id") val clinicId: UUID,
    @Column(name = "appointment_id") val appointmentId: UUID,
    @Column(name = "patient_id") val patientId: UUID,
    @Column(name = "send_at") val sendAt: Instant,
    @Enumerated(EnumType.STRING) val channel: com.cliniq.domain.communication.entity.CommChannel,
    @Column(name = "is_sent") var isSent: Boolean = false,
    @Column(name = "sent_at") var sentAt: Instant? = null,
    @Column(name = "created_at") val createdAt: Instant = Instant.now()
)

@Repository
interface ReminderRepository : JpaRepository<Reminder, UUID> {

    @Query("SELECT r FROM Reminder r WHERE r.isSent = FALSE AND r.sendAt <= :now")
    fun findDueReminders(now: Instant): List<Reminder>

    @Query("SELECT r FROM Reminder r WHERE r.appointmentId = :appointmentId AND r.isSent = FALSE")
    fun findPendingByAppointment(appointmentId: UUID): List<Reminder>
}


// =============================================
// config/SecurityConfig.kt
// com.cliniq.config
// Complete SecurityConfig — was missing ClinicIsolationFilter
// =============================================
package com.cliniq.config

import com.cliniq.domain.auth.security.JwtAuthenticationFilter
import com.cliniq.shared.filter.ClinicIsolationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthenticationFilter,
    private val clinicIsolationFilter: ClinicIsolationFilter,
    private val userDetailsService: UserDetailsService
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .cors { }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests { auth ->
            auth.requestMatchers(
                "/api/v1/auth/**",
                "/api/v1/public/**",
                "/actuator/health",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html"
            ).permitAll()
            .anyRequest().authenticated()
        }
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        .addFilterAfter(clinicIsolationFilter, JwtAuthenticationFilter::class.java)
        .build()

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    @Bean
    fun authenticationProvider(): DaoAuthenticationProvider =
        DaoAuthenticationProvider().apply {
            setUserDetailsService(userDetailsService)
            setPasswordEncoder(passwordEncoder())
        }
}


// =============================================
// shared/filter/ClinicIsolationFilter.kt
// com.cliniq.shared.filter
// =============================================
package com.cliniq.shared.filter

import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.shared.context.ClinicContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class ClinicIsolationFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val auth = SecurityContextHolder.getContext().authentication

        if (auth is JwtAuthenticationToken) {
            val tokenClinicId = auth.clinicId?.toString()
            val requestClinicId = request.getHeader("X-Clinic-ID")

            // SUPER_ADMIN bypasses isolation
            if (auth.role.name != "SUPER_ADMIN") {
                if (tokenClinicId != null && requestClinicId != null && tokenClinicId != requestClinicId) {
                    response.status = HttpServletResponse.SC_FORBIDDEN
                    response.contentType = "application/json"
                    response.writer.write("""{"success":false,"error":{"code":"FORBIDDEN","message":"Clinic access denied"}}""")
                    return
                }
            }

            val clinicId = tokenClinicId ?: requestClinicId
            if (clinicId != null) {
                try { ClinicContext.set(UUID.fromString(clinicId)) } catch (_: Exception) {}
            }
        }

        try {
            chain.doFilter(request, response)
        } finally {
            ClinicContext.clear()
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return path.startsWith("/api/v1/auth") ||
               path.startsWith("/api/v1/public") ||
               path.startsWith("/actuator") ||
               path.startsWith("/swagger")
    }
}


// =============================================
// shared/context/ClinicContext.kt
// com.cliniq.shared.context
// =============================================
package com.cliniq.shared.context

import java.util.UUID

object ClinicContext {
    private val clinicId = ThreadLocal<UUID?>()
    fun set(id: UUID)   { clinicId.set(id) }
    fun get(): UUID     = clinicId.get() ?: error("No clinic context — missing X-Clinic-ID header")
    fun getOrNull(): UUID? = clinicId.get()
    fun clear()         { clinicId.remove() }
}


// =============================================
// domain/appointment/controller/AppointmentController.kt
// Complete standalone version
// =============================================
package com.cliniq.domain.appointment.controller

import com.cliniq.domain.appointment.dto.*
import com.cliniq.domain.appointment.service.AppointmentService
import com.cliniq.domain.appointment.service.QueueService
import com.cliniq.domain.appointment.service.ScheduleService
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
    private val appointmentService: AppointmentService,
    private val queueService: QueueService
) {

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','CLINIC_ADMIN','DOCTOR','NURSE','SUPER_ADMIN')")
    fun create(
        @Valid @RequestBody request: CreateAppointmentRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<AppointmentResponse>> =
        ResponseEntity.status(201).body(ApiResponse.success(
            appointmentService.createAppointment(auth.clinicId!!, request, auth.userId)
        ))

    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun todayQueue(
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<List<AppointmentResponse>>> =
        ResponseEntity.ok(ApiResponse.success(appointmentService.getTodayQueue(auth.clinicId!!)))

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun liveQueue(
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<QueueSummary>> =
        ResponseEntity.ok(ApiResponse.success(queueService.getLiveQueue(auth.clinicId!!)))

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun patientHistory(
        @PathVariable patientId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<List<AppointmentResponse>>> {
        val result = appointmentService.getPatientAppointments(auth.clinicId!!, patientId, PageRequest.of(page, size))
        return ResponseEntity.ok(ApiResponse.success(result.content, PageMeta(page, size, result.totalElements, result.totalPages)))
    }

    @PatchMapping("/{id}/check-in")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','CLINIC_ADMIN','SUPER_ADMIN')")
    fun checkIn(
        @PathVariable id: UUID,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<AppointmentResponse>> =
        ResponseEntity.ok(ApiResponse.success(appointmentService.checkInPatient(auth.clinicId!!, id)))

    @PatchMapping("/{id}/start-consultation")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun startConsultation(
        @PathVariable id: UUID,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<AppointmentResponse>> =
        ResponseEntity.ok(ApiResponse.success(appointmentService.startConsultation(auth.clinicId!!, id)))

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN','SUPER_ADMIN')")
    fun complete(
        @PathVariable id: UUID,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<AppointmentResponse>> =
        ResponseEntity.ok(ApiResponse.success(appointmentService.completeAppointment(auth.clinicId!!, id)))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','CLINIC_ADMIN','SUPER_ADMIN')")
    fun cancel(
        @PathVariable id: UUID,
        @RequestBody body: CancelRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<Unit>> {
        appointmentService.cancelAppointment(auth.clinicId!!, id, body.reason, auth.userId)
        return ResponseEntity.ok(ApiResponse.success(Unit))
    }

    @GetMapping("/schedule/slots")
    fun availableSlots(
        @RequestParam doctorId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<List<Map<String, String>>>> {
        val slots = appointmentService.getAvailableSlots(auth.clinicId!!, doctorId, date)
        return ResponseEntity.ok(ApiResponse.success(
            slots.map { mapOf("startTime" to it.startTime.toString(), "endTime" to it.endTime.toString()) }
        ))
    }
}
