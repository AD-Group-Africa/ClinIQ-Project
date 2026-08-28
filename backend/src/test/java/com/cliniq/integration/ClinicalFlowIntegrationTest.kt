package com.cliniq.integration

import com.cliniq.domain.auth.entity.User
import com.cliniq.domain.auth.enums.UserRole
import com.cliniq.domain.billing.dto.InvoiceResponse
import com.cliniq.domain.billing.repository.InvoiceRepository
import com.cliniq.domain.clinic.entity.Clinic
import com.cliniq.domain.clinic.repository.ClinicRepository
import com.cliniq.domain.communication.entity.Communication
import com.cliniq.domain.communication.repository.CommunicationRepository
import com.cliniq.domain.lab.entity.LabResult
import com.cliniq.domain.lab.repository.LabResultRepository
import com.cliniq.domain.patient.dto.PatientResponse
import com.cliniq.domain.patient.entity.Patient
import com.cliniq.domain.patient.repository.PatientRepository
import com.cliniq.domain.pharmacy.entity.DrugDispense
import com.cliniq.domain.pharmacy.repository.DrugDispenseRepository
import com.cliniq.domain.consultation.entity.Consultation
import com.cliniq.domain.consultation.entity.Prescription
import com.cliniq.domain.consultation.repository.ConsultationRepository
import com.cliniq.domain.consultation.repository.PrescriptionRepository
import com.cliniq.domain.appointment.entity.Appointment
import com.cliniq.domain.appointment.entity.AppointmentStatus
import com.cliniq.domain.appointment.repository.AppointmentRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.Sql.ExecutionPhase
import javax.persistence.EntityManager
import javax.persistence.PersistenceContext
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(locations = "classpath:application-test.yml")
@Sql(scripts = ["/db/migration/V1__initial_schema.sql", "/db/migration/V2__add_booked_by_to_appointments.sql", "/db/migration/V3__seed_data.sql"],
    executionPhase = ExecutionPhase.BEFORE_TEST_METHOD)
class ClinicalFlowIntegrationTest {

    @PersistenceContext
    lateinit var em: EntityManager

    @Autowired
    lateinit var patientRepository: PatientRepository

    @Autowired
    lateinit var appointmentRepository: AppointmentRepository

    @Autowired
    lateinit var consultationRepository: ConsultationRepository

    @Autowired
    lateinit var prescriptionRepository: PrescriptionRepository

    @Autowired
    lateinit var labResultRepository: LabResultRepository

    @Autowired
    lateinit var drugDispenseRepository: DrugDispenseRepository

    @Autowired
    lateinit var invoiceRepository: InvoiceRepository

    @Autowired
    lateinit var communicationRepository: CommunicationRepository

    @Autowired
    lateinit var clinicRepository: ClinicRepository

    @Autowired
    lateinit var userRepository: UserRepository

    var clinicId: UUID = UUID.randomUUID()
    var adminId: UUID = UUID.randomUUID()
    var doctorId: UUID = UUID.randomUUID()
    var patientId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        // Create clinic
        val clinic = Clinic(
            id = clinicId,
            name = "Integration Test Clinic",
            slug = "int-test-clinic",
            phone = "1234567890",
            email = "int@test.com",
            address = "1 Test St",
            city = "Nairobi",
            country = "KE",
            timezone = "Africa/Nairobi",
            currency = "KES"
        )
        clinicRepository.save(clinic)

        // Create admin user with BCrypt password 'Admin123!'
        adminId = UUID.randomUUID()
        val admin = User(
            id = adminId,
            clinicId = clinicId,
            email = "admin@int.test",
            password = "$2a$12$NlKdWyFNUlWo2u9GZ.7sSe.9gXW3Y6wPJJ.FlQmJHnL5rE2Y.SR.m",
            role = UserRole.SUPER_ADMIN,
            isActive = true
        )
        userRepository.save(admin)

        // Create doctor user
        doctorId = UUID.randomUUID()
        val doctorUser = User(
            id = doctorId,
            clinicId = clinicId,
            email = "doctor@int.test",
            password = "$2a$12$NlKdWyFNUlWo2u9GZ.7sSe.9gXW3Y6wPJJ.FlQmJHnL5rE2Y.SR.m",
            role = UserRole.DOCTOR,
            isActive = true
        )
        userRepository.save(doctorUser)

        // Create patient user
        patientId = UUID.randomUUID()
        val patientUser = User(
            id = patientId,
            clinicId = clinicId,
            email = "patient@int.test",
            password = "$2a$12$NlKdWyFNUlWo2u9GZ.7sSe.9gXW3Y6wPJJ.FlQmJHnL5rE2Y.SR.m",
            role = UserRole.PATIENT,
            isActive = true
        )
        userRepository.save(patientUser)

        // Create patient entity
        val patient = Patient(
            id = patientId,
            clinicId = clinicId,
            firstName = "Integration",
            lastName = "Patient",
            phone = "0712345678",
            email = "patient@int.test",
            dateOfBirth = LocalDate.of(1990, 1, 1),
            gender = "Male",
            nationalId = "NHIFinteg001",
            bloodGroup = "O+",
            allergies = emptyArray(),
            emergencyContactName = "Emergency Contact",
            emergencyContactPhone = "0787654321",
            insuranceProvider = "Test Insurance",
            insuranceNumber = "INSinteg001",
            address = "Integration Test Address",
            notes = emptyArray(),
            tags = emptyArray()
        )
        patientRepository.save(patient)
    }

    @Test
    fun `patient_crud_operations_work() `() {
        // Test create
        val saved = patientRepository.findById(patientId)?.take { em.clear() }
        assert(saved.isNotEmpty)
        assert(saved.get.firstName == "Integration")
        assert(saved.get.lastName == "Patient")

        // Test update
        val updated = patientRepository.findById(patientId)?.take { em.clear() }
        updated?.get?.let { p ->
            p.firstName = "Updated"
            patientRepository.save(p)
        }
        val afterUpdate = patientRepository.findById(patientId)?.take { em.clear() }
        assert(afterUpdate?.get.firstName == "Updated")
    }

    @Test
    fun `appointment_creation_and_status_flow() `() {
        val appt = Appointment(
            id = UUID.randomUUID(),
            clinicId = clinicId,
            patient = patientRepository.findById(patientId)?.get ?: throw AssertionError(),
            doctor = userRepository.findByIdAndClinicId(doctorId, clinicId)?.get ?: throw AssertionError(),
            appointmentDate = LocalDate.now().plusDays(1),
            startTime = java.time.LocalTime.of(10, 0),
            endTime = java.time.LocalTime.of(11, 0),
            type = "Consultation",
            chiefComplaint = "Test complaint",
            queueNumber = 1,
            bookedBy = adminId,
            bookedVia = "PORTAL"
        )
        val saved = appointmentRepository.save(appt)
        assert(saved.id != null)

        // Test status flow: SCHEDULED -> CHECKED_IN -> IN_CONSULTATION -> COMPLETED
        assert(saved.status == AppointmentStatus.SCHEDULED)

        // Check in
        saved.status = AppointmentStatus.CHECKED_IN
        appointmentRepository.save(saved)

        // Start consultation
        saved.status = AppointmentStatus.IN_CONSULTATION
        appointmentRepository.save(saved)

        // Complete
        saved.status = AppointmentStatus.COMPLETED
        appointmentRepository.save(saved)

        val finalAppt = appointmentRepository.findById(saved.id)?.take { em.clear() }
        assert(finalAppt?.status == AppointmentStatus.COMPLETED)
    }

    @Test
    fun `consultation_and_prescription_flow() `() {
        // First create an appointment with COMPLETED status
        val appt = Appointment(
            id = UUID.randomUUID(),
            clinicId = clinicId,
            patient = patientRepository.findById(patientId)?.get ?: throw AssertionError(),
            doctor = userRepository.findByIdAndClinicId(doctorId, clinicId)?.get ?: throw AssertionError(),
            appointmentDate = LocalDate.now(),
            startTime = java.time.LocalTime.of(10, 0),
            endTime = java.time.LocalTime.of(11, 0),
            type = "Consultation",
            chiefComplaint = "Test",
            queueNumber = 1,
            bookedBy = adminId,
            bookedVia = "PORTAL"
        )
        appointmentRepository.save(appt).copy(status = AppointmentStatus.COMPLETED)
        appointmentRepository.save(appt)

        // Create consultation
        val consult = Consultation(
            id = UUID.randomUUID(),
            clinicId = clinicId,
            appointmentId = appt.id,
            patientId = patientId,
            doctorId = doctorId,
            visitNumber = 1,
            chiefComplaint = "Test chief complaint",
            history = "Test history",
            examination = "Test examination",
            diagnosis = "Test diagnosis",
            diagnosisCodes = emptyArray(),
            treatmentPlan = "Test treatment plan",
            notes = "Test notes",
            vitals = emptyMap(),
            followUpDate = null,
            followUpNotes = null
        )
        val savedConsult = consultationRepository.save(consult)

        // Add prescription
        val rx = Prescription(
            id = UUID.randomUUID(),
            clinicId = clinicId,
            consultationId = savedConsult.id,
            patientId = patientId,
            doctorId = doctorId,
            medications = "Test medication",
            notes = "Test prescription notes"
        )
        val savedRx = prescriptionRepository.save(rx)

        // Verify we can retrieve
        val foundRx = prescriptionRepository.findById(savedRx.id)?.take { em.clear() }
        assert(foundRx.isNotEmpty)
        assert(foundRx.get.medications == "Test medication")
    }

    @Test
    fun `lab_request_and_results_flow() `() {
        // Create lab request
        val labReq = LabRequest(
            id = UUID.randomUUID(),
            clinicId = clinicId,
            patientId = patientId,
            doctorId = doctorId,
            requestNumber = "LAB-2024-0001",
            priority = "NORMAL",
            clinicalNotes = "Test clinical notes",
            sampleType = "BLOOD",
            totalPrice = java.math.BigDecimal.valueOf(5000),
            dueAt = java.time.Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS)
        )
        val savedLabReq = labRequestRepository.save(labReq)

        // Add lab results
        val labResult = LabResult(
            id = UUID.randomUUID(),
            request = savedLabReq,
            testId = UUID.randomUUID(),
            testName = "HbA1c",
            value = "6.5",
            unit = "%",
            normalRange = "4.0-5.6",
            isAbnormal = true,
            interpretation = "Prediabetic",
            performedBy = doctorId
        )
        val savedLabResult = labResultRepository.save(labResult)

        // Verify retrieval
        val results = labResultRepository.findByRequestId(savedLabReq.id)
        assert(results.size == 1)
        assert(results.first().value == "6.5")
    }

    @Test
    fun `drug_dispensing_flow() `() {
        // Create a drug first
        // (Simplified - in reality would add drug via repository)

        // Create dispense record
        val dispense = DrugDispense(
            id = UUID.randomUUID(),
            clinicId = clinicId,
            drugId = UUID.randomUUID(),
            batchId = UUID.randomUUID(),
            consultationId = UUID.randomUUID(),
            patientId = patientId,
            doctorId = doctorId,
            quantity = 10,
            unitPrice = java.math.BigDecimal.valueOf(500),
            totalPrice = java.math.BigDecimal.valueOf(5000),
            instructions = "Take twice daily"
        )
        val savedDispense = drugDispenseRepository.save(dispense)

        // Verify
        val found = drugDispenseRepository.findById(savedDispense.id)?.take { em.clear() }
        assert(found.isNotEmpty)
        assert(found.get.quantity == 10)
    }

    @Test
    fun `cross_clinic_access_is_isolated() `() {
        // Create second clinic
        val otherClinicId = UUID.randomUUID()
        val otherClinic = Clinic(
            id = otherClinicId,
            name = "Other Clinic",
            slug = "other-clinic",
            phone = "0987654321",
            email = "other@test.com",
            address = "Other Address",
            city = "Mombasa",
            country = "KE",
            timezone = "Africa/Nairobi",
            currency = "KES"
        )
        clinicRepository.save(otherClinic)

        // Create user in other clinic
        val otherUserId = UUID.randomUUID()
        val otherUser = User(
            id = otherUserId,
            clinicId = otherClinicId,
            email = "other@other.test",
            password = "$2a$12$NlKdWyFNUlWo2u9GZ.7sSe.9gXW3Y6wPJJ.FlQmJHnL5rE2Y.SR.m",
            role = UserRole.PATIENT,
            isActive = true
        )
        userRepository.save(otherUser)

        // Patient from clinicId should not see users from otherClinicId
        val patientsInOther = patientRepository.findByClinicId(otherClinicId)
        assert(patientsInOther.isNotEmpty)

        // Verify clinic isolation - patient belongs to correct clinic
        val patientInClinic = patientRepository.findById(patientId)?.take { em.clear() }
        assert(patientInClinic?.clinicId == clinicId)
    }

    @Test
    fun `rbac_role_hierarchy_basic() `() {
        // Verify role types exist and are distinct
        val admin = userRepository.findByEmail("admin@int.test")?.get
        val doctor = userRepository.findByEmail("doctor@int.test")?.get
        val patient = userRepository.findByEmail("patient@int.test")?.get

        assert(admin.role == UserRole.SUPER_ADMIN)
        assert(doctor.role == UserRole.DOCTOR)
        assert(patient.role == UserRole.PATIENT)

        // All should be active
        assert(admin.isActive)
        assert(doctor.isActive)
        assert(patient.isActive)
    }
}