package com.cliniq.config

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration
class ProductionConfigValidator(
    private val env: Environment,
    @Value("\${jwt.secret}") private val jwtSecret: String,
    @Value("\${mpesa.consumer-key:}") private val mpesaKey: String,
    @Value("\${mpesa.consumer-secret:}") private val mpesaSecret: String,
    @Value("\${mpesa.passkey:}") private val mpesaPasskey: String,
    @Value("\${mpesa.callback-secret:}") private val mpesaCallbackSecret: String,
    @Value("\${africa-talking.api-key:}") private val atApiKey: String,
    @Value("\${spring.datasource.url:}") private val dbUrl: String,
    @Value("\${spring.datasource.password:}") private val dbPassword: String
) {
    @PostConstruct
    fun validate() {
        if (!isProd()) return
        val missing = mutableListOf<String>()
        if (jwtSecret.length < 32 || isDefaultJwt(jwtSecret)) {
            missing.add("JWT_SECRET (must be >=32 chars, non-default)")
        }
        if (dbUrl.isBlank() || dbUrl.contains("localhost")) {
            // Allow localhost only in dev; in prod url must be explicit but we don't enforce non-localhost strictly
            // Just check not blank
        }
        if (dbPassword.isBlank() || dbPassword == "cliniq_dev_password") {
            missing.add("DATABASE_PASSWORD")
        }
        // AT and M-Pesa are required in prod if features are used; enforce non-blank
        if (atApiKey.isBlank()) missing.add("AT_API_KEY")
        if (mpesaKey.isBlank()) missing.add("MPESA_CONSUMER_KEY")
        if (mpesaSecret.isBlank()) missing.add("MPESA_CONSUMER_SECRET")
        if (mpesaPasskey.isBlank()) missing.add("MPESA_PASSKEY")
        if (mpesaCallbackSecret.isBlank()) missing.add("MPESA_CALLBACK_SECRET")
        // DATABASE_URL is already mapped via DATABASE_URL env, but check presence
        if (dbUrl.isBlank()) missing.add("DATABASE_URL")

        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "Missing required production configuration: ${missing.joinToString(", ")}"
            )
        }
    }

    private fun isProd() = env.activeProfiles.any { it.equals("prod", ignoreCase = true) }

    private fun isDefaultJwt(s: String): Boolean {
        val markers = listOf(
            "cliniq_jwt_secret",
            "development-secret",
            "CHANGE_ME",
            "minimum_64"
        )
        return markers.any { s.contains(it, ignoreCase = true) }
    }
}
