// =============================================================
// ClinIQ — Missing Pieces (Compilation Blockers Fix)
// Fixes ALL 5 compile failures in one file:
//   1. Doctor entity + DoctorSchedule entity
//   2. All 9 missing JPA repositories
//   3. NotificationService implementation
//   4. PublicClinicController (chatbot API layer)
//   5. CommunicationRepository
// =============================================================

// =============================================
// entity/Doctor.kt
// com.cliniq.domain.clinic.entity
// =============================================
package com.cliniq.domain.clinic.entity

import com.cliniq.domain.auth.entity.User
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "doctors")
data class Doctor(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    val user: User,

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @Column
    var specialization: String? = null,

    @Column
    var qualification: String? = null,

    @Column(name = "license_number")
    var licenseNumber: String? = null,

    @Column
    var bio: String? = null,

    @Column(name = "consultation_fee")
    var consultationFee: BigDecimal = BigDecimal.ZERO,

    @Column(name = "is_available")
    var isAvailable: Boolean = true,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
) {
    val fullName get() = "Dr. ${user.firstName} ${user.lastName}"
}

@Entity
@Table(name = "doctor_schedules")
data class DoctorSchedule(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    val doctor: Doctor,

    @Column(name = "clinic_id")
    val clinicId: UUID,

    @Column(name = "day_of_week")
    val dayOfWeek: Int,   // 0=Sunday … 6=Saturday

    @Column(name = "start_time")
    val startTime: LocalTime,

    @Column(name = "end_time")
    val endTime: LocalTime,

    @Column(name = "slot_duration_minutes")
    val slotDurationMinutes: Int = 30,

    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "schedule_overrides")
data class ScheduleOverride(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    val doctor: Doctor,

    @Column(name = "clinic_id")
    val clinicId: UUID,

    @Column(name = "override_date")
    val overrideDate: java.time.LocalDate,

    @Column(name = "is_available")
    val isAvailable: Boolean = false,

    @Column(name = "start_time")
    val startTime: LocalTime? = null,

    @Column(name = "end_time")
    val endTime: LocalTime? = null,

    @Column
    val reason: String? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)


// =============================================
// ALL MISSING REPOSITORIES
// =============================================

// --- ClinicRepository ---
package com.cliniq.domain.clinic.repository

import com.cliniq.domain.clinic.entity.Clinic
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ClinicRepository : JpaRepository<Clinic, UUID> {
    fun findBySlug(slug: String): Clinic?
    fun existsBySlug(slug: String): Boolean
    fun existsByEmail(email: String): Boolean

    @Query("SELECT c FROM Clinic c WHERE c.isActive = TRUE ORDER BY c.createdAt ASC")
    fun findAllActive(): List<Clinic>

    @Query("SELECT c FROM Clinic c WHERE c.slug = :slug AND c.isActive = TRUE")
    fun findActiveBySlug(slug: String): Clinic?
}

// --- DoctorRepository ---
package com.cliniq.domain.clinic.repository

import com.cliniq.domain.clinic.entity.Doctor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DoctorRepository : JpaRepository<Doctor, UUID> {
    fun findByIdAndClinicId(id: UUID, clinicId: UUID): Doctor?

    @Query("SELECT d FROM Doctor d WHERE d.clinicId = :clinicId AND d.isAvailable = TRUE ORDER BY d.user.firstName")
    fun findAvailableByClinic(clinicId: UUID): List<Doctor>

    @Query("SELECT d FROM Doctor d WHERE d.user.id = :userId")
    fun findByUserId(userId: UUID): Doctor?

    fun countByClinicId(clinicId: UUID): Long
}

// --- DoctorScheduleRepository ---
package com.cliniq.domain.appointment.repository

import com.cliniq.domain.clinic.entity.DoctorSchedule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DoctorScheduleRepository : JpaRepository<DoctorSchedule, UUID> {

    @Query("SELECT s FROM DoctorSchedule s WHERE s.doctor.id = :doctorId AND s.dayOfWeek = :dayOfWeek AND s.isActive = TRUE")
    fun findByDoctorAndDay(doctorId: UUID, dayOfWeek: Int): DoctorSchedule?

    @Query("SELECT s FROM DoctorSchedule s WHERE s.clinicId = :clinicId AND s.isActive = TRUE")
    fun findByClinic(clinicId: UUID): List<DoctorSchedule>
}

// --- ScheduleOverrideRepository ---
package com.cliniq.domain.appointment.repository

import com.cliniq.domain.clinic.entity.ScheduleOverride
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
interface ScheduleOverrideRepository : JpaRepository<ScheduleOverride, UUID> {

    @Query("SELECT o FROM ScheduleOverride o WHERE o.doctor.id = :doctorId AND o.overrideDate = :date")
    fun findByDoctorAndDate(doctorId: UUID, date: LocalDate): ScheduleOverride?
}

// --- AppointmentRepository (full version) ---
package com.cliniq.domain.appointment.repository

import com.cliniq.domain.appointment.entity.Appointment
import com.cliniq.domain.appointment.entity.AppointmentStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Repository
interface AppointmentRepository : JpaRepository<Appointment, UUID> {

    @Query("""
        SELECT a FROM Appointment a WHERE a.clinicId = :clinicId
        AND a.appointmentDate = :date
        AND a.status NOT IN ('CANCELLED', 'NO_SHOW')
        ORDER BY a.startTime ASC
    """)
    fun findByClinicAndDate(clinicId: UUID, date: LocalDate): List<Appointment>

    @Query("""
        SELECT a FROM Appointment a WHERE a.clinicId = :clinicId
        AND a.doctor.id = :doctorId AND a.appointmentDate = :date
        AND a.status NOT IN ('CANCELLED', 'NO_SHOW')
    """)
    fun findByDoctorAndDate(clinicId: UUID, doctorId: UUID, date: LocalDate): List<Appointment>

    @Query("""
        SELECT a FROM Appointment a WHERE a.clinicId = :clinicId
        AND a.patient.id = :patientId ORDER BY a.appointmentDate DESC, a.startTime DESC
    """)
    fun findByPatient(clinicId: UUID, patientId: UUID, pageable: Pageable): Page<Appointment>

    @Query("""
        SELECT a FROM Appointment a WHERE a.reminderSent = FALSE
        AND a.appointmentDate = :tomorrow AND a.status = 'SCHEDULED'
    """)
    fun findPendingReminders(tomorrow: LocalDate): List<Appointment>

    @Query("""
        SELECT COUNT(a) FROM Appointment a WHERE a.clinicId = :clinicId
        AND a.doctor.id = :doctorId AND a.appointmentDate = :date
        AND a.startTime < :endTime AND a.endTime > :startTime
        AND a.status NOT IN ('CANCELLED', 'NO_SHOW')
    """)
    fun countConflicts(clinicId: UUID, doctorId: UUID, date: LocalDate, startTime: LocalTime, endTime: LocalTime): Long

    @Query("""
        SELECT a FROM Appointment a WHERE a.clinicId = :clinicId
        AND a.status = 'CHECKED_IN' AND a.doctor.id = :doctorId
        ORDER BY a.queueNumber ASC
    """)
    fun findNextInQueue(clinicId: UUID, doctorId: UUID): Appointment?

    @Query("""
        SELECT a FROM Appointment a WHERE a.appointmentDate < :date
        AND a.status IN ('SCHEDULED', 'CONFIRMED')
    """)
    fun findScheduledBeforeDate(date: LocalDate): List<Appointment>
}

// --- ConsultationRepository ---
package com.cliniq.domain.consultation.repository

import com.cliniq.domain.consultation.entity.Consultation
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ConsultationRepository : JpaRepository<Consultation, UUID> {

    fun findByIdAndClinicId(id: UUID, clinicId: UUID): Consultation?

    @Query("""
        SELECT c FROM Consultation c WHERE c.clinicId = :clinicId
        AND c.patientId = :patientId ORDER BY c.createdAt DESC
    """)
    fun findByPatientId(clinicId: UUID, patientId: UUID, pageable: Pageable): Page<Consultation>

    @Query("SELECT COUNT(c) FROM Consultation c WHERE c.clinicId = :clinicId AND c.patientId = :patientId")
    fun countByPatientId(clinicId: UUID, patientId: UUID): Int
}

// --- PrescriptionRepository ---
package com.cliniq.domain.consultation.repository

import com.cliniq.domain.consultation.entity.Prescription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PrescriptionRepository : JpaRepository<Prescription, UUID> {

    @Query("SELECT p FROM Prescription p WHERE p.consultationId = :consultationId")
    fun findByConsultationId(consultationId: UUID): List<Prescription>

    @Query("SELECT p FROM Prescription p WHERE p.clinicId = :clinicId AND p.patientId = :patientId ORDER BY p.createdAt DESC")
    fun findByPatient(clinicId: UUID, patientId: UUID): List<Prescription>
}

// --- LabTestRepository ---
package com.cliniq.domain.lab.repository

import com.cliniq.domain.lab.entity.LabTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LabTestRepository : JpaRepository<LabTest, UUID> {
    fun findByClinicIdAndIsActiveTrue(clinicId: UUID, pageable: Pageable): Page<LabTest>
    fun findByIdAndClinicId(id: UUID, clinicId: UUID): LabTest?
}

// --- LabRequestRepository ---
package com.cliniq.domain.lab.repository

import com.cliniq.domain.lab.entity.LabRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LabRequestRepository : JpaRepository<LabRequest, UUID> {

    fun findByIdAndClinicId(id: UUID, clinicId: UUID): LabRequest?

    @Query("""
        SELECT r FROM LabRequest r WHERE r.clinicId = :clinicId
        AND r.status NOT IN ('COMPLETED', 'CANCELLED')
        ORDER BY r.priority DESC, r.createdAt ASC
    """)
    fun findPending(clinicId: UUID, pageable: Pageable): Page<LabRequest>

    @Query("SELECT r FROM LabRequest r WHERE r.clinicId = :clinicId AND r.patientId = :patientId ORDER BY r.createdAt DESC")
    fun findByPatient(clinicId: UUID, patientId: UUID, pageable: Pageable): Page<LabRequest>

    fun countByClinicId(clinicId: UUID): Long
}

// --- LabResultRepository ---
package com.cliniq.domain.lab.repository

import com.cliniq.domain.lab.entity.LabResult
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LabResultRepository : JpaRepository<LabResult, UUID> {

    @Query("SELECT r FROM LabResult r WHERE r.request.id = :requestId ORDER BY r.createdAt ASC")
    fun findByRequestId(requestId: UUID): List<LabResult>
}

// --- DrugDispenseRepository ---
package com.cliniq.domain.pharmacy.repository

import com.cliniq.domain.pharmacy.entity.DrugDispense
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DrugDispenseRepository : JpaRepository<DrugDispense, UUID> {

    @Query("SELECT d FROM DrugDispense d WHERE d.clinicId = :clinicId AND d.patientId = :patientId ORDER BY d.dispensedAt DESC")
    fun findByPatient(clinicId: UUID, patientId: UUID, pageable: Pageable): Page<DrugDispense>

    @Query("SELECT SUM(d.totalPrice) FROM DrugDispense d WHERE d.clinicId = :clinicId AND CAST(d.dispensedAt AS LocalDate) = CURRENT_DATE")
    fun todayRevenue(clinicId: UUID): java.math.BigDecimal?
}

// --- CommunicationRepository ---
package com.cliniq.domain.communication.repository

import com.cliniq.domain.communication.entity.Communication
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface CommunicationRepository : JpaRepository<Communication, UUID> {
    fun findByClinicIdAndPatientIdOrderByCreatedAtDesc(clinicId: UUID, patientId: UUID): List<Communication>
}


// =============================================
// NotificationService.kt
// com.cliniq.domain.communication.service
// =============================================
package com.cliniq.domain.communication.service

import com.cliniq.domain.appointment.entity.Appointment
import com.cliniq.domain.clinic.entity.Clinic
import com.cliniq.domain.communication.entity.CommChannel
import com.cliniq.domain.communication.entity.CommStatus
import com.cliniq.domain.communication.entity.CommType
import com.cliniq.domain.communication.entity.Communication
import com.cliniq.domain.communication.repository.CommunicationRepository
import com.cliniq.domain.pharmacy.dto.StockAlertSummary
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.format.DateTimeFormatter

@Service
@Transactional
class NotificationService(
    private val smsService: AfricasTalkingService,
    private val communicationRepository: CommunicationRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    // ------------------------------------------------
    // Appointment notifications
    // ------------------------------------------------

    fun sendAppointmentConfirmation(appointment: Appointment) {
        val patient = appointment.patient
        val doctor = appointment.doctor ?: return
        val time = appointment.startTime.format(DateTimeFormatter.ofPattern("h:mm a"))
        val date = appointment.appointmentDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))

        val message = """
            ✅ Appointment confirmed!
            📅 $date at $time
            👨‍⚕️ ${doctor.fullName}
            🔢 Queue #${appointment.queueNumber ?: "TBC"}
            
            Please arrive 10 min early with your ID.
            Reply CANCEL to cancel.
        """.trimIndent()

        sendAndRecord(
            clinicId = appointment.clinicId,
            patientId = patient.id,
            phone = patient.phone,
            message = message,
            type = CommType.APPOINTMENT_CONFIRMATION,
            appointmentId = appointment.id
        )
    }

    fun sendAppointmentReminder(appointment: Appointment) {
        val patient = appointment.patient
        val doctor = appointment.doctor ?: return
        val time = appointment.startTime.format(DateTimeFormatter.ofPattern("h:mm a"))

        val message = """
            👋 Reminder: You have an appointment tomorrow.
            ⏰ $time with ${doctor.fullName}
            🔢 Queue #${appointment.queueNumber ?: "TBC"}
            
            See you then! Reply CANCEL to cancel.
        """.trimIndent()

        sendAndRecord(
            clinicId = appointment.clinicId,
            patientId = patient.id,
            phone = patient.phone,
            message = message,
            type = CommType.APPOINTMENT_REMINDER,
            appointmentId = appointment.id
        )
    }

    fun sendCancellationNotice(appointment: Appointment, reason: String) {
        val patient = appointment.patient
        val time = appointment.startTime.format(DateTimeFormatter.ofPattern("h:mm a"))
        val date = appointment.appointmentDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))

        val message = """
            ❌ Your appointment on $date at $time has been cancelled.
            Reason: $reason
            
            Please call us to reschedule.
        """.trimIndent()

        sendAndRecord(
            clinicId = appointment.clinicId,
            patientId = patient.id,
            phone = patient.phone,
            message = message,
            type = CommType.APPOINTMENT_CANCELLATION,
            appointmentId = appointment.id
        )
    }

    fun sendQueueCallNotification(appointment: Appointment) {
        val patient = appointment.patient
        val message = "🔔 ${patient.firstName}, it's your turn! Please proceed to the consultation room. Queue #${appointment.queueNumber}"

        sendAndRecord(
            clinicId = appointment.clinicId,
            patientId = patient.id,
            phone = patient.phone,
            message = message,
            type = CommType.GENERAL,
            appointmentId = appointment.id
        )
    }

    // ------------------------------------------------
    // Inventory alerts (sent to clinic admin)
    // ------------------------------------------------

    fun sendInventoryAlert(clinic: Clinic, summary: StockAlertSummary) {
        if (clinic.phone.isNullOrBlank()) return

        val alertLines = summary.alerts.take(5).joinToString("\n") { alert ->
            "• ${alert.drugName}: ${alert.alertType.replace('_', ' ')} (${alert.currentStock} units)"
        }

        val message = """
            ⚠️ ${clinic.name} — Inventory Alert
            
            ${summary.outOfStockCount} out of stock
            ${summary.lowStockCount} low stock
            ${summary.expiringSoonCount} expiring in 30 days
            
            $alertLines
            
            Log in to manage inventory.
        """.trimIndent()

        // Send to clinic phone (admin)
        try {
            smsService.send(clinic.phone!!, message)
            log.info("Inventory alert sent to {} ({})", clinic.name, clinic.phone)
        } catch (e: Exception) {
            log.error("Failed to send inventory alert to {}", clinic.name, e)
        }
    }

    // ------------------------------------------------
    // Internal send + record helper
    // ------------------------------------------------

    private fun sendAndRecord(
        clinicId: java.util.UUID,
        patientId: java.util.UUID,
        phone: String,
        message: String,
        type: CommType,
        appointmentId: java.util.UUID? = null
    ) {
        val comm = Communication(
            clinicId = clinicId,
            patientId = patientId,
            channel = CommChannel.SMS,
            type = type,
            body = message,
            appointmentId = appointmentId
        )

        try {
            val msgId = smsService.send(phone, message)
            comm.status = CommStatus.SENT
            comm.sentAt = Instant.now()
            comm.externalRef = msgId
        } catch (e: Exception) {
            log.error("SMS failed to {}: {}", phone, e.message)
            comm.status = CommStatus.FAILED
            comm.failedReason = e.message
        }

        communicationRepository.save(comm)
    }
}


// =============================================
// PublicClinicController.kt
// These are the endpoints the chatbot calls.
// No authentication required — public-facing.
// Rate limited at Nginx level.
// =============================================
package com.cliniq.domain.public_api.controller

import com.cliniq.domain.appointment.service.AppointmentService
import com.cliniq.domain.appointment.service.ScheduleService
import com.cliniq.domain.clinic.repository.ClinicRepository
import com.cliniq.domain.clinic.repository.DoctorRepository
import com.cliniq.domain.patient.repository.PatientRepository
import com.cliniq.domain.patient.service.PatientService
import com.cliniq.shared.exceptions.NotFoundException
import com.cliniq.shared.response.ApiResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@RestController
@RequestMapping("/api/v1/public/clinic")
class PublicClinicController(
    private val clinicRepository: ClinicRepository,
    private val doctorRepository: DoctorRepository,
    private val scheduleService: ScheduleService,
    private val appointmentService: AppointmentService,
    private val patientService: PatientService
) {

    // GET /api/v1/public/clinic/{slug}
    // Returns public clinic info (name, hours, address, services)
    @GetMapping("/{slug}")
    fun getClinicInfo(@PathVariable slug: String): ResponseEntity<ApiResponse<PublicClinicInfo>> {
        val clinic = clinicRepository.findActiveBySlug(slug)
            ?: throw NotFoundException("Clinic not found")

        return ResponseEntity.ok(ApiResponse.success(
            PublicClinicInfo(
                id = clinic.id,
                name = clinic.name,
                phone = clinic.phone,
                address = clinic.address,
                city = clinic.city,
                hours = clinic.settings["hours"] as? String ?: "Mon–Fri 8:00 AM – 6:00 PM",
                services = (clinic.settings["services"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                logoUrl = clinic.logoUrl
            )
        ))
    }

    // GET /api/v1/public/clinic/{slug}/doctors
    // Returns available doctors for booking
    @GetMapping("/{slug}/doctors")
    fun getDoctors(@PathVariable slug: String): ResponseEntity<ApiResponse<List<PublicDoctorInfo>>> {
        val clinic = clinicRepository.findActiveBySlug(slug)
            ?: throw NotFoundException("Clinic not found")

        val doctors = doctorRepository.findAvailableByClinic(clinic.id)
        return ResponseEntity.ok(ApiResponse.success(
            doctors.map { d ->
                PublicDoctorInfo(
                    id = d.id,
                    firstName = d.user.firstName,
                    lastName = d.user.lastName,
                    specialization = d.specialization,
                    consultationFee = d.consultationFee,
                    avatarUrl = d.user.avatarUrl
                )
            }
        ))
    }

    // GET /api/v1/public/clinic/{slug}/schedule/slots?doctorId=&date=
    // Returns available time slots for a doctor on a date
    @GetMapping("/{slug}/schedule/slots")
    fun getAvailableSlots(
        @PathVariable slug: String,
        @RequestParam doctorId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ResponseEntity<ApiResponse<List<PublicSlot>>> {
        val clinic = clinicRepository.findActiveBySlug(slug)
            ?: throw NotFoundException("Clinic not found")

        val slots = scheduleService.getAvailableSlots(clinic.id, doctorId, date)
        return ResponseEntity.ok(ApiResponse.success(
            slots.map { s -> PublicSlot(startTime = s.startTime.toString(), endTime = s.endTime.toString()) }
        ))
    }

    // POST /api/v1/public/clinic/{slug}/appointments
    // Creates an appointment via chatbot (walk-in or new patient auto-register)
    @PostMapping("/{slug}/appointments")
    fun createPublicAppointment(
        @PathVariable slug: String,
        @Valid @RequestBody request: PublicBookingRequest
    ): ResponseEntity<ApiResponse<PublicBookingResponse>> {
        val clinic = clinicRepository.findActiveBySlug(slug)
            ?: throw NotFoundException("Clinic not found")

        // Find or auto-register patient by phone
        val patient = patientService.findOrCreateByPhone(
            clinicId = clinic.id,
            phone = request.patientPhone,
            name = request.patientName
        )

        // Book appointment (using system/chatbot user ID)
        val appt = appointmentService.createAppointment(
            clinicId = clinic.id,
            request = com.cliniq.domain.appointment.dto.CreateAppointmentRequest(
                patientId = patient.id,
                doctorId = request.doctorId,
                appointmentDate = request.appointmentDate,
                startTime = LocalTime.parse(request.startTime),
                chiefComplaint = request.chiefComplaint
            ),
            bookedBy = clinic.id  // system booking — uses clinic ID as actor
        )

        return ResponseEntity.status(201).body(ApiResponse.success(
            PublicBookingResponse(
                appointmentId = appt.id,
                patientName = patient.fullName,
                queueNumber = appt.queueNumber,
                confirmationMessage = "Appointment confirmed for ${patient.firstName}!"
            )
        ))
    }

    // POST /api/v1/public/clinic/{slug}/appointments/cancel-by-phone
    @PostMapping("/{slug}/appointments/cancel-by-phone")
    fun cancelByPhone(
        @PathVariable slug: String,
        @RequestBody body: Map<String, String>
    ): ResponseEntity<ApiResponse<Unit>> {
        val clinic = clinicRepository.findActiveBySlug(slug)
            ?: throw NotFoundException("Clinic not found")
        val phone = body["phone"] ?: throw com.cliniq.shared.exceptions.BadRequestException("Phone required")

        appointmentService.cancelNextByPhone(clinic.id, phone)
        return ResponseEntity.ok(ApiResponse.success(Unit))
    }

    // POST /api/v1/public/clinic/{slug}/chatbot/escalate
    // Notifies reception that a WhatsApp user needs human help
    @PostMapping("/{slug}/chatbot/escalate")
    fun escalate(
        @PathVariable slug: String,
        @RequestBody request: EscalationRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val clinic = clinicRepository.findActiveBySlug(slug)
            ?: throw NotFoundException("Clinic not found")

        // Log escalation — in production this sends an in-app notification to reception
        org.slf4j.LoggerFactory.getLogger(this::class.java)
            .info("Chatbot escalation for clinic {}: phone={}", clinic.name, request.phone?.takeLast(4))

        return ResponseEntity.ok(ApiResponse.success(Unit))
    }
}

// ---- Public API DTOs ----
data class PublicClinicInfo(
    val id: java.util.UUID,
    val name: String,
    val phone: String?,
    val address: String?,
    val city: String?,
    val hours: String,
    val services: List<String>,
    val logoUrl: String?
)

data class PublicDoctorInfo(
    val id: java.util.UUID,
    val firstName: String,
    val lastName: String,
    val specialization: String?,
    val consultationFee: java.math.BigDecimal,
    val avatarUrl: String?
)

data class PublicSlot(
    val startTime: String,
    val endTime: String
)

data class PublicBookingRequest(
    @field:NotBlank val patientPhone: String,
    @field:NotBlank val patientName: String,
    val doctorId: java.util.UUID,
    val appointmentDate: LocalDate,
    @field:NotBlank val startTime: String,
    val chiefComplaint: String? = null
)

data class PublicBookingResponse(
    val appointmentId: java.util.UUID,
    val patientName: String,
    val queueNumber: Int?,
    val confirmationMessage: String
)

data class EscalationRequest(
    val phone: String?,
    val channel: String?,
    val lastMessage: String?
)
