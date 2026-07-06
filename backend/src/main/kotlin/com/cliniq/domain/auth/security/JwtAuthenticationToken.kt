package com.cliniq.domain.auth.security

import com.cliniq.domain.auth.entity.UserRole
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.UUID

class JwtAuthenticationToken(
    val userId: UUID,
    val email: String,
    val clinicId: UUID?,
    val role: UserRole
) : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_${role.name}"))) {
    init { isAuthenticated = true }
    override fun getCredentials(): Any? = null
    override fun getPrincipal(): Any = email
}
