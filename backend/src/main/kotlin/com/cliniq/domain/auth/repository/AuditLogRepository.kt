package com.cliniq.domain.auth.repository

import com.cliniq.domain.auth.entity.AuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, UUID> {

    @Query("SELECT a FROM AuditLog a WHERE a.clinicId = :clinicId ORDER BY a.createdAt DESC")
    fun findByClinic(clinicId: UUID, pageable: Pageable): Page<AuditLog>

    @Query("SELECT a FROM AuditLog a WHERE a.clinicId = :clinicId AND a.entityType = :type AND a.entityId = :id ORDER BY a.createdAt DESC")
    fun findByEntity(clinicId: UUID, type: String, id: UUID, pageable: Pageable): Page<AuditLog>
}
