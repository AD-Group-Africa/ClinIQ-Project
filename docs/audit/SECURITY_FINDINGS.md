# ClinIQ Backend — Security Findings (Current)

**Audit Date**: 2026-08-28
**Base Commit**: `f90905e`
**Severity Scale**: Critical (P0) → High (P1) → Medium (P2) → Low (P3)

> This document has been refreshed from the 2026-08-22 audit. The previous document is preserved as historical context at the bottom.

---

## Current state (post-restructure)

### Authentication (P0)
| Item | Status | Evidence |
|---|---|---|
| BCrypt password hashing | ✓ | `SecurityConfig.kt:61` uses `BCryptPasswordEncoder(12)` |
| JWT signing | ✓ | `JwtService.kt:26-31` uses `Keys.hmacShaKeyFor` with HMAC-SHA key |
| Access token expiry | ✓ | `application.yml:38` — 900 000 ms (15 min) |
| Refresh token expiry | ✓ | `application.yml:39` — 604 800 000 ms (7 days) |
| Refresh token rotation | ✓ | `AuthService.refresh:122` revokes old, issues new |
| Token revocation (Redis blacklist) | ✓ | `JwtService.blacklist` + `revokeRefreshToken` |
| Logout invalidates both tokens | ✓ | `AuthService.logout:127-134` |
| Login failure handled | ✓ | `AuthService.login:36-42` translates `BadCredentialsException` to `UnauthorizedException` |
| Deactivated user blocked at login | ✓ | `AuthService.login:45` throws `ForbiddenException` |

### Authorization (P1)
| Item | Status | Evidence |
|---|---|---|
| `@EnableMethodSecurity` enabled | ✓ | `SecurityConfig.kt:25` |
| RBAC role annotations on controllers | ✓ | `@PreAuthorize("hasAnyRole(...)")` everywhere |
| `JwtAuthenticationToken` carries `clinicId` and `role` | ✓ | `domain.auth.security.JwtAuthenticationToken` |
| Clinic isolation filter | ⚠ | `ClinicIsolationFilter` works but has the `X-Clinic-ID` fallback noted in the audit |
| Object-level authorization tests | ✗ | Not yet written |
| CORS explicit configuration | ✗ | `SecurityConfig.kt:34` uses `cors { }` (Spring default) |
| Security headers | ✓ | CSP, HSTS, X-Frame-Options configured in `SecurityConfig:36-44` |
| Rate limiting | ✗ | Not configured |

### Multi-tenancy (P1)
| Item | Status | Evidence |
|---|---|---|
| Every domain entity has `clinic_id` | ✓ | All `@Entity` classes |
| Every controller passes `auth.clinicId!!` to service | ✓ | All controllers |
| Every service scopes by `clinicId` | ✓ | All services |
| Every repository query filters by `clinicId` | ✓ | All repository methods |
| Cross-clinic access test coverage | ✗ | Required by brief §9 |
| `X-Clinic-ID` header trusted as primary source | ✗ | Correctly **not** trusted; only used to validate the JWT's clinicId |

### Data exposure (P2)
| Item | Status | Evidence |
|---|---|---|
| No JPA entity in API responses | ✓ | All controllers use DTOs |
| `passwordHash` never serialized | ✓ | Field is on entity only; DTOs do not include it |
| Validation on request bodies | ✓ | `@Valid` and field constraints on DTOs |
| Standardized error response | ✓ | `GlobalExceptionHandler` returns `ApiError` |

### Observability (P2)
| Item | Status |
|---|---|
| Health endpoint exposed | ✓ `actuator/health` |
| Structured logging | ⚠ Basic only — no request/correlation ID |
| Sensitive data in logs | ⚠ `BillingService.kt:78` logs patient name and amount — acceptable for now |
| Database health | ✓ via Spring Actuator |
| Redis health | ✓ via Spring Actuator |

---

## Recommended actions (in priority order)

1. **Add explicit CORS configuration** in `SecurityConfig` (P1) — accept `app.frontend-url` only.
2. **Add rate limiting on `/auth/login` and `/auth/register`** (P1) — Bucket4j or similar.
3. **Add startup check** that refuses to start the `prod` profile if `JWT_SECRET` equals the default value (P0).
4. **Replace `tokenHash` from `hashCode()` to SHA-256** in `JwtService` (P1).
5. **Remove `X-Clinic-ID` fallback in `ClinicIsolationFilter`** (P1) — derive clinicId only from JWT.
6. **Write cross-clinic access tests** (P1) — at least one per major entity.
7. **Add request/correlation ID MDC filter** (P2) — improves traceability.

## See also
- [`CODEBASE_AUDIT.md`](./CODEBASE_AUDIT.md) — full audit with code references.
