
package com.cliniq.domain.clinic.service

import com.cliniq.domain.clinic.dto.DoctorListResponse
import com.cliniq.domain.clinic.repository.DoctorRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DoctorService(
    private val doctors: DoctorRepository
) {
    @Transactional(readOnly = true)
    fun getAvailableDoctors(clinicId: UUID): List<DoctorListResponse> =
        doctors.findAvailableByClinic(clinicId)
            .filter { it.user.isActive }
            .map { doctor ->
                DoctorListResponse(
                    id = doctor.id,
                    fullName = doctor.fullName,
                    specialization = doctor.specialization,
                    qualification = doctor.qualification
                )
            }
}
