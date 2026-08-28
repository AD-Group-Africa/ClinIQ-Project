# ClinIQ Backend — Security Findings

**Audit Date**: 2026-08-22
**Base Tag**: `pre-audit`
**Severity Scale**: Critical (P0) → High (P1) → Medium (P2) → Low (P3)

## Authentication (P0 Critical)

### 1. Default JWT secret in code
- **Value**: `cliniq_jwt_secret_minimum_64_chars_long_change_in_production_now`
- **Location**: `application.yml` line 37: `jwt.secret: ${JWT_SECRET:cliniq_jwt_secret_minimum_64_chars_long_change_in_production_now}`
- **Impact**: **CRITICAL** — if defaults are not overridden, JWT is weak/trivially breakable
- **Fix**: Always set `JWT_SECRET` via environment variable; never commit default value

### 2. BCrypt password encoding
- **Status**: Configured via Spring Security auto-configuration
- **Verification needed**: Confirm BCrypt is being used for password hashing
- **Location**: `UserDetailsService` / `UserRepository` password handling
- **Fix**: Verify `password_hash` column stores BCrypt hashes; add test to confirm

### 3. Refresh token rotation
- **Status**: Not configured
- **Risk**: Without rotation, stolen refresh tokens can be reused indefinitely
- **Fix**: Implement refresh token storage (Redis blacklisting) + rotation on each use

### 4. Token blacklisting / Redis revocation
- **Status**: Redis configured but purpose unclear
- **Possible uses**:
  - Store revoked refresh tokens (jti + expiry)
  - Cache blacklisted access tokens
  - Rate limiting counter
- **Fix**: Clarify Redis purpose; implement logout blacklisting if not present

### 5. Login endpoint
- **Endpoint**: `POST /api/v1/auth/login`
- **Expected**: Authenticate credentials, return JWT + refresh token
- **Verification**: Test end-to-end login flow with test accounts

## Authorization (P1 High)

### 6. Role-based access control
- **Roles defined**: `SUPER_ADMIN`, `CLINIC_ADMIN`, `DOCTOR`, `NURSE`, `RECEPTIONIST`, `FINANCE`, `PATIENT`
- **Implementation**: `@EnableMethodSecurity` with JSR-305 annotations
- **Gap**: No explicit `GrantedAuthority` mapper from DB `role` column
- **Impact**: `hasAnyRole()` may not work as expected; role values may not map to authorities
- **Fix**: Either:
  - Implement `Role` entity with `ManyToMany` to `User`, or
  - Map DB `role` values to Spring Security `GrantedAuthority` beans

### 7. Clinic tenant isolation
- **Expected**: Every request must be isolated by clinic context
- **Implementation**: `ClinicIsolationFilter` referenced in `SecurityConfig` (needs verification)
- **Gap**: No explicit evidence that clinic ID is checked on every data access
- **Fix**: Enforce clinic ID filter on all repository queries; test cross-clinic access

### 8. Object-level authorization
- **Rule**: Users can only access their own clinic's data
- **Scope**: Patients, appointments, invoices, prescriptions, lab results, etc.
- **Status**: Not explicitly tested or enforced in code review
- **Fix**: Add `@PreAuthorize` or `@Secured` annotations on service methods; write integration tests

### 9. CORS configuration
- **Config**: `WebConfig.kt` (or equivalent)
- **Current**: Allows `/api/**` without strict origin restrictions
- **Risk**: May allow cross-origin attacks in production
- **Fix**: Restrict to specific origins; add `allowedMethods`, `allowedHeaders` explicitly

### 10. Password policy
- **Status**: No explicit password complexity requirements in code
- **Risk**: Weak passwords if users choose them
- **Fix**: Add validation annotations or service-layer checks

## Security Headers & Hardening (P2 Medium)

### 11. Security headers
- **Missing**: `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`
- **Implementation**: Spring Boot Actuator `springdoc` or `WebConfig` can add headers
- **Fix**: Add `SecurityFilterChain` bean with standard headers

### 12. Dependency scanning
- **Status**: No automated vulnerability scanning in CI/CD
- **Dependencies**: Spring Boot 3.3.4, JJWT 0.12.6, Flyway 10.10.0, etc.
- **Fix**: Add Dependabot or similar; run `mvn dependency:check` or `gradle dependencyUpdates`

### 13. Rate limiting
- **Status**: No rate limiting configured on public endpoints
- **Endpoints at risk**: `POST /api/v1/auth/login`, `POST /api/v1/auth/register`
- **Fix**: Add rate limiter (Spring Cloud Gateway, Bucket4j, or Redis-based counter)

### 14. Input validation
- **Status**: Bean Validation (`@Valid`) configured on controller parameters
- **Custom validators**: May be missing for domain-specific rules
- **Fix**: Add custom `@Valid` groups or constraint validators for clinic-specific rules

## Data Exposure & Privacy (P2 Medium)

### 15. JPA entities exposed as API responses
- **Rule**: Never expose JPA entities directly — use DTOs
- **Status**: Need to verify all controllers use DTOs, not entities
- **Risk**: Entity relationships could expose excessive data; lazy loading issues
- **Fix**: Audit all controllers; ensure `ApiResponse<PatientDTO>` not `ApiResponse<Patient>`

### 16. Exposed entity fields
- **Potential**: Some entity fields may contain sensitive data (password_hash, etc.)
- **Verification needed**: Check DTOs strip sensitive fields
- **Fix**: Use `@JsonIgnore` or projection interfaces for sensitive fields

## Session & Token Management (P1 High)

### 17. Logout and token invalidation
- **Endpoint**: `POST /api/v1/auth/logout`
- **Expected**: Invalidate refresh token, add to Redis blacklist
- **Status**: Endpoint exists but blacklisting mechanism needs verification
- **Fix**: Test login → logout → refresh cycle; confirm token is blacklisted

### 18. JWT token expiry
- **Access token**: 900000 ms (15 minutes) — configured in `application.yml`
- **Refresh token**: 604800000 ms (7 days) — configured in `application.yml`
- **Risk**: Long refresh token window increases attack surface
- **Fix**: Consider shorter refresh window + rotation; implement revocation

## Security Audit Summary

| Category | Status | Priority |
|----------|--------|----------|
| JWT secret default value | ❌ Present in config | **P0 Critical** |
| Role model alignment with Spring Security | ❓ Needs verification | **P1 High** |
| Clinic tenant isolation | ❓ Needs enforcement | **P1 High** |
| Token blacklisting on logout | ❓ Needs implementation | **P1 High** |
| Security headers | ❌ Missing | **P2 Medium** |
| Rate limiting on login/register | ❌ Missing | **P2 Medium** |
| Input validation | ✅ Bean Validation present | **P3 Low** |
| JPA entities vs DTOs | ⚠️ Needs audit | **P2 Medium** |
| CORS configuration | ⚠️ May be too permissive | **P2 Medium** |
| Password hashing (BCrypt) | ✅ Configured | **P3 Low** |

## Immediate Action Items

1. **Remove default JWT secret** — set via env var only
2. **Align role model** — implement Role entity or authority mapper
3. **Enforce clinic isolation** — on every request, on every query
4. **Implement logout blacklisting** — Redis jti-based revocation
5. **Add security headers** — CSP, X-Frame-Options, etc.
6. **Add rate limiting** — on auth endpoints
7. **Audit DTO usage** — ensure no entity exposure
8. **Test cross-clinic access** — explicitly write failing tests then fix