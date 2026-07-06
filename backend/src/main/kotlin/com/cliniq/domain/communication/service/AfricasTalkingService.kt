package com.cliniq.domain.communication.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AfricasTalkingService {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun send(phone: String, message: String) {
        // TODO: Integrate Africa's Talking API
        log.info("SMS sent to {}: {}", phone.takeLast(4), message)
    }
}
