package com.cliniq.domain.billing.service

import com.cliniq.domain.billing.dto.MpesaStkPushResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class MpesaService(
    @Value("\${mpesa.consumer-key:}") private val consumerKey: String,
    @Value("\${mpesa.consumer-secret:}") private val consumerSecret: String,
    @Value("\${mpesa.shortcode:174379}") private val shortcode: String,
    @Value("\${mpesa.passkey:}") private val passkey: String,
    @Value("\${mpesa.callback-url:https://example.com/api/v1/billing/mpesa/callback}") private val callbackUrl: String,
    @Value("\${mpesa.callback-secret:}") private val callbackSecret: String,
    @Value("\${mpesa.sandbox:true}") private val sandbox: Boolean,
    private val restTemplate: RestTemplate
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val baseUrl get() = if (sandbox)
        "https://sandbox.safaricom.co.ke"
    else
        "https://api.safaricom.co.ke"

    fun initiateSTKPush(phone: String, amount: Long, invoiceId: UUID, accountRef: String): MpesaStkPushResponse {
        if (consumerKey.isBlank()) {
            log.warn("M-Pesa not configured � returning mock response")
            return MpesaStkPushResponse(true, "mock_${System.currentTimeMillis()}", "M-Pesa payment prompt sent (mock)")
        }

        return try {
            val token = getAccessToken()
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            val password = Base64.getEncoder().encodeToString("$shortcode$passkey$timestamp".toByteArray())
            val normalizedPhone = normalizePhone(phone)

            val callbackWithAuth = buildCallbackUrl(invoiceId)
            val body = mapOf(
                "BusinessShortCode" to shortcode,
                "Password" to password,
                "Timestamp" to timestamp,
                "TransactionType" to "CustomerPayBillOnline",
                "Amount" to amount,
                "PartyA" to normalizedPhone,
                "PartyB" to shortcode,
                "PhoneNumber" to normalizedPhone,
                "CallBackURL" to callbackWithAuth,
                "AccountReference" to accountRef,
                "TransactionDesc" to "ClinIQ Payment"
            )

            val headers = HttpHeaders().apply {
                setBearerAuth(token)
                contentType = MediaType.APPLICATION_JSON
            }

            val response = restTemplate.postForObject(
                "$baseUrl/mpesa/stkpush/v1/processrequest",
                HttpEntity(body, headers),
                Map::class.java
            )

            val checkoutId = response?.get("CheckoutRequestID") as? String
            MpesaStkPushResponse(true, checkoutId, "Payment prompt sent to $normalizedPhone")
        } catch (e: Exception) {
            log.error("M-Pesa STK push failed", e)
            MpesaStkPushResponse(false, null, "Payment initiation failed: ${e.message}")
        }
    }

    private fun getAccessToken(): String {
        val credentials = Base64.getEncoder()
            .encodeToString("$consumerKey:$consumerSecret".toByteArray())

        val headers = HttpHeaders().apply {
            set("Authorization", "Basic $credentials")
        }

        val response = restTemplate.exchange(
            "$baseUrl/oauth/v1/generate?grant_type=client_credentials",
            org.springframework.http.HttpMethod.GET,
            HttpEntity<Nothing>(headers),
            Map::class.java
        )
        return response.body?.get("access_token") as? String
            ?: throw RuntimeException("Failed to get M-Pesa access token")
    }

    private fun normalizePhone(phone: String): String {
        val digits = phone.replace(Regex("\\D"), "")
        return when {
            digits.startsWith("254") -> digits
            digits.startsWith("07") -> "254${digits.substring(1)}"
            digits.startsWith("01") -> "254${digits.substring(1)}"
            digits.startsWith("7") || digits.startsWith("1") -> "254$digits"
            else -> digits
        }
    }

    fun buildCallbackUrl(invoiceId: UUID): String {
        val base = "$callbackUrl?invoiceId=$invoiceId"
        if (callbackSecret.isBlank()) return base
        val token = hmac(invoiceId.toString(), callbackSecret)
        return "$base&token=$token"
    }

    fun isValidCallbackToken(invoiceId: UUID, token: String?): Boolean {
        if (callbackSecret.isBlank()) return true // no secret configured -> allow (dev mode)
        if (token.isNullOrBlank()) return false
        val expected = hmac(invoiceId.toString(), callbackSecret)
        return MessageDigest.isEqual(expected.toByteArray(), token.toByteArray())
    }

    private fun hmac(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private object MessageDigest {
        fun isEqual(a: ByteArray, b: ByteArray): Boolean = java.security.MessageDigest.isEqual(a, b)
    }
}
