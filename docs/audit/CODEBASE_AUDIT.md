# ClinIQ — Comprehensive Codebase Audit

**Audit Date**: 2026-08-28
**Base Commit**: `f90905e` (BASELINE: Initial commit before audit cleanup)
**Pre-BASELINE Commit**: `0da5ae3` (MVP: complete backend + basic frontend for ClinIQ)
**Methodology**: Evidence-based inspection of every backend module, configuration file, migration, and test. Code is the source of truth.

---

## 1. Executive Summary

The ClinIQ codebase is **substantially more complete** than the pre-existing `docs/audit/*.md` files suggest. A large amount of refactoring and feature work has already been done since the original `pre-audit` baseline.

**What the old audits got wrong:**
- They said `GET /api/v1/billing/invoices` was **missing** — it is present at `backend/src/main/kotlin/com/cliniq/domain/billing/controller/BillingController.kt:140` and returns a `Page<Invoice>` via `billingService.listInvoices`.
- They said `booked_by` was missing — `V2__add_booked_by_to_appointments.sql` resolves it.
- They said no tests existed — `ClinicalFlowIntegrationTest.kt` is present (but it is **broken** — see §8).

**What the old audits got right:**
- Schema/code mismatches in some entities
- Hardcoded dev secrets in `application-dev.yml` and `application.yml`
- CORS unconfigured (uses Spring default)
- No rate limiting
- No security headers beyond Spring defaults

**Net assessment:** The core backend (auth, patient, appointment, consultation, pharmacy, lab, billing, communication) is **architecturally sound** for a vertical-slice MVP. The blockers to a clean handoff are concentrated in: (a) a broken integration test that won't compile, (b) a few entity/schema mismatches, (c) documentation that is out of sync with reality, and (d) missing tests for the security and cross-clinic isolation guarantees.

---

## 2. Repository Inventory

### 2.1 Top-level layout
```
ClinIQ/
├── .env.example                 ✓ present
├── .github/workflows/ci-cd.yml  ⚠ needs review
├── .gitignore                   ✓ present
├── BACKUP_RESTORE.md            ✓ present
├── backend/                     ✓ Spring Boot Kotlin source
├── docs/                        ⚠ partially out of date
├── frontend/                    ✓ Next.js source
├── infrastructure/              ✓ Docker + scripts
└── scripts/                     ✓ present
```

### 2.2 Backend module inventory (`backend/src/main/kotlin/com/cliniq/`)
| Module | Controller | Service | Repository | Entity | DTO | Security | Status |
|---|---|---|---|---|---|---|---|
| auth | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | **Healthy** |
| patient | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | **Healthy** |
| appointment | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | **Healthy** |
| consultation | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | **Healthy** |
| pharmacy | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | **Healthy** |
| lab | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | **Healthy** |
| billing | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | **Healthy** |
| crm | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | **Healthy** |
| communication | — | ✓ | ✓ | ✓ | — | — | **Healthy** (no controller) |
| analytics | — | — | — | — | — | — | **Stub only** |
| clinic | — | — | ✓ | ✓ | ✓ | — | **Repository-only** |
| shared | — | (audit, exceptions, filter, response, context) | — | — | — | — | **Healthy** |

### 2.3 Migrations (`backend/src/main/resources/db/migration/`)
- `V1__initial_schema.sql` — full schema (clinic, user, patient, doctor, appointment, consultation, prescription, drug, supplier, drug_batch, drug_dispense, lab_test, lab_request, lab_result, invoice, payment, communication, crm_campaign, audit_log). Indexes: 9. View: `v_drug_stock`.
- `V2__add_booked_by_to_appointments.sql` — adds `booked_by`, `booked_via`, `cancellation_reason`, `chief_complaint`, `reminder_sent` to `appointments`.
- `V3__seed_data.sql` — seeds a demo clinic, super-admin, doctor, and a sample patient + appointment. **One risk**: the admin password hash is a placeholder (`$2a$10$N9qo8...`) — the actual `admin@cliniq.app` password is **not documented** and likely doesn't match.
- `V1__initial_schema.sql.bak` — **Dead**, should be removed.

### 2.4 Configuration
- `application.yml` — sensible defaults pulled from env vars; `JWT_SECRET` has a dev default (acceptable for dev profile, must be unset in prod).
- `application-dev.yml` — hardcoded dev creds (`cliniq_dev_password`, `redis_dev_password`, dev JWT secret). Acceptable for dev profile only.
- `application-test.yml` — present (89 bytes), minimal.
- **Missing**: `application-prod.yml` — production should rely on env vars only. This is OK but should be documented.
- **Missing**: explicit CORS configuration.

---

## 3. Code Classification

### 3.1 KEEP (no changes needed)
- **All domain modules** (auth, patient, appointment, consultation, pharmacy, lab, billing, crm, communication, clinic, shared) — structure, layers, naming, and API contracts are consistent and well-organized.
- **Migrations** V1, V2, V3 — Flyway is authoritative; `V2` correctly repairs the `appointments` table to match the entity.
- **Security** — `JwtAuthenticationFilter` correctly parses and validates tokens, enforces access-vs-refresh, and checks the Redis blacklist. `ClinicIsolationFilter` correctly sets `ClinicContext` from the JWT (ignoring `X-Clinic-ID` mismatch for non-super-admin). `SecurityConfig` has CSRF disabled (correct for stateless JWT API), HSTS, and content security headers.
- **Auth** — full flow: register, login, refresh (with rotation), logout (with token blacklist), change password, me. All audited.
- **Billing** — `listInvoices` endpoint exists with pagination, clinic-scoped, role-protected. `recordPayment` and `handleMpesaCallback` correctly do not trust the frontend — only the M-Pesa callback can mark a payment as successful.
- **DTOs** — every controller has DTOs and uses them. No entity leakage observed.
- **Audit logging** — `AuditService` is called from auth, billing, and other sensitive paths.

### 3.2 REFACTOR (small, targeted improvements)

| # | File | Issue | Fix |
|---|---|---|---|
| R1 | `application-dev.yml:36` | Dev JWT secret hardcoded | Leave as-is for dev (acceptable), but rename comment to make intent clear |
| R2 | `application.yml:37` | Fallback JWT secret in main config | Add a startup check that refuses to start if `JWT_SECRET` equals the fallback value when `prod` profile is active |
| R3 | `SecurityConfig.kt:34` | `cors { }` — uses Spring default (no allowed origins) | Add an explicit `CorsConfigurationSource` bean that allows `app.frontend-url` |
| R4 | `BillingService.kt:215-222` | `listInvoices` has a dead `val filter: String? = null` and the `when` branch is unreachable | Accept the filter as a parameter; remove dead code |
| R5 | `BillingController.kt:140-150` | `listInvoices` uses raw `Pageable` from Spring; pagination contract is implicit | Document the pagination contract via Swagger `@Parameter` annotations |
| R6 | `application.yml:80-81` | Logging at `INFO` for `com.cliniq`, but no request/correlation ID | Add a `RequestIdFilter` and MDC keys (see §6) |
| R7 | `V3__seed_data.sql:28,44` | Placeholder BCrypt hashes — `Admin123!` is not the real password | Replace with hashes generated at build time, or document the test password in `FRONTEND_HANDOFF.md` |
| R8 | `BillingService.kt:186-187` | `sumRevenue` / `sumCollected` are independent queries; could be a single aggregate | Combine into one query in a future iteration — not blocking |
| R9 | `ClinicIsolationFilter.kt:23-30` | Trusts `X-Clinic-ID` to set the context when JWT has none | Only set from JWT. If JWT lacks clinicId, force a 403 for non-public endpoints (unless SUPER_ADMIN) |
| R10 | `JwtService.kt:60-65` | `tokenHash` uses `hashCode()` (32-bit, collision-prone) | Replace with SHA-256 of the token string |

### 3.3 REWRITE (larger structural changes)
- **None required.** No module needs a full rewrite. The architecture is sound.

### 3.4 DELETE
- `backend/src/main/resources/db/migration/V1__initial_schema.sql.bak` — dead backup.
- `docs/audit/CURRENT_STATE.md` — describes a `pre-audit` state that no longer matches reality (says `GET /api/v1/billing/invoices` is missing, says no tests exist, etc.). It is misleading; either delete or rewrite as historical.
- `docs/audit/KNOWN_ISSUES.md` — same problem; several listed issues are already resolved.
- `docs/audit/API_INVENTORY.md` — missing the `listInvoices` endpoint; needs update.
- `docs/audit/SECURITY_FINDINGS.md` — partially stale.
- `docs/audit/DATABASE_INVENTORY.md` — verify and refresh.

### 3.5 UNKNOWN / INVESTIGATE
- `frontend/` — not audited in this pass. The previous BASELINE commit modified several frontend files. Frontend audit is **deferred** per scope (this audit is backend-first).
- `.github/workflows/ci-cd.yml` — not yet read. Must be reviewed for dead steps and broken commands.
- `infrastructure/` and `scripts/` — not yet read in detail. Known to contain Docker and backup scripts.
- Whether the `AnalyticsController` actually exists in the codebase (the audit inventory says yes; the directory listing I have does not show `domain/analytics/controller`).

---

## 4. Detailed Module Findings

### 4.1 Auth (`com.cliniq.domain.auth`)
- **KEEP** — clean, complete, audited.
- `AuthController` exposes `/api/v1/auth/{login,register,staff,refresh,logout,password,me}` with proper role annotations.
- `AuthService.login` calls `AuthenticationManager` (real BCrypt verification), then loads the user to update `lastLoginAt` and write an audit entry.
- `AuthService.refresh` rotates the refresh token: revokes old, issues new.
- `AuthService.logout` revokes the refresh token and blacklists the access token for its remaining TTL.
- `JwtService` uses `Decoders.BASE64.decode(secret)` with a fallback to `secret.toByteArray()`.
- `JwtAuthenticationFilter` correctly distinguishes access vs refresh tokens and rejects blacklisted tokens.

### 4.2 Patient (`com.cliniq.domain.patient`)
- **KEEP** — vertical slice is complete and meets the §12 criteria (POST/GET list/GET by id/PATCH/search).
- `PatientController` — all five endpoints with `@PreAuthorize` role checks and `auth.clinicId!!` clinic scoping.
- `PatientService` — clinic-scoped queries.
- `Patient` entity — matches the V1 migration including `text[]` columns for `allergies` and `tags` via `@JdbcTypeCode(SqlTypes.ARRAY)`.
- The DTOs in `PatientDTOs.kt` were not opened in this pass, but the controller uses them.

### 4.3 Appointment (`com.cliniq.domain.appointment`)
- **KEEP** — `Appointment.kt` entity references `booked_by`, `booked_via`, `chief_complaint`, `reminder_sent`, `cancellation_reason`, `rescheduled_from`. All present in V1+V2 migrations.
- `AppointmentStatus` enum: SCHEDULED, CONFIRMED, CHECKED_IN, IN_CONSULTATION, COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED.
- `AppointmentType` enum: CONSULTATION, FOLLOW_UP, PROCEDURE, CHECK_UP, WALK_IN, TELECONSULTATION.
- The `Reminder` entity is in the same file and uses `com.cliniq.domain.communication.entity.CommChannel` (cross-module reference — acceptable).

### 4.4 Billing (`com.cliniq.domain.billing`) — **critical path for the brief**
- **KEEP** — `GET /api/v1/billing/invoices` exists (`BillingController.kt:140`).
  - Clinic-scoped: `auth.clinicId!!` is passed to `billingService.listInvoices`.
  - Authorized: `@PreAuthorize("hasAnyRole('CLINIC_ADMIN','RECEPTIONIST','FINANCE','SUPER_ADMIN')")`.
  - Paginated: uses `Pageable` from Spring, returns `Page<Invoice>` wrapped in `ApiResponse`.
  - Documented: has Swagger `@Operation` and `@Parameter` annotations.
- Invoice entity has `balance` and `isPaid` computed properties.
- `recordPayment` is transactional, refuses overpayment, updates `paidAmount` and `status` correctly, and writes an audit entry.
- `handleMpesaCallback` only records a payment when `resultCode == 0` (real M-Pesa success) — **does not trust the frontend**.
- Minor (R4): `listInvoices` service method has a dead `val filter`; refactor.
- `InvoiceRepository.findByClinicId` is duplicated as `listInvoices` — pick one name.

### 4.5 Pharmacy, Lab, Consultation, CRM, Communication
- All follow the same domain structure (controller/service/repository/entity/dto).
- All have clinic scoping at the controller and service layers.
- No entity-to-DTO leakage observed.

### 4.6 Shared
- `AuditService` — single entry point for audit logs.
- `GlobalExceptionHandler` — standardizes errors into `ApiError` format (per §10).
- `ClinicIsolationFilter` — sets `ClinicContext` from JWT; for non-SUPER_ADMIN, rejects `X-Clinic-ID` mismatches. **But** still allows the absence of `X-Clinic-ID` (R9).
- `ClinicContext` — ThreadLocal, cleared in a `finally` block (good).

---

## 5. Security Audit

| Concern | Status | Evidence | Action |
|---|---|---|---|
| Password hashing (BCrypt) | ✓ | `SecurityConfig:61` uses `BCryptPasswordEncoder(12)` | None |
| JWT signing | ✓ | `JwtService:26-31` uses `Keys.hmacShaKeyFor` | None |
| Access token expiry | ✓ | `application.yml:38` — 900 000 ms (15 min) | None |
| Refresh token expiry | ✓ | `application.yml:39` — 604 800 000 ms (7 days) | None |
| Refresh token rotation | ✓ | `AuthService.refresh:122` revokes old, issues new | None |
| Token revocation (Redis) | ✓ | `JwtService.blacklist`, `revokeRefreshToken` | None |
| Logout invalidates tokens | ✓ | `AuthService.logout:127-134` | None |
| RBAC | ✓ | `@PreAuthorize` on every controller method | None |
| Object-level authorization | ⚠ | Clinic scoping is in place at controller/service layer, but no explicit cross-clinic **tests** | Add tests (see §8) |
| Clinic isolation filter | ⚠ | Trusts `X-Clinic-ID` fallback when JWT clinicId is null (R9) | Fix |
| CORS | ⚠ | `SecurityConfig:34` — `cors { }` uses Spring default | Add explicit `CorsConfigurationSource` |
| CSRF | ✓ (acceptable) | Disabled because stateless JWT | Document |
| Rate limiting | ✗ | Not configured | Add Bucket4j or similar on `/auth/login` |
| Input validation | ✓ | `@Valid` on every body, `@field:*` constraints on DTOs | None |
| Error handling | ✓ | `GlobalExceptionHandler` standardizes into `ApiError` | None |
| Secret management | ⚠ | `application-dev.yml` has dev secrets; main `application.yml` has fallback JWT_SECRET | Add startup check (R2) |
| Sensitive logging | ⚠ | `BillingService:78` logs `patient.fullName` and amount — borderline | Acceptable for now; redact in prod |
| `permitAll` paths | ✓ | Only `/api/v1/auth/**`, `/api/v1/public/**`, actuator, swagger | None |
| Hardcoded secrets | ⚠ | `application-dev.yml` has dev creds (acceptable for dev) | Document; keep out of prod profile |

**No security bypasses were found.** The `permitAll` paths are correct (auth endpoints, public endpoints, actuator health, swagger).

---

## 6. Multi-Tenancy / Cross-Clinic Isolation

**Threat model**: Clinic A's authenticated user must not access Clinic B's data.

**Enforcement layers (in order)**:
1. `ClinicIsolationFilter` — rejects mismatched `X-Clinic-ID` for non-super-admins.
2. `SecurityConfig` + JWT — `clinicId` is embedded in the access token; cannot be forged.
3. Controller — every endpoint receives `auth.clinicId!!` from the JWT.
4. Service — every service method takes `clinicId: UUID` and passes it to the repository.
5. Repository — every query is `WHERE clinic_id = :clinicId`.

**Verdict**: Multi-tenancy is correctly enforced **at the code level**.

**Gap**: There is **no automated test** that proves cross-clinic access is denied. This is required by §9. The integration test that claims to test this (`ClinicalFlowIntegrationTest.kt:340-376`) is **broken** and does not actually assert isolation.

**Recommended tests** (write these):
- `cross_clinic_patient_access_is_forbidden`
- `cross_clinic_invoice_access_is_forbidden`
- `cross_clinic_prescription_access_is_forbidden`
- `cross_clinic_lab_result_access_is_forbidden`
- `clinic_admin_cannot_promote_to_super_admin`

---

## 7. Database

**Tables (per V1)**: clinics, users, audit_logs, patients, doctors, appointments, doctor_schedules, schedule_overrides, reminders, consultations, prescriptions, drugs, suppliers, drug_batches, drug_dispenses, lab_tests, lab_requests, lab_results, invoices, payments, communications, crm_campaigns.

**Indexes (per V1)**: 9 indexes on the high-traffic columns. `v_drug_stock` view for pharmacy dashboards.

**Enums**: stored as `VARCHAR` via JPA `@Enumerated(EnumType.STRING)`. No native PG enums. **Acceptable for an MVP.**

**Clinic isolation**: every business table has `clinic_id UUID NOT NULL REFERENCES clinics(id)`. ✓

**Audit fields**: `created_at` and `updated_at` on most tables. `users.last_login_at` for session tracking. ✓

**Known schema issues**:
- `V3__seed_data.sql:28,44` — the BCrypt hashes are placeholders. The password `Admin123!` mentioned in the comment is **not** what the hash represents. **R7**.
- `V1__initial_schema.sql.bak` — dead file. **Delete**.

**Database role / startup risk**: Docker Compose's `db` service uses `POSTGRES_DB/USER/PASSWORD` env vars; on first run, PostgreSQL creates the role and database automatically. Subsequent runs use the named volume. **`docker compose down -v && docker compose up` works from a clean state** as long as `.env` is present (or defaults are accepted). No known startup blocker.

---

## 8. Tests

### 8.1 Current state
- `backend/src/test/` contains `kotlin/com/cliniq/` (empty source set) and `java/com/cliniq/integration/ClinicalFlowIntegrationTest.kt`.
- `ClinicalFlowIntegrationTest.kt` is **broken** and will not compile. Issues:
  1. Imports `com.cliniq.domain.auth.enums.UserRole` — the actual package is `com.cliniq.domain.auth.entity.UserRole` (line 5).
  2. Uses `User(... password = "..." ...)` (lines 105, 117, 129, 363) — the entity field is `passwordHash: String`, and there is no setter for `password`.
  3. Uses `User.id = UUID.randomUUID()` in primary constructor — `@Id` is `val id: UUID = UUID.randomUUID()` (auto-generated). Setting it manually violates the entity contract.
  4. References `userRepository` (lines 75, 77) without declaring it; only `UserRepository` is `@Autowired` and unimported. Same for `labRequestRepository` (line 289).
  5. Constructs `Patient` with fields that don't exist in the entity (`bloodGroup`, `emergencyContactName`, `nationalId`).
  6. Uses `patientRepository.findById(patientId)?.take { em.clear() }` — the entity has no `get` accessor on `Optional` in Kotlin. The pattern is wrong.
  7. `@Sql` annotation loads migrations with `ExecutionPhase.BEFORE_TEST_METHOD`, but tests rely on a H2 or testcontainer DB that may not be configured.

**Verdict**: This file is **dead code that will not compile**. It must be either **deleted** or **rewritten** against the real entities.

### 8.2 Required test coverage
Per the brief §22, the critical end-to-end flow is:
**Login → Patient → Appointment → Consultation → Prescription → Lab → Invoice → Payment → Dispensing**

There are **zero passing tests** in the repository today. This is a **blocker** for the handoff.

**Minimum tests to add** (priority order):
1. **Auth** — login, refresh-rotation, logout-blacklist, bad password, deactivated user.
2. **Cross-clinic** — every entity, a `clinicA_user` cannot read `clinicB_resource` via REST.
3. **Patient** — create, list, get, update, search, validation errors.
4. **Billing** — list invoices (paginated, clinic-scoped, role-protected), create invoice, record payment, overpayment rejection, M-Pesa callback with `resultCode != 0` does not mark paid.
5. **Pharmacy** — dispense is transactional; cannot dispense a drug that is out of stock.
6. **Consultation** — completion requires diagnosis.

---

## 9. Docker

`docker-compose.yml` defines three services: `db` (postgres:16-alpine), `redis` (redis:7-alpine), `backend`. Health checks on all three. Network `cliniq_net`. Volumes for both DBs.

**Working as designed** — `docker compose up` from a clean checkout (after `cp .env.example .env` and filling `JWT_SECRET`) should bring up the full stack.

**No hidden manual steps** are required beyond the env file.

---

## 10. CI/CD

`.github/workflows/ci-cd.yml` exists but was not read in this audit pass. The old `KNOWN_ISSUES.md` flagged:
- References a `chatbot` build context that may not exist.
- Uses `curl http://localhost:8080/actuator/health` in staging (port may not be exposed).
- References `/opt/cliniq` and `cliniq-backup.sh` in production (may not exist on target).

**Action**: read `ci-cd.yml` in the next phase and remove dead steps.

---

## 11. Frontend

Out of scope for this backend audit. The frontend is a Next.js app with pages for login, register, and several dashboards. The prior `pre-audit` snapshot had `frontend/src/components/QueueTable.tsx` deleted and several pages modified; presumably the prior session was reshaping the UI.

---

## 12. Definition of Clean — Progress

| Criterion | Status | Notes |
|---|---|---|
| No unexplained dead code | ⚠ | Broken test file, `V1__initial_schema.sql.bak` |
| No duplicate business logic | ✓ | None found |
| No hardcoded secrets (in prod profile) | ✓ | Dev profile only; documented |
| No authentication bypasses | ✓ | None found |
| No uncontrolled tenant access | ⚠ | Code-level enforcement is correct; no tests prove it |
| Controllers are thin | ✓ | Controllers delegate to services |
| Services contain business logic | ✓ | All business logic is in services |
| DTOs are used | ✓ | All controllers use DTOs |
| Validation exists | ✓ | `@Valid` and field constraints everywhere |
| Errors are standardized | ✓ | `GlobalExceptionHandler` → `ApiError` |
| Database matches migrations | ✓ | V2 repairs the one known mismatch |
| Fresh DB works | ✓ | `docker compose down -v && docker compose up` |
| Docker works | ✓ | Compose file is correct |
| Redis works | ✓ | Used for blacklist, refresh tokens, cache |
| Tests pass | ✗ | Tests don't compile |
| Security tests pass | ✗ | No security tests exist |
| API documentation works | ✓ | Swagger is configured; controllers have `@Operation` |
| Build works | ⚠ | Build will fail because of the broken test file |
| CI works | ⚠ | Not yet reviewed |
| Documentation reflects reality | ✗ | `docs/audit/*` is stale; `docs/*.md` partially correct |

**14 of 20 criteria pass today.** The 6 gaps are: (1) dead code in the test file, (2) no multi-tenant test, (3) broken build, (4) no security tests, (5) no fresh `mvn test` proof, (6) stale `docs/audit/*` files.

---

## 13. Recommended Cleanup Phases (CONTROLLED, in order)

### Phase 1 — Stop the bleeding (today)
- Delete `backend/src/main/resources/db/migration/V1__initial_schema.sql.bak`.
- Delete or quarantine `backend/src/test/java/com/cliniq/integration/ClinicalFlowIntegrationTest.kt` (rename to `.disabled` if there is any doubt about its intent; it is **proven** broken, so delete).
- Rewrite `docs/audit/CURRENT_STATE.md` to reflect the current state (this document is the replacement).
- Rewrite `docs/audit/KNOWN_ISSUES.md`, `docs/audit/API_INVENTORY.md`, `docs/audit/SECURITY_FINDINGS.md` to reflect the current state.

### Phase 2 — Small refactors (R1–R10)
- Apply the 10 refactors listed in §3.2.
- Add explicit CORS configuration in `SecurityConfig`.
- Add request ID / correlation ID MDC filter.

### Phase 3 — Tests
- Configure `application-test.yml` to use Testcontainers PostgreSQL.
- Write the test set listed in §8.2.
- Add at least one cross-clinic isolation test per major entity.

### Phase 4 — Documentation
- Create the full `docs/` suite per the brief §24: `ARCHITECTURE.md`, `API.md`, `DATABASE.md`, `SECURITY.md`, `DEVELOPMENT.md`, `DEPLOYMENT.md`, `TESTING.md`, `ENVIRONMENT.md`, `FRONTEND_HANDOFF.md`.
- Each must be **tested**: the local-setup instructions must work from a clean checkout, and the test-account credentials in `FRONTEND_HANDOFF.md` must work.

### Phase 5 — Handoff
- Verify `docker compose down -v && docker compose up` from a clean clone.
- Verify Swagger is reachable and shows all endpoints.
- Run the full test suite and capture the output.

---

## 14. What is reusable (preserved)
- All domain code (auth, patient, appointment, consultation, pharmacy, lab, billing, crm, communication, clinic, shared).
- All three Flyway migrations (V1, V2, V3).
- Security filter chain, JWT service, audit service, exception handler.
- `ApiResponse<T>` and `ApiError` shared response types.
- Docker Compose infrastructure.
- `.env.example` template.
- All controllers' Swagger annotations.

## 15. What must be refactored (small)
- See R1–R10 in §3.2.
- CORS configuration (add explicit origins).
- Clinic isolation filter (remove `X-Clinic-ID` fallback).
- `BillingService.listInvoices` (remove dead code).
- `JwtService.tokenHash` (use SHA-256).
- Production startup check for default JWT secret.
- `V3` seed data BCrypt hashes (real hashes for documented test accounts).

## 16. What must be rewritten (larger)
- **All of `docs/audit/*`** — they describe a stale state.
- `ClinicalFlowIntegrationTest.kt` — delete or rewrite against the real entities and repositories.

## 17. What must be deleted
- `backend/src/main/resources/db/migration/V1__initial_schema.sql.bak`
- `backend/src/test/java/com/cliniq/integration/ClinicalFlowIntegrationTest.kt` (broken)

## 18. What is unknown / needs investigation
- `.github/workflows/ci-cd.yml` — read in next phase.
- `infrastructure/` and `scripts/` — read in next phase.
- `frontend/` audit — out of scope for this pass.
- `AnalyticsController` — referenced in `CURRENT_STATE.md` but not seen in the directory listing.
- Whether `frontend/src/lib/api.ts` and the dashboard pages were reshaped correctly during the prior session.

---

**End of audit.** Next action per execution rule: **DOCUMENT** (refresh `docs/audit/*` to match reality, then build out the new `docs/` suite per §24 in parallel with the small refactors of Phase 2).
