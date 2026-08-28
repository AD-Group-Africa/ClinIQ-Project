# ClinIQ Backend — Current State Audit

**Audit Date**: 2026-08-22
**Base Tag**: `pre-audit`
**Base Command**: `./gradlew build` (BUILD SUCCESSFUL)
**Services**: PostgreSQL + Redis containers running

## Build & Runtime Status

- `./gradlew build` — **PASSES** cleanly
- `./gradlew bootRun` — Fails without PostgreSQL; succeeds when DB is available
- Flyway migration `V1__initial_schema.sql` — successfully applied on fresh DB
- Docker Compose — build failed due to `gradlew` path issue (Windows `.bat` vs Unix `gradlew`)

## Running Services

| Service | Status | Notes |
|---------|--------|-------|
| PostgreSQL | Running | `cliniq-postgres` container, port 5432 |
| Redis | Running | `cliniq-redis` container, port 6379 |
| Backend App | Running (with DB) | `bootRun` works with PostgreSQL up |

## Inventory Summary

### Controllers (api/v1)
- `AuthController` → `/api/v1/auth/*`
- `PatientController` → `/api/v1/patients/*`
- `AppointmentController` → `/api/v1/appointments/*`
- `BillingController` → `/api/v1/billing/*`
- `LabController` → `/api/v1/lab/*`
- `PharmacyController` → `/api/v1/pharmacy/*`
- `ConsultationController` → `/api/v1/consultations/*`
- `CrmController` → `/api/v1/crm/*`
- `AnalyticsController` → `/api/v1/analytics/*`

### Domains
- `auth` — Auth, JWT, RBAC, User model
- `patient` — Patient management (CRD)
- `appointment` — Appointment scheduling
- `billing` — Invoices, payments, M-Pesa integration
- `pharmacy` — Drugs, inventory, dispensing (FEFO)
- `lab` — Lab requests, processing, results
- `consultation` — Clinical consultations
- `crm` — Campaigns, contacts
- `communication` — SMS, WhatsApp notifications

### Key Configuration Files
- `backend/src/main/resources/application.yml` — Main config
- `backend/src/main/resources/application-dev.yml` — Dev profile
- `backend/src/main/resources/application-test.yml` — Test profile
- `.env.example` — Has `CHANGE_ME` placeholders for all secrets
- `backend/src/main/resources/db/migration/V1__initial_schema.sql` — Single Flyway migration

### Known Configuration Issues
- `JWT_SECRET` defaults to `cliniq_jwt_secret_minimum_64_chars_long_change_in_production_now`
- `DATABASE_PASSWORD` defaults to `cliniq_dev_password`
- `REDIS_PASSWORD` defaults to `redis_dev_password`
- Africa's Talking, M-Pesa, WhatsApp credentials are all `CHANGE_ME` or empty
- No `application-prod.yml` — production relies entirely on environment variables

### Database Inventory ( Flyway: V1__initial_schema.sql applied )

#### Tables Created
- `clinics`, `users`, `audit_logs`
- `patients`, `doctors`
- `appointments`, `doctor_schedules`, `schedule_overrides`, `reminders`
- `consultations`, `prescriptions`
- `drugs`, `suppliers`, `drug_batches`, `drug_dispenses`
- `lab_tests`, `lab_requests`, `lab_results`
- `invoices`, `payments`
- `communications`, `crm_campaigns`

#### Indexes (12 created)
- `idx_patients_clinic_phone`
- `idx_appointments_clinic_date`
- `idx_appointments_status`
- `idx_drug_batches_drug`
- `idx_drug_batches_expiry`
- `idx_lab_requests_patient`
- `idx_lab_requests_status`
- `idx_consultations_patient`
- `idx_invoices_clinic_patient`
- plus view `v_drug_stock`

#### ⚠️ Schema Mismatches (Hibernate validation errors)
- **Appointment entity** expects column `booked_by` — not in migration
- **Appointment entity** expects column `booked_via` — present as `booked_via` in entity, verified in code
- Entity-column vs migration discrepancies need reconciliation

### Security Status
- JWT authentication configured (AuthController, JwtService, SecurityConfig)
- `@EnableMethodSecurity` used (JSR-305 role annotations)
- Role values: `SUPER_ADMIN`, `CLINIC_ADMIN`, `DOCTOR`, `NURSE`, `RECEPTIONIST`, `FINANCE`, `PATIENT`
- **No explicit `GrantedAuthority` mapper** configured — relies on Spring Security defaults
- Clinic isolation filter referenced in `SecurityConfig` but needs verification
- CORS configured for `/api/**` — may be too permissive
- BCrypt password encoding configured via Spring Security auto-configuration

### Dead Code / Duplicates
- Root-level `.kt` files contain overlapping code:
  - `CLINIQ_AUTH_MODULE.kt` — Auth module skeleton
  - `CLINIQ_ENTITY_FIXES.kt` — Entity fixes and service completions
  - `CLINIQ_MISSING_PIECES.kt` — Compilation blockers fix
  - `CLINIQ_BACKEND_CORE.kt` — Core backend code
- Duplicate entity definitions across root files and `backend/src/main/kotlin`
- Several methods documented as comments in fix files but not integrated into main source

### Tests
- `./gradlew test` — No test source found (`NO-SOURCE`)
- Test profile relies on `application-test.yml` settings
- No integration tests configured yet

### Integrations
- **M-Pesa** — STK push, callback; credentials in `.env.example` are placeholders
- **Africa's Talking** — Service class exists; depends on `com.africastalking:core:3.4.4`
- **WhatsApp** — Service class exists; credentials in `.env.example` are placeholders
- **Redis** — Configured as cache type; no explicit cache managers beyond Spring Boot auto-configuration

### OpenAPI / Swagger
- SpringDoc configured: `swagger-ui.path: /swagger-ui.html`, `api-docs.path: /v3/api-docs`
- App not yet running with full config to verify endpoints auto-generated