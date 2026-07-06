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
