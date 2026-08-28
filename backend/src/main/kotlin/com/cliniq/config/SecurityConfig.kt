package com.cliniq.config

import com.cliniq.domain.auth.security.JwtAuthenticationFilter
import com.cliniq.domain.auth.security.JwtService
import com.cliniq.shared.filter.ClinicIsolationFilter
import com.cliniq.shared.ratelimit.AuthRateLimitFilter
import com.cliniq.shared.ratelimit.RateLimitService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import jakarta.annotation.PostConstruct

private val DEFAULT_JWT_SECRET_MARKERS = listOf(
    "cliniq_jwt_secret_64chars_minimum_change_in_production_now",
    "cliniq_jwt_secret_minimum_64_chars_long_change_in_production_now",
    "development-secret-must-be-overridden-with-env-var",
    "CHANGE_ME_minimum_64_characters_random_string_here_replace_now"
)

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtService: JwtService,
    private val userDetailsService: UserDetailsService,
    private val environment: Environment,
    private val rateLimitService: RateLimitService,
    private val objectMapper: ObjectMapper
) {

    @Value("\${jwt.secret}")
    private lateinit var jwtSecret: String

    @Value("\${app.frontend-url:http://localhost:3000}")
    private lateinit var frontendUrl: String

    @Value("\${cors.allowed-origins:}")
    private lateinit var corsAllowedOrigins: String

    @PostConstruct
    fun validateProductionSecrets() {
        val activeProfiles = environment.activeProfiles
        val isProd = activeProfiles.any { it.equals("prod", ignoreCase = true) }
        if (isProd && DEFAULT_JWT_SECRET_MARKERS.any { jwtSecret.contains(it) }) {
            throw IllegalStateException(
                "Refusing to start with the default JWT_SECRET in profile(s) ${activeProfiles}. " +
                "Set a strong JWT_SECRET environment variable (e.g. openssl rand -base64 64)."
            )
        }
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        // Constructed here (not as beans) so they only ever run inside the
        // security chain. Spring Boot would otherwise auto-register them as
        // servlet filters, where they would run before the chain and have their
        // authentication cleared by SecurityContextHolderFilter.
        val jwtAuthFilter = JwtAuthenticationFilter(jwtService)
        val clinicIsolationFilter = ClinicIsolationFilter()
        val rateLimitFilter = AuthRateLimitFilter(rateLimitService, objectMapper)
        return http
        .csrf { it.disable() }
        .cors { it.configurationSource(corsConfigurationSource()) }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .headers { headers ->
            headers.contentSecurityPolicy("default-src 'self'")
            headers.httpStrictTransportSecurity { hsts ->
                hsts.maxAgeInSeconds(31536000).includeSubDomains(true)
            }
            headers.frameOptions { it.sameOrigin() }
            headers.referrerPolicy { it.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN) }
            headers.contentTypeOptions { }
        }
        .authorizeHttpRequests { auth ->
            auth.requestMatchers(
                "/api/v1/auth/**",
                "/api/v1/public/**",
                "/api/v1/billing/mpesa/callback",
                "/actuator/health",
                "/actuator/info",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html"
            ).permitAll()
            .anyRequest().authenticated()
        }
        .exceptionHandling { handling ->
            handling.authenticationEntryPoint { _, res, _ ->
                writeJson(res, 401, "UNAUTHORIZED", "Authentication required")
            }
            handling.accessDeniedHandler { _, res, _ ->
                writeJson(res, 403, "FORBIDDEN", "Access denied")
            }
        }
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter::class.java)
        .addFilterAfter(clinicIsolationFilter, JwtAuthenticationFilter::class.java)
        .build()
    }

    private fun writeJson(res: jakarta.servlet.http.HttpServletResponse, status: Int, code: String, message: String) {
        res.status = status
        res.contentType = "application/json"
        res.writer.write("""{"success":false,"error":{"code":"$code","message":"$message"}}""")
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val explicitOrigins = corsAllowedOrigins.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val isProd = environment.activeProfiles.any { it.equals("prod", ignoreCase = true) }

        val config = CorsConfiguration().apply {
            if (explicitOrigins.isNotEmpty()) {
                allowedOrigins = explicitOrigins
                // When explicit origins are set, do not use patterns
                allowedOriginPatterns = emptyList()
            } else if (isProd) {
                // Production defaults to deny-all unless explicitly configured
                allowedOrigins = emptyList()
                allowedOriginPatterns = emptyList()
            } else {
                allowedOrigins = listOf(frontendUrl, "http://localhost:3000", "http://localhost:3001")
                allowedOriginPatterns = listOf("http://localhost:*")
            }
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "Accept", "X-Request-Id", "X-Clinic-ID")
            exposedHeaders = listOf("X-Request-Id")
            allowCredentials = true
            maxAge = 3600L
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/**", config)
        source.registerCorsConfiguration("/api/v1/auth/**", config)
        return source
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    @Bean
    fun authenticationProvider(): DaoAuthenticationProvider =
        DaoAuthenticationProvider().apply {
            setUserDetailsService(userDetailsService)
            setPasswordEncoder(passwordEncoder())
        }
}
