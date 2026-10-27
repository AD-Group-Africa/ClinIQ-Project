
package com.cliniq.domain.clinic.dto

import java.util.UUID

data class DoctorListResponse(
    val id: UUID,
    val fullName: String,
    val specialization: String?,
    val qualification: String?
)
