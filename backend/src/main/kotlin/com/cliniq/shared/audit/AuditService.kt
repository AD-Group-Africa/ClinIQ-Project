package com.cliniq.shared.audit

import com.cliniq.domain.auth.repository.AuditLogRepository
import com.cliniq.domain.auth.entity.AuditLog
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AuditService(private val repo: AuditLogRepository) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun log(
        clinicId:   UUID,
        userId:     UUID?   = null,
        action:     String,
        entityType: String? = null,
        entityId:   UUID?   = null,
        oldValues:  Map<String, Any?>? = null,
        newValues:  Map<String, Any?>? = null,
        ipAddress:  String? = null
    ) {
        try {
            repo.save(AuditLog(
                clinicId = clinicId, userId = userId, action = action,
                entityType = entityType, entityId = entityId,
                oldValues = oldValues, newValues = newValues, ipAddress = ipAddress
            ))
        } catch (e: Exception) {
            log.warn("Audit write failed (non-critical): {}", e.message)
        }
        log.info("AUDIT clinic={} user={} action={} entity={}:{}", clinicId, userId, action, entityType, entityId)
    }
}
