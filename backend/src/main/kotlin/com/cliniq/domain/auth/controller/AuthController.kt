package com.cliniq.domain.auth.controller

import com.cliniq.domain.auth.dto.*
import com.cliniq.domain.auth.security.JwtAuthenticationToken
import com.cliniq.domain.auth.service.AuthService
import com.cliniq.shared.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest, http: HttpServletRequest) =
        ResponseEntity.ok(ApiResponse.success(authService.login(req, http.remoteAddr)))

    @PostMapping("/register")
    fun register(@Valid @RequestBody req: RegisterClinicRequest) =
        ResponseEntity.status(201).body(ApiResponse.success(authService.registerClinic(req)))

    @PostMapping("/staff")
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','SUPER_ADMIN')")
    fun registerStaff(
        @Valid @RequestBody req: RegisterStaffRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ) = ResponseEntity.status(201).body(
        ApiResponse.success(authService.registerStaff(req, auth.clinicId!!, auth.userId))
    )

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody req: RefreshTokenRequest) =
        ResponseEntity.ok(ApiResponse.success(authService.refresh(req)))

    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal auth: JwtAuthenticationToken,
        http: HttpServletRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val token = http.getHeader("Authorization")?.removePrefix("Bearer ")?.trim()
        authService.logout(auth.userId, token)
        return ResponseEntity.ok(ApiResponse.success(Unit))
    }

    @PatchMapping("/password")
    fun changePassword(
        @Valid @RequestBody req: ChangePasswordRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<Unit>> {
        authService.changePassword(auth.userId, req)
        return ResponseEntity.ok(ApiResponse.success(Unit))
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal auth: JwtAuthenticationToken) =
        ResponseEntity.ok(ApiResponse.success(authService.me(auth.userId)))
}
