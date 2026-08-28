# ClinIQ Backend — Historical Known Issues (SUPERSEDED)

> **This document is HISTORICAL. It described a `pre-audit` state. Most critical and high-priority issues listed here are already resolved. The current authoritative audit is [`CODEBASE_AUDIT.md`](./CODEBASE_AUDIT.md).**

**Original Audit Date**: 2026-08-22
**Superseded By**: [`CODEBASE_AUDIT.md`](./CODEBASE_AUDIT.md) on 2026-08-28

## Status of the originally listed issues

| # | Original issue | Current status | Evidence |
|---|---|---|---|
| 1 | `GET /api/v1/billing/invoices` missing | **RESOLVED** | `BillingController.kt:140` |
| 2 | Database role/model mismatch | **RESOLVED** | `AuthService` and JWT embed role; `UserRole` enum is mapped via `JwtAuthenticationToken` |
| 3 | `booked_by` column missing | **RESOLVED** | `V2__add_booked_by_to_appointments.sql` |
| 4 | Seed data missing | **RESOLVED** | `V3__seed_data.sql` (note: BCrypt hashes in seed need review) |
| 5 | Production configuration incomplete | **PARTIALLY RESOLVED** | Env-driven config exists; no `application-prod.yml`; startup check recommended (R2) |
| 6 | CI/CD pipeline broken | **NOT YET VERIFIED** | `.github/workflows/ci-cd.yml` not yet reviewed |
| 7 | Security not fully audited | **PARTIALLY RESOLVED** | See CODEBASE_AUDIT.md §5 |
| 8 | API documentation outdated | **PARTIALLY RESOLVED** | New `API_INVENTORY.md` needed |
| 9 | Dead code in root .kt files | **RESOLVED** | Root .kt files were removed in BASELINE commit |
| 10 | Database role issue after resets | **LIKELY RESOLVED** | `docker-compose.yml` recreates role on `up` from env vars |
| 11 | Deployment scripts not fully reliable | **NOT YET VERIFIED** | `scripts/` not yet reviewed |
| 12 | Redis usage unclear | **RESOLVED** | Used for refresh-token storage, blacklist, cache |
| 13–20 | Low-priority issues | Various | See CODEBASE_AUDIT.md |

## New issues identified by the current audit (2026-08-28)

- `backend/src/test/java/com/cliniq/integration/ClinicalFlowIntegrationTest.kt` is **broken** (will not compile). Must be deleted or rewritten.
- `backend/src/main/resources/db/migration/V1__initial_schema.sql.bak` is dead and should be removed.
- Cross-clinic isolation is enforced in code but has no automated test coverage.
- CORS is left to Spring's default — needs an explicit `CorsConfigurationSource` bean.
- `ClinicIsolationFilter` still allows the absence of `X-Clinic-ID` to fall through to the JWT's clinicId — for non-SUPER_ADMIN, missing `X-Clinic-ID` should not bypass the filter.

## See also
- [`CODEBASE_AUDIT.md`](./CODEBASE_AUDIT.md) — the current, evidence-based audit.
