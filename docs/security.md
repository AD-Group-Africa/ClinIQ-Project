# ClinIQ Security

ClinIQ handles highly sensitive healthcare data. This document records the security model, the current controls, and the rules every developer must follow.

## Threat Model

- **Tenant escape**: a user of Clinic A accessing Clinic B data.
- **Privilege escalation**: a clinic user gaining SUPER_ADMIN or another clinic's context.
- **Client-side payment fraud**: a frontend claiming a payment succeeded.
- **Credential/token theft**: leaked JWTs, refresh tokens, or passwords.
- **Data exfiltration**: sensitive patient/clinical data leaked via logs or error responses.

## Authentication

| Control | Implementation |
|---|---|
| Password hashing | BCrypt, strength 12 (`BCryptPasswordEncoder(12)`) |
| Access token | JWT (HS384 via `Keys.hmacShaKeyFor`), 15 min TTL, carries `jti`, `email`, `clinicId`, `role`, `type=access` |
| Refresh token | JWT, 7 days TTL, stored in Redis keyed by user id, carries unique `jti` |
| Refresh rotation | Every refresh revokes the old token before issuing a new one; reuse of a revoked token is rejected |
| Token revocation | Access tokens are blacklisted in Redis (SHA-256 of the token) for their remaining TTL; logout revokes refresh + blacklists access |
| Login failures | `BadCredentialsException` → generic `401` (no user enumeration) |
| Deactivated users | Blocked at login and on refresh |

## Authorization (RBAC)

- `@EnableMethodSecurity` + `@PreAuthorize("hasAnyRole(...)")` on every controller method.
- Roles: `SUPER_ADMIN`, `CLINIC_ADMIN`, `DOCTOR`, `NURSE`, `RECEPTIONIST`, `FINANCE`, `LAB_TECHNICIAN`, `PHARMACIST`, `PATIENT`.
- The JWT is the only trusted identity input; `@AuthenticationPrincipal` binds the parsed token (never a client-supplied identity).

## Multi-tenancy isolation

- Tenant context is derived **only** from the `clinicId` claim in the signed JWT.
- `X-Clinic-ID` header is advisory: for non-SUPER_ADMIN it must match the JWT's clinic or the request is rejected (`403`). It can never grant access to another clinic.
- Every repository query filters by `clinicId`; cross-clinic lookups return `404`.
- `SUPER_ADMIN` is the only cross-clinic role, and must still select a clinic explicitly.

## Web & transport

| Control | Status |
|---|---|
| CSRF | Disabled — stateless JWT API; no cookies used for auth |
| CORS | Explicit `CorsConfigurationSource`; only `app.frontend-url`, `http://localhost:*`, `https://*.cliniq.app` |
| Security headers | CSP (`default-src 'self'`), HSTS (1y, includeSubdomains), X-Frame-Options sameOrigin, Referrer-Policy, X-Content-Type-Options |
| Session | Stateless (`SessionCreationPolicy.STATELESS`) |
| Rate limiting | **Not yet implemented** — add before public login/register exposure |

## Secrets & configuration

- All secrets come from environment variables (`application.yml` uses `${VAR:default}`).
- **Startup fails** in the `prod` profile if `JWT_SECRET` is a known default value.
- `.env` is gitignored; `.env.example` is the template.
- Dev-only secrets live in `application-dev.yml` (never in a prod profile).

## Error handling & logging

- `GlobalExceptionHandler` returns a consistent `ApiError` — no stack traces, no SQL, no internal details.
- Validation errors return a field → message map under `error.details`.
- `RequestIdFilter` adds `X-Request-Id` and an MDC `requestId` to every log line for correlation.
- **Never log**: passwords, JWTs, refresh tokens, full medical records, or patient clinical details. Audit entries store only structured metadata (entity ids, action, changed-field maps).

## Payment security

- The frontend can **never** mark a payment as paid.
- Manual payments are recorded server-side with amount/balance validation (overpayment rejected).
- M-Pesa success is only accepted from the webhook with `ResultCode == 0`.
- Webhook endpoints are public but must be treated as untrusted input and validated; future providers must follow the same server-verified, idempotent pattern.

## Operational rules (must never regress)

1. Never trust `X-Clinic-ID` as authorization — it is a validated selector only.
2. Never return JPA entities from controllers; always map to DTOs.
3. Never log tokens, passwords, or PHI.
4. Never add a `permitAll` path without review (the only public paths are `/api/v1/auth/{login,register,refresh}`, `/api/v1/public/**`, `/api/v1/billing/mpesa/callback`, actuator health/info, swagger).
5. Add a new Flyway migration (`Vn`) for schema changes; never edit an applied migration in production.
6. Keep the JWT/clinic filters inside the security chain (not as standalone servlet-filter beans).

## Known gaps (tracked)

- Rate limiting on `/auth/login` and `/auth/register` (design hook exists; not implemented).
- Webhook origin validation for M-Pesa (currently trusts the callback contract; add signature/origin checks before production).
- Object-level authorization tests for every domain (cross-clinic tests exist for patients and invoices).
- `application-prod.yml` — production relies on env vars only (documented in README).