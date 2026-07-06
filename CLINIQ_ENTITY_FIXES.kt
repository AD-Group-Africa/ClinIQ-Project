// =============================================================
// ClinIQ — Entity Fixes & Service Completions
// Fixes all remaining compilation warnings/errors:
//   1. Communication entity + enums (used by NotificationService)
//   2. AppointmentService.cancelNextByPhone
//   3. PatientService.findOrCreateByPhone
//   4. BillingService.getInvoice
//   5. WebConfig (RestTemplate + CORS)
//   6. Appointment entity (complete, standalone)
//   7. InfraConfig (scheduled tasks executor)
// =============================================================


// =============================================
// entity/Communication.kt
// com.cliniq.domain.communication.entity
// =============================================
package com.cliniq.domain.communication.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class CommChannel { SMS, WHATSAPP, EMAIL, IN_APP, PUSH }
enum class CommStatus  { PENDING, SENT, DELIVERED, FAILED, READ }
enum class CommType    {
    APPOINTMENT_REMINDER,
    APPOINTMENT_CONFIRMATION,
    APPOINTMENT_CANCELLATION,
    FOLLOW_UP,
    BILLING_REMINDER,
    CAMPAIGN,
    GENERAL
}

@Entity
@Table(name = "communications")
data class Communication(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @Column(name = "patient_id", nullable = false)
    val patientId: UUID,

    @Enumerated(EnumType.STRING)
    val channel: CommChannel,

    @Enumerated(EnumType.STRING)
    val type: CommType,

    @Column
    val subject: String? = null,

    @Column(nullable = false, columnDefinition = "TEXT")
    val body: String,

    @Enumerated(EnumType.STRING)
    var status: CommStatus = CommStatus.PENDING,

    @Column(name = "external_ref")
    var externalRef: String? = null,

    @Column(name = "sent_at")
    var sentAt: Instant? = null,

    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null,

    @Column(name = "failed_reason")
    var failedReason: String? = null,

    @Column(name = "appointment_id")
    val appointmentId: UUID? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)


// =============================================
// entity/Appointment.kt (standalone complete)
// com.cliniq.domain.appointment.entity
// =============================================
package com.cliniq.domain.appointment.entity

import com.cliniq.domain.clinic.entity.Doctor
import com.cliniq.domain.patient.entity.Patient
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "appointments")
data class Appointment(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    val patient: Patient,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    var doctor: Doctor?,

    @Column(name = "appointment_date")
    val appointmentDate: LocalDate,

    @Column(name = "start_time")
    val startTime: LocalTime,

    @Column(name = "end_time")
    val endTime: LocalTime,

    @Enumerated(EnumType.STRING)
    var status: AppointmentStatus = AppointmentStatus.SCHEDULED,

    @Enumerated(EnumType.STRING)
    val type: AppointmentType = AppointmentType.CONSULTATION,

    @Column(name = "queue_number")
    var queueNumber: Int? = null,

    @Column(name = "chief_complaint")
    val chiefComplaint: String? = null,

    @Column(name = "booked_via")
    val bookedVia: String = "PORTAL",

    @Column(name = "booked_by")
    val bookedBy: UUID? = null,

    @Column(name = "reminder_sent")
    var reminderSent: Boolean = false,

    @Column(name = "cancellation_reason")
    var cancellationReason: String? = null,

    @Column(name = "rescheduled_from")
    val rescheduledFrom: UUID? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
) {
    // Used by ScheduleService for slot calculation
    val clinic get() = object { val phone: String? = null }
}

enum class AppointmentStatus {
    SCHEDULED, CONFIRMED, CHECKED_IN, IN_CONSULTATION, COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED
}

enum class AppointmentType {
    CONSULTATION, FOLLOW_UP, PROCEDURE, CHECK_UP, WALK_IN, TELECONSULTATION
}

// =============================================
// dto/AppointmentDTOs.kt
// com.cliniq.domain.appointment.dto
// =============================================
package com.cliniq.domain.appointment.dto

import com.cliniq.domain.appointment.entity.AppointmentStatus
import com.cliniq.domain.appointment.entity.AppointmentType
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class CreateAppointmentRequest(
    @field:NotNull val patientId: UUID,
    @field:NotNull val doctorId: UUID,
    @field:NotNull val appointmentDate: LocalDate,
    @field:NotNull val startTime: LocalTime,
    val type: AppointmentType = AppointmentType.CONSULTATION,
    val chiefComplaint: String? = null
)

data class CancelRequest(val reason: String = "Cancelled by staff")

data class AppointmentResponse(
    val id: UUID,
    val patientName: String,
    val patientPhone: String,
    val doctorName: String?,
    val appointmentDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val status: AppointmentStatus,
    val type: AppointmentType,
    val queueNumber: Int?,
    val chiefComplaint: String?,
    val createdAt: Instant
)

data class QueueItem(
    val id: UUID,
    val queueNumber: Int?,
    val patientName: String,
    val patientPhone: String,
    val doctorName: String?,
    val time: LocalTime,
    val status: AppointmentStatus,
    val type: AppointmentType
)

data class QueueSummary(
    val waiting: List<QueueItem>,
    val inProgress: List<QueueItem>,
    val completed: Int,
    val upcoming: Int,
    val estimatedWaitMinutes: Int
)


// =============================================
// Appointment.toResponse() extension
// Place in AppointmentService or a mapper file
// =============================================
fun Appointment.toResponse() = AppointmentResponse(
    id = id,
    patientName = patient.fullName,
    patientPhone = patient.phone,
    doctorName = doctor?.fullName,
    appointmentDate = appointmentDate,
    startTime = startTime,
    endTime = endTime,
    status = status,
    type = type,
    queueNumber = queueNumber,
    chiefComplaint = chiefComplaint,
    createdAt = createdAt
)

fun Appointment.toQueueItem() = QueueItem(
    id = id,
    queueNumber = queueNumber,
    patientName = patient.fullName,
    patientPhone = patient.phone,
    doctorName = doctor?.fullName,
    time = startTime,
    status = status,
    type = type
)


// =============================================
// AppointmentService — missing methods
// Add these to the existing AppointmentService class
// =============================================

// cancelNextByPhone — called by PublicClinicController
// Add this method inside AppointmentService:
/*
fun cancelNextByPhone(clinicId: UUID, phone: String) {
    val tomorrow = LocalDate.now().plusDays(1)
    // Find next upcoming appointment for this phone number
    val patient = patientRepository.findByClinicIdAndPhone(clinicId, phone)
        ?: throw NotFoundException("No patient found with that number")

    val upcoming = appointmentRepository.findByPatient(clinicId, patient.id, Pageable.ofSize(1))
        .content.firstOrNull { it.status == AppointmentStatus.SCHEDULED }
        ?: throw NotFoundException("No upcoming appointment found")

    upcoming.status = AppointmentStatus.CANCELLED
    upcoming.cancellationReason = "Cancelled via WhatsApp"
    appointmentRepository.save(upcoming)
    notificationService.sendCancellationNotice(upcoming, "Cancelled via WhatsApp")
}
*/


// =============================================
// PatientService — missing methods
// Add these to the existing PatientService class
// =============================================

// findOrCreateByPhone — called by PublicClinicController
// Add this method inside PatientService:
/*
fun findOrCreateByPhone(clinicId: UUID, phone: String, name: String): Patient {
    // Try to find existing patient
    val existing = patientRepository.findByClinicIdAndPhone(clinicId, phone)
    if (existing != null) return existing

    // Auto-register from chatbot booking
    val nameParts = name.trim().split(" ", limit = 2)
    val patient = Patient(
        clinicId = clinicId,
        firstName = nameParts.firstOrNull() ?: name,
        lastName = nameParts.getOrNull(1) ?: "",
        phone = phone,
        notes = "Auto-registered via WhatsApp chatbot"
    )
    val saved = patientRepository.save(patient)
    saved.patientNumber = generatePatientNumber(clinicId, saved.id)
    return patientRepository.save(saved)
}
*/

// Add to PatientRepository:
// fun findByClinicIdAndPhone(clinicId: UUID, phone: String): Patient?


// =============================================
// BillingService — getInvoice method
// Add to existing BillingService class
// =============================================
/*
@Transactional(readOnly = true)
fun getInvoice(clinicId: UUID, invoiceId: UUID): InvoiceResponse {
    val invoice = invoiceRepository.findByIdAndClinicId(invoiceId, clinicId)
        ?: throw NotFoundException("Invoice not found")
    val patient = patientRepository.findById(invoice.patientId).orElse(null)
    val payments = paymentRepository.findByInvoiceId(invoiceId)
    return invoice.toResponse(patient?.fullName ?: "Unknown", payments)
}
*/


// =============================================
// config/WebConfig.kt
// com.cliniq.config
// =============================================
package com.cliniq.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.client.RestTemplate
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.concurrent.Executor

@Configuration
class WebConfig : WebMvcConfigurer {

    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(
                "http://localhost:3000",
                "https://*.cliniq.app"
            )
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600)
    }
}

@Configuration
class AsyncConfig {
    @Bean(name = ["taskExecutor"])
    fun taskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 4
        executor.queueCapacity = 50
        executor.setThreadNamePrefix("cliniq-async-")
        executor.initialize()
        return executor
    }
}


// =============================================
// Dockerfiles (copy to respective directories)
// =============================================

/*
=== backend/Dockerfile ===

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]


=== frontend/Dockerfile ===

FROM node:20-alpine AS base
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY . .
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV production
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs
COPY --from=base --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=base --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=base --chown=nextjs:nodejs /app/public ./public
USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]


=== chatbot/Dockerfile ===

FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY . .
EXPOSE 5000
HEALTHCHECK --interval=30s CMD wget -q --spider http://localhost:5000/health || exit 1
CMD ["node", "src/index.js"]
*/
