package com.cliniq.domain.appointment.service

import com.cliniq.domain.appointment.dto.TimeSlot
import com.cliniq.domain.appointment.repository.AppointmentRepository
import com.cliniq.domain.appointment.repository.DoctorScheduleRepository
import com.cliniq.domain.appointment.repository.ScheduleOverrideRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ScheduleService(
    private val schedules: DoctorScheduleRepository,
    private val overrides: ScheduleOverrideRepository,
    private val appointments: AppointmentRepository
) {

    /**
     * Returns available time slots for a doctor on a given date,
     * respecting weekly schedule, date overrides, and existing bookings.
     */
    fun getAvailableSlots(clinicId: UUID, doctorId: UUID, date: LocalDate): List<TimeSlot> {
        // Date-specific override takes priority
        val override = overrides.findByDoctorAndDate(doctorId, date)
        if (override != null && !override.isAvailable) return emptyList()

        val dayOfWeek = date.dayOfWeek.value % 7 // Sunday=0 .. Saturday=6 (matches DB convention)
        val schedule = schedules.findByDoctorAndDay(doctorId, dayOfWeek) ?: return emptyList()

        val startTime = override?.startTime ?: schedule.startTime
        val endTime   = override?.endTime   ?: schedule.endTime
        val duration  = schedule.slotDurationMinutes.toLong()

        // Generate all possible slots
        val allSlots = mutableListOf<TimeSlot>()
        var cursor = startTime
        while (cursor.plusMinutes(duration) <= endTime) {
            allSlots.add(TimeSlot(cursor, cursor.plusMinutes(duration)))
            cursor = cursor.plusMinutes(duration)
        }

        // Remove slots that overlap with existing bookings
        val booked = appointments.findByDoctorAndDate(clinicId, doctorId, date)
        return allSlots.filterNot { slot ->
            booked.any { it.startTime < slot.endTime && it.endTime > slot.startTime }
        }.filter { slot ->
            // If date == today, only show future slots
            date != LocalDate.now() || slot.startTime > LocalTime.now()
        }
    }
}
