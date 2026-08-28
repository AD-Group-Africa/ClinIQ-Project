# ClinIQ Backend

ClinIQ is a clinic / small-to-medium hospital management platform built for the African healthcare market. This repository contains the **backend** — a modular monolith that is the system of record for patients, appointments, consultations, pharmacy, laboratory, billing, payments and clinic administration.

The backend is designed to be:
- **Secure** — JWT auth, BCrypt, RBAC, clinic-scoped multi-tenancy.
- **Multi-tenant** — every record is scoped to a clinic; the tenant context comes from the signed JWT, never from client headers.
- **API-contract driven** — DTOs on every endpoint, stable response envelopes, Swagger/OpenAPI generated from code.
- **Ready to grow** — modular domain packages, a provider-agnostic payment boundary, and a separate AI service boundary.

## Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0 |
| Framework | Spring Boot 3.3 |
| Database | PostgreSQL 16 + Flyway |
| Cache / tokens | Redis 7 |
| Auth | JWT (access + rotating refresh) + BCrypt |
| API docs | springdoc OpenAPI (`/swagger-ui.html`, `/v3/api-docs`) |
| Build | Gradle 8.5 (Java 21) |

## Quick Start

```bash
# 1. Start infrastructure (PostgreSQL + Redis)
docker compose up -d

# 2. Configure environment
cp .env.example .env   # fill in JWT_SECRET etc.

# 3. Run the backend (dev profile uses port 8080)
cd backend && ./gradlew bootRun
```

- API base: `http://localhost:8080/api/v1/`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`

### Demo accounts (dev profile only)

Created at startup by `DemoUserSeeder` for the seeded `demo` clinic — password for all: **`Demo1234!`**

| Email | Role |
|---|---|
| `admin@cliniq.app` | SUPER_ADMIN |
| `doctor@cliniq.app` | DOCTOR |
| `nurse@cliniq.app` | NURSE |
| `reception@cliniq.app` | RECEPTIONIST |
| `finance@cliniq.app` | FINANCE |

## Environment Variables

All configuration is env-driven (`application.yml`). Copy `.env.example` and set:

| Variable | Required | Description |
|---|---|---|
| `DATABASE_URL` | yes (prod) | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/cliniq` |
| `DATABASE_USER` / `DATABASE_PASSWORD` | yes | DB credentials |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | yes | Redis for token blacklist + cache |
| `JWT_SECRET` | **yes** | ≥ 64 random chars (`openssl rand -base64 64`). Startup **refuses to run** in `prod` with a default value. |
| `FRONTEND_URL` | no | Allowed CORS origin (default `http://localhost:3000`) |
| `AT_API_KEY` / `AT_USERNAME` | no | Africa's Talking SMS |
| `MPESA_*` | no | M-Pesa Daraja (STK push) |
| `SPRING_PROFILES_ACTIVE` | no | `dev` (default), `test`, `prod` |

## Commands

```bash
cd backend
./gradlew build          # compile + run all tests
./gradlew test           # tests only
./gradlew bootRun        # run the app
```

**Tests** are integration tests that boot the full Spring context against disposable PostgreSQL + Redis:

- With `DATABASE_URL`/`REDIS_HOST` env vars set (CI, or `docker compose up`), tests run against that infrastructure.
- Otherwise Testcontainers starts disposable containers (requires Docker).

## Domains / Modules

```
com.cliniq
├── config            # Security, CORS, caching, demo seeder
├── domain
│   ├── auth          # identity: login, refresh rotation, logout, RBAC
│   ├── clinic        # clinic/tenant entity + repository
│   ├── patient       # registration, search, profile
│   ├── appointment   # appointments, queue, doctor schedules
│   ├── consultation  # encounters, notes, prescriptions
│   ├── pharmacy      # drugs, batches (FEFO), dispensing, stock alerts
│   ├── lab           # tests, requests, results
│   ├── billing       # invoices, payments, M-Pesa, revenue reports
│   ├── crm           # campaigns
│   ├── communication # SMS/notifications (no controller yet)
│   └── analytics     # dashboard/trends summaries
└── shared
    ├── audit         # async audit log (WHO/WHAT/WHEN)
    ├── context       # ThreadLocal clinic context
    ├── exceptions    # domain exceptions + GlobalExceptionHandler
    ├── filter        # RequestIdFilter, ClinicIsolationFilter
    └── response      # ApiResponse<T> / ApiError envelope
```

## Database

Flyway migrations live in `backend/src/main/resources/db/migration/`:

- `V1__initial_schema.sql` — full schema
- `V2__add_booked_by_to_appointments.sql` — appointment booking metadata
- `V3__seed_data.sql` — demo clinic + sample patient (demo users are created at runtime by `DemoUserSeeder`, not in SQL)
- `V4__add_clinic_subscription_expiry.sql` — clinic software-subscription expiry (SaaS readiness)

A fresh database is fully reproducible from these migrations. **Never edit an applied migration** in production; add a new `Vn` instead.

## Documentation

- [API.md](docs/API.md) — endpoint inventory, conventions, error format, pagination
- [ARCHITECTURE.md](docs/ARCHITECTURE.md) — system design, tenancy, payment & AI boundaries
- [SECURITY.md](docs/SECURITY.md) — security model and operational rules

## Team Boundaries

- **Backend (Harrison)** — this repository.
- **AI (Alvin)** — separate service (Python/FastAPI) that calls the backend API; the backend remains the system of record.
- **Frontend (Yvonne)** — builds against the OpenAPI spec and the DTOs in this repo; no database access.

---

*ClinIQ is an AD Group product.*
