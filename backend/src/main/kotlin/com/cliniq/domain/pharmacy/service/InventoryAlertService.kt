package com.cliniq.domain.pharmacy.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class InventoryAlertService(
    private val pharmacyService: PharmacyService,
    private val clinicRepository: com.cliniq.domain.clinic.repository.ClinicRepository,
    private val notificationService: com.cliniq.domain.communication.service.NotificationService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Scheduled(cron = "0 0 7 * * *")
    @Transactional(readOnly = true)
    fun sendDailyInventoryAlerts() {
        val clinics = clinicRepository.findAllActive()
        log.info("Running inventory alerts for {} clinics", clinics.size)
        clinics.forEach { clinic ->
            try {
                val summary = pharmacyService.getStockAlerts(clinic.id)
                if (summary.alerts.isNotEmpty()) {
                    log.info("Clinic {}: {} low, {} out, {} expiring", clinic.name, summary.lowStockCount, summary.outOfStockCount, summary.expiringSoonCount)
                    notificationService.sendInventoryAlert(clinic, summary)
                }
            } catch (e: Exception) {
                log.error("Failed inventory alert for clinic {}", clinic.id, e)
            }
        }
    }
}
