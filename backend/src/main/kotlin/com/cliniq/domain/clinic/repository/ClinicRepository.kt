package com.cliniq.domain.clinic.repository

import com.cliniq.domain.clinic.entity.Clinic
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ClinicRepository : JpaRepository<Clinic, UUID> {
    fun findBySlug(slug: String): Clinic?
    fun existsBySlug(slug: String): Boolean
    fun existsByEmail(email: String): Boolean

    @Query("SELECT c FROM Clinic c WHERE c.isActive = TRUE ORDER BY c.createdAt ASC")
    fun findAllActive(): List<Clinic>

    @Query("SELECT c FROM Clinic c WHERE c.slug = :slug AND c.isActive = TRUE")
    fun findActiveBySlug(slug: String): Clinic?
}
