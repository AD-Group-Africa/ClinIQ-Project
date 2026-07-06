package com.cliniq.domain.auth.service

import com.cliniq.domain.auth.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User as SpringUser
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserDetailsServiceImpl(private val users: UserRepository) : UserDetailsService {
    override fun loadUserByUsername(email: String): UserDetails {
        val u = users.findByEmail(email) ?: throw UsernameNotFoundException("User not found: $email")
        return SpringUser.builder()
            .username(u.email)
            .password(u.passwordHash)
            .disabled(!u.isActive)
            .authorities(SimpleGrantedAuthority("ROLE_${u.role.name}"))
            .build()
    }
}
