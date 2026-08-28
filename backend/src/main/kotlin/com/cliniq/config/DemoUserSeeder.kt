package com.cliniq.config

import com.cliniq.domain.auth.entity.User
import com.cliniq.domain.auth.entity.UserRole
import com.cliniq.domain.auth.repository.UserRepository
import com.cliniq.domain.clinic.entity.Clinic
import com.cliniq.domain.clinic.repository.ClinicRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * DemoUserSeeder
 *
 * Creates a known set of demo users for the demo clinic, with a known
 * password, on application start. Runs only in the `dev` profile by default
 * to avoid accidental seeding in production.
 *
 * The default demo password is `Demo1234!`. This is documented in
 * `docs/FRONTEND_HANDOFF.md` and must be changed for any non-local
 * deployment.
 */
@Component
@Profile("dev", "default")
@Order(10)
class DemoUserSeeder(
    private val userRepository: UserRepository,
    private val clinicRepository: ClinicRepository,
    private val passwordEncoder: PasswordEncoder
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)
    private val demoPassword = "Demo1234!"

    @Transactional
    override fun run(vararg args: String?) {
        val clinic = clinicRepository.findBySlug("demo") ?: run {
            log.warn("Demo clinic 'demo' not found; skipping DemoUserSeeder. " +
                    "Ensure V3 migration has run.")
            return
        }

        val accounts: List<Triple<String, String, UserRole>> = listOf(
            Triple("admin@cliniq.app", "Admin", UserRole.SUPER_ADMIN),
            Triple("doctor@cliniq.app", "Doctor", UserRole.DOCTOR),
            Triple("nurse@cliniq.app", "Nurse", UserRole.NURSE),
            Triple("reception@cliniq.app", "Reception", UserRole.RECEPTIONIST),
            Triple("finance@cliniq.app", "Finance", UserRole.FINANCE)
        )

        for ((email, firstName, role) in accounts) {
            upsertUser(clinic, email, firstName, role)
        }
        log.info("DemoUserSeeder: ensured ${accounts.size} demo users for clinic 'demo' " +
                "(password: $demoPassword)")
    }

    private fun upsertUser(clinic: Clinic, email: String, firstName: String, role: UserRole) {
        val existing = userRepository.findByEmail(email)
        if (existing != null) {
            // Keep the demo password fresh so the documented credential always works.
            existing.passwordHash = passwordEncoder.encode(demoPassword)
            existing.isActive = true
            userRepository.save(existing)
            return
        }
        userRepository.save(
            User(
                clinicId = clinic.id,
                email = email,
                passwordHash = passwordEncoder.encode(demoPassword),
                firstName = firstName,
                lastName = "Demo",
                role = role,
                isActive = true,
                isVerified = true
            )
        )
    }
}
