// =============================================================
// ClinIQ — Production Hardening
// Contents:
//   1. CacheConfig        — Redis caching for analytics (5-min TTL)
//   2. AuditAspect        — AOP automatic audit logging
//   3. MetricsConfig      — Custom Micrometer business counters
//   4. GlobalExceptionHandler — Centralized error handling (complete)
//   5. application-prod.yml   — Production Spring config (as comment)
//   6. logback-spring.xml     — Structured JSON logging (as comment)
// =============================================================


// =============================================
// config/CacheConfig.kt
// com.cliniq.config
// =============================================
package com.cliniq.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig {

    companion object {
        // Cache names — use these as @Cacheable(cacheNames = [...])
        const val ANALYTICS_DASHBOARD    = "analytics:dashboard"
        const val ANALYTICS_TRENDS       = "analytics:trends"
        const val ANALYTICS_SEGMENTS     = "analytics:segments"
        const val CLINIC_INFO            = "clinic:info"
        const val DOCTOR_LIST            = "clinic:doctors"
        const val DRUG_STOCK             = "pharmacy:stock"
    }

    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): CacheManager {
        val objectMapper = ObjectMapper()
            .registerModule(JavaTimeModule())
            .registerModule(KotlinModule.Builder().build())
            .activateDefaultTyping(
                com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                    .allowIfBaseType(Any::class.java).build()
            )

        val jsonSerializer = GenericJackson2JsonRedisSerializer(objectMapper)

        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
            .disableCachingNullValues()

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration(ANALYTICS_DASHBOARD,  defaultConfig.entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration(ANALYTICS_TRENDS,     defaultConfig.entryTtl(Duration.ofHours(1)))
            .withCacheConfiguration(ANALYTICS_SEGMENTS,   defaultConfig.entryTtl(Duration.ofHours(1)))
            .withCacheConfiguration(CLINIC_INFO,          defaultConfig.entryTtl(Duration.ofHours(1)))
            .withCacheConfiguration(DOCTOR_LIST,          defaultConfig.entryTtl(Duration.ofMinutes(30)))
            .withCacheConfiguration(DRUG_STOCK,           defaultConfig.entryTtl(Duration.ofMinutes(2)))
            .build()
    }
}


// =============================================
// shared/audit/AuditAspect.kt
// com.cliniq.shared.audit
// AOP: automatically audits destructive service calls.
// Targets methods annotated with @Transactional that
// are NOT readOnly — i.e. real writes.
// =============================================
package com.cliniq.shared.audit

import com.cliniq.shared.context.ClinicContext
import com.cliniq.shared.exceptions.ForbiddenException
import com.cliniq.shared.exceptions.NotFoundException
import io.micrometer.core.instrument.MeterRegistry
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Aspect
@Component
class AuditAspect(
    private val auditService: AuditService,
    private val meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Intercepts all @Service methods that are @Transactional (non-readOnly writes).
     * Logs: method name, result, execution time, user, clinic.
     */
    @Around("within(@org.springframework.stereotype.Service *) && @annotation(transactional) && !transactional.readOnly()")
    fun auditWriteOperation(pjp: ProceedingJoinPoint, transactional: Transactional): Any? {
        val signature = pjp.signature as? MethodSignature
        val className  = pjp.target.javaClass.simpleName
        val methodName = signature?.name ?: "unknown"
        val action     = "${className}.${methodName}".uppercase().replace(".", "_")

        val startMs = System.currentTimeMillis()
        var success = true

        return try {
            val result = pjp.proceed()
            result
        } catch (e: Exception) {
            success = false
            // Record error metric
            meterRegistry.counter("cliniq.service.errors",
                "class", className, "method", methodName
            ).increment()
            throw e
        } finally {
            val elapsed = System.currentTimeMillis() - startMs
            val userId  = extractUserId()
            val clinicId = ClinicContext.getOrNull()

            // Log slow operations
            if (elapsed > 2000) {
                log.warn("SLOW OPERATION: {} took {}ms", action, elapsed)
            }

            // Record timing metric
            meterRegistry.timer("cliniq.service.duration",
                "class", className, "method", methodName, "success", success.toString()
            ).record(elapsed, java.util.concurrent.TimeUnit.MILLISECONDS)

            // Write audit log for significant write operations
            if (success && clinicId != null && userId != null &&
                shouldAudit(className, methodName)) {
                try {
                    auditService.log(
                        clinicId = clinicId,
                        userId = userId,
                        action = action,
                        entityType = inferEntityType(className),
                        entityId = null
                    )
                } catch (e: Exception) {
                    log.debug("Audit log write failed (non-critical): {}", e.message)
                }
            }
        }
    }

    private fun shouldAudit(className: String, method: String): Boolean {
        // Skip analytics reads, health checks, scheduled jobs
        val skipMethods = setOf("get", "find", "list", "search", "count", "exists",
                                "refresh", "process", "run", "schedule")
        return skipMethods.none { method.lowercase().startsWith(it) }
    }

    private fun inferEntityType(className: String): String = when {
        className.contains("Patient")     -> "patient"
        className.contains("Appointment") -> "appointment"
        className.contains("Consultation")-> "consultation"
        className.contains("Invoice")     -> "invoice"
        className.contains("Billing")     -> "invoice"
        className.contains("Drug")        -> "drug"
        className.contains("Pharmacy")    -> "drug"
        className.contains("Lab")         -> "lab_request"
        className.contains("Auth")        -> "user"
        className.contains("Crm")         -> "crm_campaign"
        else -> className.lowercase().removeSuffix("service")
    }

    private fun extractUserId(): UUID? = try {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth is com.cliniq.domain.auth.security.JwtAuthenticationToken) auth.userId else null
    } catch (_: Exception) { null }
}


// =============================================
// config/MetricsConfig.kt
// com.cliniq.config
// Custom business metrics exposed to Prometheus
// =============================================
package com.cliniq.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Configuration
class MetricsConfig {

    @Bean
    fun appointmentCreatedCounter(registry: MeterRegistry): Counter =
        Counter.builder("cliniq.appointments.created")
            .description("Total appointments booked across all clinics")
            .register(registry)

    @Bean
    fun consultationCompletedCounter(registry: MeterRegistry): Counter =
        Counter.builder("cliniq.consultations.completed")
            .description("Total consultations completed")
            .register(registry)

    @Bean
    fun invoiceCreatedCounter(registry: MeterRegistry): Counter =
        Counter.builder("cliniq.invoices.created")
            .description("Total invoices created")
            .register(registry)

    @Bean
    fun paymentRecordedCounter(registry: MeterRegistry): Counter =
        Counter.builder("cliniq.payments.recorded")
            .description("Total payments recorded")
            .register(registry)

    @Bean
    fun smssSentCounter(registry: MeterRegistry): Counter =
        Counter.builder("cliniq.sms.sent")
            .description("Total SMS messages sent via Africa's Talking")
            .register(registry)

    @Bean
    fun patientRegisteredCounter(registry: MeterRegistry): Counter =
        Counter.builder("cliniq.patients.registered")
            .description("Total patients registered")
            .register(registry)
}

/**
 * Usage in services — inject Counter beans and call .increment():
 *
 * @Autowired private lateinit var appointmentCreatedCounter: Counter
 * // After saving appointment:
 * appointmentCreatedCounter.increment()
 */


// =============================================
// shared/exceptions/GlobalExceptionHandler.kt
// com.cliniq.shared.exceptions
// COMPLETE centralized exception handler
// =============================================
package com.cliniq.shared.exceptions

import com.cliniq.shared.response.ApiError
import com.cliniq.shared.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.Instant

@org.springframework.web.bind.annotation.RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(this::class.java)

    @org.springframework.web.bind.annotation.ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<ApiResponse<Nothing>> =
        error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.message)

    @org.springframework.web.bind.annotation.ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ResponseEntity<ApiResponse<Nothing>> =
        error(HttpStatus.CONFLICT, "CONFLICT", ex.message)

    @org.springframework.web.bind.annotation.ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ResponseEntity<ApiResponse<Nothing>> =
        error(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.message)

    @org.springframework.web.bind.annotation.ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException): ResponseEntity<ApiResponse<Nothing>> =
        error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.message)

    @org.springframework.web.bind.annotation.ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException): ResponseEntity<ApiResponse<Nothing>> =
        error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.message)

    @org.springframework.web.bind.annotation.ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ApiResponse<Nothing>> =
        error(HttpStatus.FORBIDDEN, "FORBIDDEN", "You don't have permission to access this resource")

    @org.springframework.web.bind.annotation.ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException): ResponseEntity<ApiResponse<Nothing>> =
        error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials")

    @org.springframework.web.bind.annotation.ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val fieldErrors = ex.bindingResult.allErrors.associate { error ->
            val field = if (error is FieldError) error.field else error.objectName
            field to (error.defaultMessage ?: "Invalid value")
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponse(false, error = ApiError(
                code = "VALIDATION_ERROR",
                message = "Request validation failed",
                details = fieldErrors
            ))
        )
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(ex: MissingServletRequestParameterException): ResponseEntity<ApiResponse<Nothing>> =
        error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Required parameter '${ex.parameterName}' is missing")

    @org.springframework.web.bind.annotation.ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<Nothing>> =
        error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid value for parameter '${ex.name}'")

    @org.springframework.web.bind.annotation.ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(ex: NoResourceFoundException): ResponseEntity<ApiResponse<Nothing>> =
        error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Endpoint not found: ${ex.resourcePath}")

    @org.springframework.web.bind.annotation.ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unhandled exception: {}", ex.message, ex)
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred")
    }

    private fun error(status: HttpStatus, code: String, message: String?): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(status).body(
            ApiResponse(false, error = ApiError(code, message, timestamp = Instant.now()))
        )
}


// =============================================================
// SAVE AS: backend/src/main/resources/application-prod.yml
// =============================================================
/*
spring:
  datasource:
    hikari:
      maximum-pool-size: 30
      minimum-idle: 10
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 900000
  jpa:
    show-sql: false
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true
  flyway:
    enabled: true
    validate-on-migrate: true
    out-of-order: false
  cache:
    type: redis

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http:
          server:
            requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99

logging:
  level:
    com.cliniq: INFO
    org.springframework.security: WARN
    org.hibernate: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

server:
  compression:
    enabled: true
    mime-types: application/json,text/plain,text/css,application/javascript
    min-response-size: 1024
  error:
    include-message: never
    include-binding-errors: never
    include-stacktrace: never
*/


// =============================================================
// SAVE AS: backend/src/main/resources/logback-spring.xml
// Structured JSON logging for Loki/Grafana
// =============================================================
/*
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProfile name="prod">
        <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <customFields>{"app":"cliniq","env":"production"}</customFields>
                <fieldNames>
                    <timestamp>timestamp</timestamp>
                    <message>message</message>
                    <logger>logger</logger>
                    <thread>thread</thread>
                    <level>level</level>
                </fieldNames>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON_CONSOLE"/>
        </root>
    </springProfile>

    <springProfile name="dev,test">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="DEBUG">
            <appender-ref ref="CONSOLE"/>
        </root>
        <logger name="org.hibernate.SQL"             level="DEBUG"/>
        <logger name="org.springframework.security"  level="DEBUG"/>
    </springProfile>
</configuration>

Note: Add net.logstash.logback.encoder to build.gradle.kts for JSON logging:
implementation("net.logstash.logback:logstash-logback-encoder:7.4")
*/
