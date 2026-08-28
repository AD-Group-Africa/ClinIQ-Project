# ClinIQ Backend — Known Issues Audit

## Critical Issues (P0)

### 1. Missing endpoint: GET /api/v1/billing/invoices
- **Controller**: `BillingController.kt`
- **Missing**: List endpoint for invoices
- **Existing endpoints**:
  - `POST /api/v1/billing/invoices` (create invoice)
  - `GET /api/v1/billing/invoices/{id}` (get by ID)
  - `POST /api/v1/billing/payments` (record payment)
  - `POST /api/v1/billing/mpesa/initiate` (M-Pesa STK push)
  - `POST /api/v1/billing/mpesa/callback` (M-Pesa webhook)
  - `GET /api/v1/billing/reports/revenue` (revenue report)
- **Impact**: Clinic staff cannot list invoices via API
- **Fix**: Add `@GetMapping("/invoices")` with pagination support

### 2. Database role/model mismatch
- **Issue**: `users.role` VARCHAR column expects values `SUPER_ADMIN`, `CLINIC_ADMIN`, etc., but Spring Security `@PreAuthorize("hasAnyRole('...')")` requires either:
  - A separate `roles` table with `authorities` approach, OR
  - Role values matching Spring Security's `GrantedAuthority` format
- **Current DB values**: `SUPER_ADMIN`, `CLINIC_ADMIN`, `DOCTOR`, `NURSE`, `RECEPTIONIST`, `FINANCE`, `PATIENT`
- **Impact**: RBAC may not enforce correctly; role-based access may fail silently
- **Fix**: Align role model — either implement `Role` entity or map DB values to authorities

### 3. Schema/entity mismatch — `booked_by` column
- **Entity**: `Appointment.bookedBy` mapped to column `booked_by`
- **Migration**: `V1__initial_schema.sql` does NOT have `booked_by` column in `appointments` table
- **Impact**: `SchemaValidationException` on app startup; JPA cannot build SessionFactory
- **Impact Level**: **BLOCKER** — app cannot start with fresh database
- **Fix**: Add `booked_by` column to Flyway migration, or update entity to match

### 4. Seed data missing
- **Issue**: No default clinic or admin user seeded via Flyway
- **Impact**: Fresh deployments have no baseline data; no demo clinic to work with
- **Missing**: `V2__seed_data.sql` with clinic + admin user
- **Fix**: Create Flyway seed migration

### 5. Production configuration incomplete
- **`.env.example`** has `CHANGE_ME` placeholders for all secrets:
  - `DB_PASSWORD=CHANGE_ME_strong_password`
  - `REDIS_PASSWORD=CHANGE_ME_redis_password`
  - `JWT_SECRET=CHANGE_ME_minimum_64_chars_random_string_here_replace_now`
  - `AT_API_KEY=your_api_key_from_africastalking_com`
  - `MPESA_CONSUMER_KEY=from_developer_safaricom_co_ke`
  - `MPESA_CONSUMER_SECRET=from_developer_safaricom_co_ke`
  - `MPESA_PASSKEY=from_developer_portal`
  - `WHATSAPP_VERIFY_TOKEN=CHANGE_ME_random_token`
- **No `application-prod.yml`** — production relies entirely on env vars
- **Impact**: Deployment cannot proceed without manual config

## Medium-Priority Issues (P1)

### 6. CI/CD pipeline broken
- **File**: `.github/workflows/ci-cd.yml`
- **Concerns**:
  1. References `chatbot` build context; chatbot directory structure may be incomplete
  2. Staging deploy uses `curl -sf http://localhost:8080/actuator/health` — port may not be exposed on staging
  3. Production deploy references `/opt/cliniq` path and `cliniq-backup.sh` — may not exist on target servers
  4. No database migration step in CI/CD pipeline (Flyway runs separately)
  5. `test-backend` job uses in-memory postgres service; dialect issues possible

### 7. Security not fully audited
- **JWT secret** uses default dev value — must not go to production
- **No rate limiting** evidence in runtime config
- **CORS** may be too permissive (`/api/**` allowed)
- **Object-level authorization** — users can only access their own clinic's data — not explicitly tested
- **Refresh token rotation** — not configured
- **Redis blacklisting/revocation** for logout — unconfirmed

### 8. API documentation outdated
- **File**: `CLINIQ_PRODUCTION_DOCS.md` (914 lines)
- **Mismatch**: Docs list endpoints that don't match actual controller annotations
- **Example**: Docs show `GET /api/v1/billing/invoices` as available, but controller is missing this endpoint
- **Impact**: Developers misled about available API surface

### 9. Dead code and duplicates across root `.kt` files
- **Files**: `CLINIQ_AUTH_MODULE.kt`, `CLINIQ_ENTITY_FIXES.kt`, `CLINIQ_MISSING_PIECES.kt`, `CLINIQ_BACKEND_CORE.kt`
- **Issues**:
  - Overlapping/similar code in multiple files
  - Duplicate entity/service definitions
  - Methods documented as comments in fix files but not integrated into main source
  - Confusion about which file contains the "truth"

### 10. Database role issue after resets
- **Problem**: After Docker restarts, database role/volume requires manual intervention
- **Impact**: `docker compose down -v && docker compose up` does not work from zero without manual steps
- **Fix**: Ensure Docker Compose starts from zero without manual intervention

### 11. Deployment scripts not fully reliable
- **Files**: Various scripts in `devops/scripts/`
- **Issues**: May reference hardcoded paths, missing dependencies, or unverified sequences

### 12. Redis usage unclear
- **Purpose**: Unclear if Redis is used for:
  - Token blacklisting/revocation (logout)
  - Caching (HTTP responses, frequent reads)
  - Rate limiting
- **Impact**: Potential misconfiguration, security gaps

## Low-Priority Issues (P2)

### 13. API documentation out of date
- See item 8 above

### 14. N+1 query risks and transaction issues
- **Risk**: Unoptimized JPA queries could cause performance issues at scale
- **Locations**: Multiple repository interfaces without custom query optimization

### 15. Hardcoded debug code and TODOs
- **Locations**: Various source files
- **Examples**: Debug println statements, TODO/FIXME comments left in production code

### 16. No `application-prod.yml`
- Production settings rely entirely on environment variables
- No dedicated production configuration file

### 17. No default seed data in Flyway
- See item 4 above

### 18. Multiple root-level `.kt` files with overlapping code
- See item 11 above

### 19. CORS configuration may be too permissive
- Allows `/api/**` without strict origin restrictions

### 20. Flyway `validate-on-migrate: true` without `baseline-on-migrate`
- May fail on existing databases without proper baseline setup