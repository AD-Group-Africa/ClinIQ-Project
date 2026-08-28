# ClinIQ Architecture

## System Overview

ClinIQ is a **modular monolith**: one deployable Spring Boot application with clear domain packages. This is deliberate — a real clinic platform needs a coherent transaction boundary across clinical, pharmacy, lab and billing workflows before any microservice split is justified.

```
Frontend (Next.js) ──┐
Patient Portal ──────┤   HTTPS / JSON
Staff Workspaces ────┤
                     ▼
        ┌───────────────────────────┐
        │   ClinIQ Backend (Kotlin) │
        │   Spring Boot 3           │
        │                           │
        │  auth · patient · appoint │
        │  consultation · pharmacy  │
        │  lab · billing · crm      │
        │  analytics · audit        │
        └───────┬───────────┬───────┘
                │           │
        ┌───────▼───┐  ┌────▼────────┐
        │ PostgreSQL │  │ Redis       │
        │ (system of │  │ tokens+cache│
        │  record)   │  └────┬────────┘
        └───────────┘       │
                │            │
        ┌───────▼────────────▼────────┐
        │ M-Pesa · AT SMS · (future:  │
        │ Stripe, PayPal, WhatsApp)   │
        └────────────────────────────┘
```

## Layering (per domain)

Every domain follows the same vertical slice:

```
controller   → HTTP + DTOs + @PreAuthorize (thin)
service      → business logic, transactions, clinic scoping
repository   → Spring Data JPA (always filters by clinic_id)
entity       → JPA mapping (never exposed directly)
dto          → request/response contracts (stable for frontend)
```

**Rules enforced by convention:**
- Controllers never contain business logic.
- Entities are never returned by controllers — always mapped to DTOs.
- Every service method that touches tenant data takes `clinicId` and passes it to the repository.
- Every repository query filters by `clinicId`.

## Layered Request Flow

```
RequestIdFilter (MDC + X-Request-Id)
  → SecurityFilterChain
      → JwtAuthenticationFilter      (parse + validate Bearer JWT, check Redis blacklist)
      → ClinicIsolationFilter        (bind ClinicContext from JWT, verify X-Clinic-ID)
      → @PreAuthorize (role check)
      → Controller → Service → Repository → PostgreSQL
  → GlobalExceptionHandler (standardized ApiError)
```

The JWT and clinic-isolation filters are **constructed inside the security chain** (not Spring beans) so they never run as servlet filters ahead of the chain — this keeps the SecurityContext they set from being cleared.

## Multi-Tenancy

**Tenant = Clinic.** The tenant context is derived **only** from the signed JWT (`clinicId` claim):

1. `JwtAuthenticationFilter` parses the token and builds `JwtAuthenticationToken(userId, email, clinicId, role)`.
2. `ClinicIsolationFilter` sets a ThreadLocal `ClinicContext`:
   - Non-`SUPER_ADMIN`: clinic = JWT clinic; a mismatched `X-Clinic-ID` header → `403`.
   - `SUPER_ADMIN`: may select a clinic via `X-Clinic-ID` (still only a validated selector).
3. Controllers pass `auth.clinicId!!` to services; repositories filter by it.

**Guarantees:**
- A client can never escalate to another clinic by sending a header.
- A cross-clinic lookup returns `404` (existence is not leaked).
- `SUPER_ADMIN` is the only role that can span clinics, and only via explicit header selection.

## Payment Architecture (provider-agnostic)

Billing is **not** coupled to M-Pesa. Today:

- `PaymentMethod` enum: `CASH, MPESA, CARD, INSURANCE, BANK_TRANSFER, ONLINE`
- Manual payments recorded server-side via `POST /billing/payments`.
- M-Pesa STK push via `MpesaService`; only the **webhook** (`/billing/mpesa/callback`) with `ResultCode == 0` can mark an M-Pesa payment paid.
- Revenue reports aggregate by method.

**Future providers** (Stripe, PayPal, other African PSPs) plug in behind a `PaymentProvider` abstraction without touching invoice logic. Webhooks must remain idempotent and server-verified; the frontend can never assert payment success.

**Financial domains are separate:**
- *Clinic → pays ClinIQ*: software subscription (`clinics.subscription_tier`, `subscription_expires_at`).
- *Patient → pays clinic*: invoices/payments in the `billing` domain.

## AI Integration Boundary (for Alvin)

The AI service is a **separate process** (Python/FastAPI) that talks to this backend's API. The backend remains the system of record.

```
Frontend ──► ClinIQ Backend ──► PostgreSQL
                    ▲
                    │ (HTTP + service-to-service auth)
              AI Service (FastAPI)
              - patient assistant
              - appointment assistance
              - clinical documentation assistance
              - operational intelligence / anomaly / outbreak signals
```

**Rules:**
- AI never writes clinical records directly; it can only call backend workflows (e.g., create an appointment through the same API a receptionist uses).
- AI outputs are recommendations, not clinical truth.
- The backend enforces authorization, clinic scoping, payment and business rules on every AI-initiated request.

This makes the future patient flow possible: *"Book me a consultation tomorrow afternoon"* → AI reads slots via the appointments API → patient confirms → backend creates the appointment → billing/payment flow runs server-side → notification is sent.

## Health Programs (future)

Health programs (HIV, maternal, immunization, chronic disease, TB) will be **configurable** — a `health_programs` catalog with per-patient enrollments, care plans and follow-ups. Programs add rows in program-specific tables keyed by `(clinic_id, patient_id)`; `patients` stays lean. Sensitive programs get stricter authorization. Nothing is hardcoded into the patient entity.

## Audit

`AuditService` (async, `REQUIRES_NEW`) writes `audit_logs` rows: `WHO` (user) · `WHAT` (action) · `TO WHICH RECORD` (entity_type/id) · `WHEN` (created_at) · `FROM WHERE` (ip) · `CHANGED` (old/new JSON). It is called from auth, patient, billing and other sensitive paths. Log lines never include passwords, tokens or clinical payloads.

## Observability

- `RequestIdFilter` sets `X-Request-Id` and an SLF4J MDC `requestId`; the log pattern includes it.
- Actuator health/info/metrics/prometheus are exposed; health shows DB + Redis.
- Redis is used for: access-token blacklist (SHA-256 of token), refresh-token store (rotation), and domain caches.

## Repository Layout

```
backend/src/main/kotlin/com/cliniq
├── config/         SecurityConfig, CacheConfig, WebConfig, DemoUserSeeder
├── domain/
│   ├── auth        identity + RBAC + JWT
│   ├── clinic      tenant entity
│   ├── patient
│   ├── appointment (+ queue, schedules)
│   ├── consultation
│   ├── pharmacy
│   ├── lab
│   ├── billing
│   ├── crm
│   ├── communication
│   └── analytics
└── shared/
    ├── audit
    ├── context
    ├── exceptions
    ├── filter
    └── response
```

## Database

Flyway migrations (`backend/src/main/resources/db/migration/`): V1 schema → V2 appointments booking columns → V3 demo seed → V4 clinic subscription expiry. A fresh database is reproducible end-to-end from these files; seed/demo data is clearly separated from production configuration (demo users are created at runtime by `DemoUserSeeder` with real BCrypt hashes, dev profiles only).