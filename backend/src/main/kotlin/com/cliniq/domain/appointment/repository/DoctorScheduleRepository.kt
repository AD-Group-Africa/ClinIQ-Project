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

    @Query("SELECT s FROM DoctorSchedule s WHERE s.doctor.id = :doctorId AND s.isActive = TRUE")
    fun findByDoctor(doctorId: UUID): List<DoctorSchedule>
}
