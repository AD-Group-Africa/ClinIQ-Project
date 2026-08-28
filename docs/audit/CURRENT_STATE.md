# ClinIQ Backend — Historical State Audit (SUPERSEDED)

> **This document is HISTORICAL. It described a `pre-audit` state that was the baseline before the previous refactor session. Several issues listed here are already resolved. The current authoritative audit is [`CODEBASE_AUDIT.md`](./CODEBASE_AUDIT.md).**

**Original Audit Date**: 2026-08-22
**Base Tag**: `pre-audit` (`0da5ae3` — `MVP: complete backend + basic frontend for ClinIQ`)
**Superseded By**: [`CODEBASE_AUDIT.md`](./CODEBASE_AUDIT.md) on 2026-08-28

## What has changed since this audit was written

| Item | Old status | Current status |
|---|---|---|
| `GET /api/v1/billing/invoices` | Listed as **MISSING** | **PRESENT** — see `backend/src/main/kotlin/com/cliniq/domain/billing/controller/BillingController.kt:140` |
| `booked_by` column on `appointments` | Listed as missing | **RESOLVED** by `V2__add_booked_by_to_appointments.sql` |
| Schema/entity mismatches | Several | Resolved in V2 |
| Refresh token rotation | Not configured | **IMPLEMENTED** — see `AuthService.refresh:122` |
| Token revocation on logout | Unconfirmed | **IMPLEMENTED** — see `AuthService.logout:127-134` |
| JWT signing default | Default secret in `application.yml` | Default still in `application.yml`, acceptable for dev only (see CODEBASE_AUDIT.md §5) |

## Original content (preserved for traceability)

The original audit was the pre-restructure inventory. It is preserved below so the diff between then and now is traceable.

<details>
<summary>Click to expand original content</summary>

- **Base Command**: `./gradlew build` (BUILD SUCCESSFUL)
- **Services**: PostgreSQL + Redis containers running
- **Running**: PostgreSQL `cliniq-postgres` (5432), Redis `cliniq-redis` (6379), Backend (when DB available)
- **Controllers**: Auth, Patient, Appointment, Billing, Lab, Pharmacy, Consultation, CRM, Analytics
- **Known issues**: default JWT secret, schema mismatches, dead code in root .kt files, no tests, CORS too permissive, no security headers, no rate limiting, placeholder `CHANGE_ME` values, no `application-prod.yml`

</details>

## See also
- [`CODEBASE_AUDIT.md`](./CODEBASE_AUDIT.md) — the current, evidence-based audit.
