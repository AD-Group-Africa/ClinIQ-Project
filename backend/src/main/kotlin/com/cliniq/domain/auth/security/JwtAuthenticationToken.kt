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
    // The principal is the token itself so that controllers can bind
    // `@AuthenticationPrincipal auth: JwtAuthenticationToken` and read
    // userId / clinicId / role from it.
    override fun getPrincipal(): Any = this

    // Prevent AbstractAuthenticationToken.toString()/hashCode()/equals() from
    // recursing through getPrincipal() (which returns this).
    override fun toString(): String =
        "JwtAuthenticationToken(userId=$userId, email=$email, clinicId=$clinicId, role=${role.name})"

    override fun hashCode(): Int = userId.hashCode()

    override fun equals(other: Any?): Boolean =
        other is JwtAuthenticationToken && other.userId == userId

    // getName() in AbstractAuthenticationToken would recurse through
    // getPrincipal() (which returns this), so return the email directly.
    override fun getName(): String = email
}
