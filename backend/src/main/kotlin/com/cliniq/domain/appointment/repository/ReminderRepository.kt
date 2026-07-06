package com.cliniq.domain.appointment.repository

import com.cliniq.domain.appointment.entity.Reminder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface ReminderRepository : JpaRepository<Reminder, UUID> {

    @Query("SELECT r FROM Reminder r WHERE r.isSent = FALSE AND r.sendAt <= :now")
    fun findDueReminders(now: Instant): List<Reminder>

    @Query("SELECT r FROM Reminder r WHERE r.appointmentId = :appointmentId AND r.isSent = FALSE")
    fun findPendingByAppointment(appointmentId: UUID): List<Reminder>
}
