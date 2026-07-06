package com.cliniq.domain.appointment.repository

import com.cliniq.domain.appointment.entity.Appointment
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

    fun findByIdAndClinicId(id: UUID, clinicId: UUID): Appointment?

    @Query("""
        SELECT a FROM Appointment a WHERE a.clinicId = :clinicId
        AND a.appointmentDate = :date AND a.status NOT IN ('CANCELLED','NO_SHOW')
        ORDER BY a.startTime ASC
    """)
    fun findByClinicAndDate(clinicId: UUID, date: LocalDate): List<Appointment>

    @Query("""
        SELECT a FROM Appointment a WHERE a.clinicId = :clinicId
        AND a.doctor.id = :doctorId AND a.appointmentDate = :date
        AND a.status NOT IN ('CANCELLED','NO_SHOW')
    """)
    fun findByDoctorAndDate(clinicId: UUID, doctorId: UUID, date: LocalDate): List<Appointment>

    @Query("""
        SELECT a FROM Appointment a WHERE a.clinicId = :clinicId
        AND a.patient.id = :patientId ORDER BY a.appointmentDate DESC, a.startTime DESC
    """)
    fun findByPatient(clinicId: UUID, patientId: UUID, pageable: Pageable): Page<Appointment>

    @Query("""
        SELECT a FROM Appointment a WHERE a.reminderSent = FALSE
        AND a.appointmentDate = :tomorrow AND a.status IN ('SCHEDULED','CONFIRMED')
    """)
    fun findPendingReminders(tomorrow: LocalDate): List<Appointment>

    @Query("""
        SELECT COUNT(a) FROM Appointment a WHERE a.clinicId = :clinicId
        AND a.doctor.id = :doctorId AND a.appointmentDate = :date
        AND a.startTime < :endTime AND a.endTime > :startTime
        AND a.status NOT IN ('CANCELLED','NO_SHOW')
    """)
    fun countConflicts(clinicId: UUID, doctorId: UUID, date: LocalDate, startTime: LocalTime, endTime: LocalTime): Long

    @Query("""
        SELECT a FROM Appointment a WHERE a.clinicId = :clinicId
        AND a.status = 'CHECKED_IN' AND a.doctor.id = :doctorId
        ORDER BY a.queueNumber ASC
    """)
    fun findNextInQueue(clinicId: UUID, doctorId: UUID): List<Appointment>

    @Query("""
        SELECT COALESCE(MAX(a.queueNumber), 0) FROM Appointment a
        WHERE a.clinicId = :clinicId AND a.appointmentDate = :date
    """)
    fun findMaxQueueNumber(clinicId: UUID, date: LocalDate): Int
}
