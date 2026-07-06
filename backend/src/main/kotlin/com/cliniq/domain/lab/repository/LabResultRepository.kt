package com.cliniq.domain.lab.repository

import com.cliniq.domain.lab.entity.LabResult
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LabResultRepository : JpaRepository<LabResult, UUID> {
    fun findByRequestId(requestId: UUID): List<LabResult>
}
