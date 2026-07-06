# ClinIQ — Clinic Management & Patient Operations Platform
## Master Engineering Document v1.0
### Production-Grade | SaaS-Ready | Commercially Deployable

---

## TABLE OF CONTENTS

1. [System Architecture](#step-1--system-architecture)
2. [Monorepo Structure](#step-2--monorepo-structure)
3. [Database Design](#step-3--database-design)
4. [Security Implementation](#step-4--security-implementation)
5. [Backend Implementation](#step-5--backend-implementation)
6. [Appointment Engine](#step-6--appointment-engine-implementation)
7. [Frontend Implementation](#step-7--frontend-implementation)
8. [Chatbot & Communication](#step-8--chatbot--communication-system)
9. [DevOps & Deployment](#step-9--devops--deployment)
10. [Commercialization Strategy](#step-10--commercialization-strategy)
11. [MVP Execution Plan](#step-11--mvp-execution-plan)

---

# STEP 1 — SYSTEM ARCHITECTURE

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        PUBLIC INTERNET                               │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                    ┌────────▼────────┐
                    │   Nginx Reverse  │
                    │   Proxy (TLS)    │
                    └────────┬────────┘
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
  ┌───────▼──────┐  ┌────────▼───────┐  ┌──────▼──────────┐
  │  Next.js     │  │  Spring Boot   │  │  Chatbot Service │
  │  Frontend    │  │  REST API      │  │  (Node/FastAPI)  │
  │  :3000       │  │  :8080         │  │  :5000           │
  └──────────────┘  └────────┬───────┘  └─────────────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
     ┌────────▼───┐  ┌───────▼────┐  ┌─────▼──────┐
     │ PostgreSQL │  │   Redis    │  │  MinIO     │
     │ :5432      │  │   :6379    │  │  (Files)   │
     └────────────┘  └────────────┘  └────────────┘
```

## Backend/Frontend Communication Flow

```
Client (Next.js)
    │
    ├── GET/POST /api/v1/appointments  → AppointmentController
    ├── GET/POST /api/v1/patients      → PatientController
    ├── GET/POST /api/v1/billing       → BillingController
    ├── WS /ws/notifications           → NotificationWebSocket
    │
    └── All requests carry:
        Authorization: Bearer <JWT>
        X-Clinic-ID: <clinic_uuid>
        X-Request-ID: <trace_id>
```

## Clinic Workflow Architecture

```
Patient Journey:
  Web/WhatsApp → Book Appointment → Confirmation SMS/Email
      → Queue Check-in → Consultation → Prescription
      → Invoice → Payment → Follow-up CRM

Staff Workflow:
  Reception: Check-in → Queue → Assign Doctor
  Doctor: View Queue → Consult → Notes/Prescriptions → Discharge
  Finance: Invoice → Payment → Reports
  Admin: Analytics → CRM Campaigns → Staff Management
```

## Modular System Boundaries

| Module | Responsibility | External Dependencies |
|--------|---------------|----------------------|
| Auth Module | JWT, RBAC, sessions | Redis |
| Appointment Module | Booking, scheduling, queue | Notification Module |
| Patient Module | Records, history, docs | MinIO |
| Billing Module | Invoices, payments | M-Pesa API |
| CRM Module | Campaigns, retention | Communication Module |
| Communication Module | SMS, WhatsApp, Email | Africa's Talking, Twilio |
| Chatbot Module | FAQ, booking, escalation | OpenAI (optional) |
| Analytics Module | Dashboards, reports | PostgreSQL views |

## Scalability Strategy

```
Phase 1 (MVP): Single VPS — Hetzner CX21
  - 2 vCPU, 4GB RAM
  - Docker Compose
  - ~50 concurrent clinics

Phase 2 (Growth): Hetzner CX41 or Railway
  - Horizontal Spring Boot scaling
  - Redis clustering
  - Read replicas for PostgreSQL
  - ~500 concurrent clinics

Phase 3 (Scale): Kubernetes-ready
  - Helm charts prepared
  - Microservice extraction if needed
  - CDN for static assets
```

---

# STEP 2 — MONOREPO STRUCTURE

```
cliniq/
├── backend/                          # Spring Boot (Kotlin)
│   ├── src/
│   │   └── main/
│   │       ├── kotlin/com/cliniq/
│   │       │   ├── CliniqApplication.kt
│   │       │   ├── config/
│   │       │   │   ├── SecurityConfig.kt
│   │       │   │   ├── RedisConfig.kt
│   │       │   │   ├── WebConfig.kt
│   │       │   │   └── JwtConfig.kt
│   │       │   ├── domain/
│   │       │   │   ├── auth/
│   │       │   │   ├── clinic/
│   │       │   │   ├── patient/
│   │       │   │   ├── appointment/
│   │       │   │   ├── billing/
│   │       │   │   ├── crm/
│   │       │   │   ├── communication/
│   │       │   │   └── analytics/
│   │       │   ├── shared/
│   │       │   │   ├── audit/
│   │       │   │   ├── exceptions/
│   │       │   │   ├── middleware/
│   │       │   │   └── utils/
│   │       │   └── infrastructure/
│   │       │       ├── persistence/
│   │       │       ├── messaging/
│   │       │       └── storage/
│   │       └── resources/
│   │           ├── application.yml
│   │           ├── application-dev.yml
│   │           ├── application-prod.yml
│   │           └── db/migration/
│   │               ├── V1__init_schema.sql
│   │               ├── V2__seed_roles.sql
│   │               └── V3__indexes.sql
│   ├── build.gradle.kts
│   └── Dockerfile
│
├── frontend/                         # Next.js (TypeScript)
│   ├── src/
│   │   ├── app/
│   │   │   ├── (auth)/
│   │   │   │   ├── login/page.tsx
│   │   │   │   └── layout.tsx
│   │   │   ├── (dashboard)/
│   │   │   │   ├── admin/
│   │   │   │   ├── reception/
│   │   │   │   ├── doctor/
│   │   │   │   ├── finance/
│   │   │   │   └── layout.tsx
│   │   │   ├── (patient)/
│   │   │   │   ├── portal/
│   │   │   │   └── layout.tsx
│   │   │   └── (public)/
│   │   │       ├── [clinic]/
│   │   │       └── layout.tsx
│   │   ├── components/
│   │   │   ├── ui/           # ShadCN re-exports + custom
│   │   │   ├── appointments/
│   │   │   ├── patients/
│   │   │   ├── billing/
│   │   │   ├── crm/
│   │   │   ├── analytics/
│   │   │   └── shared/
│   │   ├── lib/
│   │   │   ├── api/          # TanStack Query hooks
│   │   │   ├── store/        # Redux Toolkit slices
│   │   │   ├── utils/
│   │   │   └── validations/
│   │   └── types/
│   ├── public/
│   ├── next.config.ts
│   ├── tailwind.config.ts
│   └── Dockerfile
│
├── chatbot/                          # Node.js chatbot service
│   ├── src/
│   │   ├── handlers/
│   │   ├── integrations/
│   │   └── knowledge-base/
│   ├── package.json
│   └── Dockerfile
│
├── devops/
│   ├── docker-compose.yml
│   ├── docker-compose.prod.yml
│   ├── nginx/
│   │   ├── nginx.conf
│   │   └── cliniq.conf
│   └── scripts/
│       ├── deploy.sh
│       ├── backup.sh
│       └── seed.sh
│
├── .github/
│   └── workflows/
│       ├── ci.yml
│       ├── cd-staging.yml
│       └── cd-production.yml
│
├── docs/
│   ├── api/                  # OpenAPI specs
│   ├── architecture/
│   └── onboarding/
│
├── .env.example
├── .env.dev
└── README.md
```

---

# STEP 3 — DATABASE DESIGN

## PostgreSQL Schema (Flyway V1__init_schema.sql)

```sql
-- =====================================================
-- EXTENSIONS
-- =====================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm"; -- for full-text search on patient names

-- =====================================================
-- CLINICS (Multi-tenant root)
-- =====================================================
CREATE TABLE clinics (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(200) NOT NULL,
    slug            VARCHAR(100) UNIQUE NOT NULL, -- subdomain/URL identifier
    phone           VARCHAR(20),
    email           VARCHAR(150),
    address         TEXT,
    city            VARCHAR(100),
    country         VARCHAR(50) DEFAULT 'KE',
    logo_url        TEXT,
    website         TEXT,
    timezone        VARCHAR(50) DEFAULT 'Africa/Nairobi',
    currency        VARCHAR(10) DEFAULT 'KES',
    subscription_tier VARCHAR(20) DEFAULT 'starter', -- starter | growth | pro
    subscription_expires_at TIMESTAMPTZ,
    is_active       BOOLEAN DEFAULT TRUE,
    settings        JSONB DEFAULT '{}',  -- clinic-level config
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- =====================================================
-- USERS (All roles)
-- =====================================================
CREATE TYPE user_role AS ENUM (
    'SUPER_ADMIN',
    'CLINIC_ADMIN',
    'DOCTOR',
    'NURSE',
    'RECEPTIONIST',
    'FINANCE',
    'PATIENT'
);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    email           VARCHAR(200) UNIQUE NOT NULL,
    phone           VARCHAR(20),
    password_hash   TEXT NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    role            user_role NOT NULL,
    avatar_url      TEXT,
    is_active       BOOLEAN DEFAULT TRUE,
    is_verified     BOOLEAN DEFAULT FALSE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- =====================================================
-- DOCTORS (extends users)
-- =====================================================
CREATE TABLE doctors (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    specialization  VARCHAR(150),
    qualification   VARCHAR(200),
    license_number  VARCHAR(100),
    bio             TEXT,
    consultation_fee DECIMAL(10,2) DEFAULT 0,
    is_available    BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- =====================================================
-- PATIENTS
-- =====================================================
CREATE TYPE gender_type AS ENUM ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY');
CREATE TYPE blood_group_type AS ENUM ('A+','A-','B+','B-','AB+','AB-','O+','O-','UNKNOWN');

CREATE TABLE patients (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL, -- optional portal access
    patient_number  VARCHAR(30) UNIQUE, -- e.g. CLQ-2024-0001
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(20) NOT NULL,
    email           VARCHAR(200),
    date_of_birth   DATE,
    gender          gender_type,
    national_id     VARCHAR(50),
    blood_group     blood_group_type DEFAULT 'UNKNOWN',
    allergies       TEXT[],
    emergency_contact_name  VARCHAR(200),
    emergency_contact_phone VARCHAR(20),
    insurance_provider      VARCHAR(200),
    insurance_number        VARCHAR(100),
    address         TEXT,
    notes           TEXT,
    tags            TEXT[],  -- for CRM segmentation
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- =====================================================
-- DOCTOR SCHEDULES
-- =====================================================
CREATE TABLE doctor_schedules (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    doctor_id       UUID REFERENCES doctors(id) ON DELETE CASCADE,
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    day_of_week     SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6), -- 0=Sunday
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    slot_duration_minutes INTEGER DEFAULT 30,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Schedule overrides (holidays, leave, etc.)
CREATE TABLE schedule_overrides (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    doctor_id       UUID REFERENCES doctors(id) ON DELETE CASCADE,
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    override_date   DATE NOT NULL,
    is_available    BOOLEAN DEFAULT FALSE,
    start_time      TIME,
    end_time        TIME,
    reason          VARCHAR(200),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- =====================================================
-- APPOINTMENTS
-- =====================================================
CREATE TYPE appointment_status AS ENUM (
    'SCHEDULED',
    'CONFIRMED',
    'CHECKED_IN',
    'IN_CONSULTATION',
    'COMPLETED',
    'CANCELLED',
    'NO_SHOW',
    'RESCHEDULED'
);

CREATE TYPE appointment_type AS ENUM (
    'CONSULTATION',
    'FOLLOW_UP',
    'PROCEDURE',
    'CHECK_UP',
    'WALK_IN',
    'TELECONSULTATION'
);

CREATE TABLE appointments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    patient_id      UUID REFERENCES patients(id) ON DELETE CASCADE,
    doctor_id       UUID REFERENCES doctors(id) ON DELETE SET NULL,
    appointment_date DATE NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    type            appointment_type DEFAULT 'CONSULTATION',
    status          appointment_status DEFAULT 'SCHEDULED',
    queue_number    INTEGER,
    chief_complaint TEXT,
    notes           TEXT,
    booked_by       UUID REFERENCES users(id),  -- receptionist/self
    booked_via      VARCHAR(30) DEFAULT 'PORTAL', -- PORTAL | CHATBOT | WHATSAPP | PHONE | WALK_IN
    reminder_sent   BOOLEAN DEFAULT FALSE,
    cancellation_reason TEXT,
    rescheduled_from UUID REFERENCES appointments(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- =====================================================
-- CONSULTATIONS (Clinical records)
-- =====================================================
CREATE TABLE consultations (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    appointment_id  UUID UNIQUE REFERENCES appointments(id) ON DELETE CASCADE,
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    patient_id      UUID REFERENCES patients(id) ON DELETE CASCADE,
    doctor_id       UUID REFERENCES doctors(id) ON DELETE SET NULL,
    chief_complaint TEXT,
    history         TEXT,
    examination     TEXT,
    diagnosis       TEXT,
    treatment_plan  TEXT,
    notes           TEXT,
    vitals          JSONB DEFAULT '{}',  -- {bp, temp, weight, height, spo2}
    follow_up_date  DATE,
    follow_up_notes TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- =====================================================
-- PRESCRIPTIONS
-- =====================================================
CREATE TABLE prescriptions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    consultation_id UUID REFERENCES consultations(id) ON DELETE CASCADE,
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    patient_id      UUID REFERENCES patients(id) ON DELETE CASCADE,
    doctor_id       UUID REFERENCES doctors(id) ON DELETE SET NULL,
    medications     JSONB NOT NULL DEFAULT '[]',
    -- [{name, dosage, frequency, duration, instructions}]
    notes           TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- =====================================================
-- INVOICES & PAYMENTS
-- =====================================================
CREATE TYPE invoice_status AS ENUM ('DRAFT','ISSUED','PAID','PARTIAL','OVERDUE','CANCELLED','REFUNDED');
CREATE TYPE payment_method AS ENUM ('CASH','MPESA','CARD','INSURANCE','BANK_TRANSFER','ONLINE');

CREATE TABLE invoices (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    patient_id      UUID REFERENCES patients(id) ON DELETE CASCADE,
    appointment_id  UUID REFERENCES appointments(id) ON DELETE SET NULL,
    invoice_number  VARCHAR(50) UNIQUE NOT NULL,
    issued_at       TIMESTAMPTZ DEFAULT NOW(),
    due_date        DATE,
    line_items      JSONB NOT NULL DEFAULT '[]',
    -- [{description, quantity, unit_price, total}]
    subtotal        DECIMAL(12,2) NOT NULL DEFAULT 0,
    discount        DECIMAL(12,2) DEFAULT 0,
    tax             DECIMAL(12,2) DEFAULT 0,
    total           DECIMAL(12,2) NOT NULL DEFAULT 0,
    paid_amount     DECIMAL(12,2) DEFAULT 0,
    balance         DECIMAL(12,2) GENERATED ALWAYS AS (total - paid_amount) STORED,
    status          invoice_status DEFAULT 'DRAFT',
    notes           TEXT,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE payments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    invoice_id      UUID REFERENCES invoices(id) ON DELETE CASCADE,
    patient_id      UUID REFERENCES patients(id),
    amount          DECIMAL(12,2) NOT NULL,
    method          payment_method NOT NULL,
    reference       VARCHAR(200),  -- Mpesa transaction code
    mpesa_phone     VARCHAR(20),
    notes           TEXT,
    paid_at         TIMESTAMPTZ DEFAULT NOW(),
    recorded_by     UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- =====================================================
-- COMMUNICATIONS
-- =====================================================
CREATE TYPE comm_channel AS ENUM ('SMS','WHATSAPP','EMAIL','IN_APP','PUSH');
CREATE TYPE comm_status AS ENUM ('PENDING','SENT','DELIVERED','FAILED','READ');
CREATE TYPE comm_type AS ENUM (
    'APPOINTMENT_REMINDER',
    'APPOINTMENT_CONFIRMATION',
    'APPOINTMENT_CANCELLATION',
    'FOLLOW_UP',
    'BILLING_REMINDER',
    'CAMPAIGN',
    'GENERAL'
);

CREATE TABLE communications (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    patient_id      UUID REFERENCES patients(id) ON DELETE CASCADE,
    channel         comm_channel NOT NULL,
    type            comm_type NOT NULL,
    subject         VARCHAR(300),
    body            TEXT NOT NULL,
    status          comm_status DEFAULT 'PENDING',
    external_ref    VARCHAR(200),  -- provider message ID
    sent_at         TIMESTAMPTZ,
    delivered_at    TIMESTAMPTZ,
    failed_reason   TEXT,
    appointment_id  UUID REFERENCES appointments(id) ON DELETE SET NULL,
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- =====================================================
-- REMINDERS (Scheduled)
-- =====================================================
CREATE TABLE reminders (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    appointment_id  UUID REFERENCES appointments(id) ON DELETE CASCADE,
    patient_id      UUID REFERENCES patients(id) ON DELETE CASCADE,
    send_at         TIMESTAMPTZ NOT NULL,
    channel         comm_channel NOT NULL,
    is_sent         BOOLEAN DEFAULT FALSE,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- =====================================================
-- CRM CAMPAIGNS
-- =====================================================
CREATE TYPE campaign_status AS ENUM ('DRAFT','SCHEDULED','RUNNING','COMPLETED','CANCELLED');

CREATE TABLE crm_campaigns (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    channel         comm_channel NOT NULL,
    target_segment  JSONB DEFAULT '{}',  -- filter criteria
    message_template TEXT NOT NULL,
    scheduled_at    TIMESTAMPTZ,
    status          campaign_status DEFAULT 'DRAFT',
    sent_count      INTEGER DEFAULT 0,
    delivered_count INTEGER DEFAULT 0,
    failed_count    INTEGER DEFAULT 0,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Campaign recipients (resolved at send time)
CREATE TABLE campaign_recipients (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    campaign_id     UUID REFERENCES crm_campaigns(id) ON DELETE CASCADE,
    patient_id      UUID REFERENCES patients(id) ON DELETE CASCADE,
    communication_id UUID REFERENCES communications(id) ON DELETE SET NULL,
    status          comm_status DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- =====================================================
-- AUDIT LOGS
-- =====================================================
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    action          VARCHAR(100) NOT NULL,  -- CREATE_APPOINTMENT, UPDATE_PATIENT, etc.
    entity_type     VARCHAR(100),
    entity_id       UUID,
    old_values      JSONB,
    new_values      JSONB,
    ip_address      INET,
    user_agent      TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- =====================================================
-- PATIENT DOCUMENTS
-- =====================================================
CREATE TABLE patient_documents (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    patient_id      UUID REFERENCES patients(id) ON DELETE CASCADE,
    consultation_id UUID REFERENCES consultations(id) ON DELETE SET NULL,
    name            VARCHAR(300) NOT NULL,
    file_url        TEXT NOT NULL,
    file_type       VARCHAR(50),
    file_size_kb    INTEGER,
    uploaded_by     UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- =====================================================
-- CHATBOT SESSIONS
-- =====================================================
CREATE TABLE chatbot_sessions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    channel         comm_channel DEFAULT 'WHATSAPP',
    external_id     VARCHAR(200),  -- WhatsApp number / socket ID
    patient_id      UUID REFERENCES patients(id) ON DELETE SET NULL,
    context         JSONB DEFAULT '{}',
    escalated       BOOLEAN DEFAULT FALSE,
    escalated_to    UUID REFERENCES users(id),
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE chatbot_messages (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id      UUID REFERENCES chatbot_sessions(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,  -- user | assistant
    content         TEXT NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
```

## Flyway V2__seed_roles.sql

```sql
-- Seed subscription tiers
-- Seed default system configuration
INSERT INTO clinics (name, slug, email, subscription_tier)
VALUES ('ClinIQ Demo Clinic', 'demo', 'demo@cliniq.app', 'pro');
```

## Flyway V3__indexes.sql

```sql
-- =====================================================
-- PERFORMANCE INDEXES
-- =====================================================

-- Patients: full-text search
CREATE INDEX idx_patients_fulltext ON patients
    USING GIN (to_tsvector('english', first_name || ' ' || last_name || ' ' || COALESCE(phone, '')));

CREATE INDEX idx_patients_clinic ON patients(clinic_id);
CREATE INDEX idx_patients_phone ON patients(clinic_id, phone);

-- Appointments: date-range queries (most common)
CREATE INDEX idx_appointments_date ON appointments(clinic_id, appointment_date, status);
CREATE INDEX idx_appointments_doctor ON appointments(doctor_id, appointment_date);
CREATE INDEX idx_appointments_patient ON appointments(patient_id, appointment_date DESC);

-- Communications: unprocessed reminders
CREATE INDEX idx_reminders_pending ON reminders(send_at, is_sent)
    WHERE is_sent = FALSE;

-- Audit logs
CREATE INDEX idx_audit_entity ON audit_logs(clinic_id, entity_type, entity_id, created_at DESC);

-- Invoices
CREATE INDEX idx_invoices_patient ON invoices(patient_id, status, created_at DESC);
CREATE INDEX idx_invoices_status ON invoices(clinic_id, status);
```

---

# STEP 4 — SECURITY IMPLEMENTATION

## SecurityConfig.kt

```kotlin
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthenticationFilter,
    private val clinicIsolationFilter: ClinicIsolationFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/api/v1/auth/**",
                    "/api/v1/public/**",
                    "/actuator/health",
                    "/ws/**"
                ).permitAll()
                .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(clinicIsolationFilter, JwtAuthenticationFilter::class.java)
            .exceptionHandling {
                it.authenticationEntryPoint(JwtAuthEntryPoint())
                it.accessDeniedHandler(CustomAccessDeniedHandler())
            }
            .headers {
                it.frameOptions { fo -> fo.deny() }
                it.contentSecurityPolicy { csp ->
                    csp.policyDirectives("default-src 'self'; frame-ancestors 'none'")
                }
            }
            .build()
    }

    @Bean
    fun passwordEncoder() = BCryptPasswordEncoder(12)

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOriginPatterns = listOf(
            "https://*.cliniq.app",
            "http://localhost:3000"
        )
        config.allowedMethods = listOf("GET","POST","PUT","PATCH","DELETE","OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        config.maxAge = 3600L
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/**", config)
        return source
    }
}
```

## JwtService.kt

```kotlin
@Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration-ms}") private val expirationMs: Long,
    @Value("\${jwt.refresh-expiration-ms}") private val refreshExpirationMs: Long,
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val key by lazy {
        Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))
    }

    fun generateAccessToken(user: UserDetails, clinicId: UUID, role: UserRole): String {
        return Jwts.builder()
            .subject(user.username)
            .claim("clinicId", clinicId.toString())
            .claim("role", role.name)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expirationMs))
            .signWith(key)
            .compact()
    }

    fun generateRefreshToken(userId: UUID): String {
        val token = Jwts.builder()
            .subject(userId.toString())
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + refreshExpirationMs))
            .signWith(key)
            .compact()

        // Store in Redis for rotation/revocation
        redisTemplate.opsForValue().set(
            "refresh:$userId",
            token,
            refreshExpirationMs,
            TimeUnit.MILLISECONDS
        )
        return token
    }

    fun revokeRefreshToken(userId: UUID) {
        redisTemplate.delete("refresh:$userId")
    }

    fun validateToken(token: String): Claims {
        return Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).payload
    }
}
```

## ClinicIsolationFilter.kt

```kotlin
@Component
class ClinicIsolationFilter(
    private val clinicRepository: ClinicRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth is JwtAuthenticationToken) {
            val tokenClinicId = auth.claims["clinicId"] as? String
            val requestClinicId = request.getHeader("X-Clinic-ID")

            // Super admins bypass clinic isolation
            if (auth.role != "SUPER_ADMIN" && tokenClinicId != requestClinicId) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Clinic access denied")
                return
            }

            // Store clinicId in request context for repositories
            ClinicContext.set(UUID.fromString(tokenClinicId ?: requestClinicId))
        }
        chain.doFilter(request, response)
    }
}
```

## RBAC Annotations

```kotlin
// Custom method-level security annotations
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAnyRole('CLINIC_ADMIN', 'SUPER_ADMIN')")
annotation class RequiresAdmin

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'CLINIC_ADMIN', 'SUPER_ADMIN')")
annotation class RequiresClinical

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'CLINIC_ADMIN', 'SUPER_ADMIN')")
annotation class RequiresReception
```

## Rate Limiting (Redis-backed)

```kotlin
@Component
class RateLimitInterceptor(
    private val redisTemplate: RedisTemplate<String, String>
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val ip = request.remoteAddr
        val path = request.requestURI
        val key = "rate:$ip:${path.substringBefore('/', path)}"

        val current = redisTemplate.opsForValue().increment(key) ?: 1L
        if (current == 1L) {
            redisTemplate.expire(key, 60, TimeUnit.SECONDS)
        }

        val limit = if (path.startsWith("/api/v1/auth")) 10L else 100L
        if (current > limit) {
            response.status = 429
            response.writer.write("""{"error":"Too many requests"}""")
            return false
        }
        return true
    }
}
```

---

# STEP 5 — BACKEND IMPLEMENTATION

## Domain Structure: Appointment Module (Full Example)

```kotlin
// =====================================================
// ENTITIES
// =====================================================
@Entity
@Table(name = "appointments")
data class Appointment(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    val patient: Patient,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    var doctor: Doctor?,

    @Column(name = "appointment_date")
    val appointmentDate: LocalDate,

    @Column(name = "start_time")
    val startTime: LocalTime,

    @Column(name = "end_time")
    val endTime: LocalTime,

    @Enumerated(EnumType.STRING)
    var status: AppointmentStatus = AppointmentStatus.SCHEDULED,

    @Enumerated(EnumType.STRING)
    val type: AppointmentType = AppointmentType.CONSULTATION,

    @Column(name = "queue_number")
    var queueNumber: Int? = null,

    @Column(name = "chief_complaint")
    val chiefComplaint: String? = null,

    @Column(name = "booked_via")
    val bookedVia: String = "PORTAL",

    @Column(name = "reminder_sent")
    var reminderSent: Boolean = false,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
)

// =====================================================
// DTOs
// =====================================================
data class CreateAppointmentRequest(
    @field:NotNull val patientId: UUID,
    @field:NotNull val doctorId: UUID,
    @field:NotNull val appointmentDate: LocalDate,
    @field:NotNull val startTime: LocalTime,
    val type: AppointmentType = AppointmentType.CONSULTATION,
    val chiefComplaint: String?,
    @field:Future val appointmentDateAsInstant: Instant? = null
)

data class AppointmentResponse(
    val id: UUID,
    val patientName: String,
    val patientPhone: String,
    val doctorName: String,
    val appointmentDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val status: AppointmentStatus,
    val type: AppointmentType,
    val queueNumber: Int?,
    val chiefComplaint: String?,
    val createdAt: Instant
)

// =====================================================
// REPOSITORY
// =====================================================
@Repository
interface AppointmentRepository : JpaRepository<Appointment, UUID> {

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.clinicId = :clinicId
        AND a.appointmentDate = :date
        AND a.status NOT IN ('CANCELLED', 'NO_SHOW')
        ORDER BY a.startTime ASC
    """)
    fun findByClinicAndDate(clinicId: UUID, date: LocalDate): List<Appointment>

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.clinicId = :clinicId
        AND a.doctor.id = :doctorId
        AND a.appointmentDate = :date
        AND a.status NOT IN ('CANCELLED', 'NO_SHOW')
    """)
    fun findByDoctorAndDate(clinicId: UUID, doctorId: UUID, date: LocalDate): List<Appointment>

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.clinicId = :clinicId
        AND a.patient.id = :patientId
        ORDER BY a.appointmentDate DESC, a.startTime DESC
    """)
    fun findByPatient(clinicId: UUID, patientId: UUID, pageable: Pageable): Page<Appointment>

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.reminderSent = FALSE
        AND a.appointmentDate = :tomorrow
        AND a.status = 'SCHEDULED'
    """)
    fun findPendingReminders(tomorrow: LocalDate): List<Appointment>

    @Query("""
        SELECT COUNT(a) FROM Appointment a
        WHERE a.clinicId = :clinicId
        AND a.appointmentDate = :date
        AND a.doctor.id = :doctorId
        AND a.startTime < :endTime
        AND a.endTime > :startTime
        AND a.status NOT IN ('CANCELLED', 'NO_SHOW')
    """)
    fun countConflicts(
        clinicId: UUID, doctorId: UUID, date: LocalDate,
        startTime: LocalTime, endTime: LocalTime
    ): Long
}

// =====================================================
// SERVICE
// =====================================================
@Service
@Transactional
class AppointmentService(
    private val appointmentRepository: AppointmentRepository,
    private val patientRepository: PatientRepository,
    private val doctorRepository: DoctorRepository,
    private val scheduleService: ScheduleService,
    private val reminderService: ReminderService,
    private val notificationService: NotificationService,
    private val auditService: AuditService
) {

    fun createAppointment(clinicId: UUID, request: CreateAppointmentRequest, bookedBy: UUID): AppointmentResponse {
        val patient = patientRepository.findByIdAndClinicId(request.patientId, clinicId)
            ?: throw NotFoundException("Patient not found")

        val doctor = doctorRepository.findByIdAndClinicId(request.doctorId, clinicId)
            ?: throw NotFoundException("Doctor not found")

        // 1. Validate doctor is scheduled on this day
        val slot = scheduleService.getAvailableSlot(
            clinicId, request.doctorId, request.appointmentDate, request.startTime
        ) ?: throw ConflictException("Doctor not available at this time")

        // 2. Check conflicts
        val conflicts = appointmentRepository.countConflicts(
            clinicId, request.doctorId, request.appointmentDate,
            request.startTime, slot.endTime
        )
        if (conflicts > 0) throw ConflictException("Time slot already booked")

        // 3. Get queue number
        val queueNumber = generateQueueNumber(clinicId, request.appointmentDate)

        val appointment = Appointment(
            clinicId = clinicId,
            patient = patient,
            doctor = doctor,
            appointmentDate = request.appointmentDate,
            startTime = request.startTime,
            endTime = slot.endTime,
            type = request.type,
            chiefComplaint = request.chiefComplaint,
            queueNumber = queueNumber
        )

        val saved = appointmentRepository.save(appointment)

        // 4. Schedule reminder (24h before)
        reminderService.scheduleReminder(saved)

        // 5. Send confirmation
        notificationService.sendAppointmentConfirmation(saved)

        // 6. Audit
        auditService.log(clinicId, bookedBy, "CREATE_APPOINTMENT", "appointment", saved.id)

        return saved.toResponse()
    }

    fun cancelAppointment(clinicId: UUID, appointmentId: UUID, reason: String, cancelledBy: UUID) {
        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { NotFoundException("Appointment not found") }

        if (appointment.clinicId != clinicId) throw ForbiddenException("Access denied")

        appointment.status = AppointmentStatus.CANCELLED
        appointmentRepository.save(appointment)

        notificationService.sendCancellationNotice(appointment, reason)
        auditService.log(clinicId, cancelledBy, "CANCEL_APPOINTMENT", "appointment", appointmentId)
    }

    fun checkInPatient(clinicId: UUID, appointmentId: UUID): AppointmentResponse {
        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { NotFoundException("Appointment not found") }

        appointment.status = AppointmentStatus.CHECKED_IN
        return appointmentRepository.save(appointment).toResponse()
    }

    fun getTodayQueue(clinicId: UUID): List<AppointmentResponse> {
        return appointmentRepository.findByClinicAndDate(clinicId, LocalDate.now())
            .sortedWith(compareBy({ it.status }, { it.queueNumber }))
            .map { it.toResponse() }
    }

    private fun generateQueueNumber(clinicId: UUID, date: LocalDate): Int {
        val existing = appointmentRepository.findByClinicAndDate(clinicId, date)
        return existing.size + 1
    }
}

// =====================================================
// CONTROLLER
// =====================================================
@RestController
@RequestMapping("/api/v1/appointments")
class AppointmentController(
    private val appointmentService: AppointmentService
) {
    @PostMapping
    @RequiresReception
    fun create(
        @Valid @RequestBody request: CreateAppointmentRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<AppointmentResponse>> {
        val result = appointmentService.createAppointment(
            auth.clinicId, request, auth.userId
        )
        return ResponseEntity.status(201).body(ApiResponse.success(result))
    }

    @GetMapping("/today")
    @RequiresReception
    fun todayQueue(
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<List<AppointmentResponse>>> {
        return ResponseEntity.ok(ApiResponse.success(
            appointmentService.getTodayQueue(auth.clinicId)
        ))
    }

    @PatchMapping("/{id}/check-in")
    @RequiresReception
    fun checkIn(
        @PathVariable id: UUID,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<AppointmentResponse>> {
        return ResponseEntity.ok(ApiResponse.success(
            appointmentService.checkInPatient(auth.clinicId, id)
        ))
    }

    @DeleteMapping("/{id}")
    @RequiresReception
    fun cancel(
        @PathVariable id: UUID,
        @RequestBody body: CancelRequest,
        @AuthenticationPrincipal auth: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<Unit>> {
        appointmentService.cancelAppointment(auth.clinicId, id, body.reason, auth.userId)
        return ResponseEntity.ok(ApiResponse.success(Unit))
    }
}
```

## Global Exception Handler

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<ApiError> =
        ResponseEntity.status(404).body(ApiError("NOT_FOUND", ex.message))

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ResponseEntity<ApiError> =
        ResponseEntity.status(409).body(ApiError("CONFLICT", ex.message))

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ResponseEntity<ApiError> =
        ResponseEntity.status(403).body(ApiError("FORBIDDEN", ex.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val errors = ex.bindingResult.fieldErrors.associate { it.field to it.defaultMessage }
        return ResponseEntity.status(400).body(ApiError("VALIDATION_ERROR", "Validation failed", errors))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ApiError> {
        log.error("Unhandled exception", ex)
        return ResponseEntity.status(500).body(ApiError("INTERNAL_ERROR", "Something went wrong"))
    }
}

data class ApiError(
    val code: String,
    val message: String?,
    val details: Any? = null,
    val timestamp: Instant = Instant.now()
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: ApiError? = null
) {
    companion object {
        fun <T> success(data: T) = ApiResponse(true, data)
        fun <T> error(error: ApiError) = ApiResponse<T>(false, null, error)
    }
}
```

## application.yml

```yaml
spring:
  application:
    name: cliniq-backend
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/cliniq}
    username: ${DATABASE_USER:cliniq}
    password: ${DATABASE_PASSWORD:cliniq}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}

jwt:
  secret: ${JWT_SECRET}
  expiration-ms: 900000        # 15 minutes
  refresh-expiration-ms: 604800000  # 7 days

africa-talking:
  api-key: ${AT_API_KEY:}
  username: ${AT_USERNAME:sandbox}

mpesa:
  consumer-key: ${MPESA_CONSUMER_KEY:}
  consumer-secret: ${MPESA_CONSUMER_SECRET:}
  shortcode: ${MPESA_SHORTCODE:}
  passkey: ${MPESA_PASSKEY:}
  callback-url: ${MPESA_CALLBACK_URL:}

server:
  port: 8080
  servlet:
    context-path: /

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

---

# STEP 6 — APPOINTMENT ENGINE IMPLEMENTATION

## Slot Availability Engine

```kotlin
@Service
class ScheduleService(
    private val scheduleRepository: DoctorScheduleRepository,
    private val overrideRepository: ScheduleOverrideRepository,
    private val appointmentRepository: AppointmentRepository
) {

    /**
     * Returns all available slots for a doctor on a given date.
     * Excludes booked slots and respects overrides.
     */
    fun getAvailableSlots(clinicId: UUID, doctorId: UUID, date: LocalDate): List<TimeSlot> {
        // 1. Check for override (holiday/leave)
        val override = overrideRepository.findByDoctorAndDate(doctorId, date)
        if (override != null && !override.isAvailable) return emptyList()

        // 2. Get base schedule for this weekday
        val dayOfWeek = date.dayOfWeek.value % 7 // convert to 0=Sunday
        val schedule = scheduleRepository.findByDoctorAndDay(doctorId, dayOfWeek)
            ?: return emptyList()

        val startTime = override?.startTime ?: schedule.startTime
        val endTime = override?.endTime ?: schedule.endTime
        val slotDuration = schedule.slotDurationMinutes.toLong()

        // 3. Generate all theoretical slots
        val allSlots = mutableListOf<TimeSlot>()
        var current = startTime
        while (current.plusMinutes(slotDuration) <= endTime) {
            allSlots.add(TimeSlot(current, current.plusMinutes(slotDuration)))
            current = current.plusMinutes(slotDuration)
        }

        // 4. Remove booked slots
        val booked = appointmentRepository.findByDoctorAndDate(clinicId, doctorId, date)
        return allSlots.filter { slot ->
            booked.none { appt ->
                appt.startTime < slot.endTime && appt.endTime > slot.startTime
            }
        }
    }

    fun getAvailableSlot(clinicId: UUID, doctorId: UUID, date: LocalDate, requestedTime: LocalTime): TimeSlot? {
        return getAvailableSlots(clinicId, doctorId, date)
            .find { it.startTime == requestedTime }
    }
}

data class TimeSlot(val startTime: LocalTime, val endTime: LocalTime)
```

## Reminder Scheduler

```kotlin
@Component
class ReminderScheduler(
    private val appointmentRepository: AppointmentRepository,
    private val communicationService: CommunicationService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Runs every hour — sends reminders for next-day appointments.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    fun sendDailyReminders() {
        val tomorrow = LocalDate.now().plusDays(1)
        val appointments = appointmentRepository.findPendingReminders(tomorrow)

        log.info("Processing {} appointment reminders for {}", appointments.size, tomorrow)

        appointments.forEach { appointment ->
            try {
                communicationService.sendAppointmentReminder(appointment)
                appointment.reminderSent = true
                appointmentRepository.save(appointment)
            } catch (e: Exception) {
                log.error("Failed to send reminder for appointment {}", appointment.id, e)
            }
        }
    }

    /**
     * Marks no-shows: appointments that passed without check-in.
     */
    @Scheduled(cron = "0 0 20 * * *")  // Every day at 8 PM
    @Transactional
    fun markNoShows() {
        val yesterday = LocalDate.now().minusDays(1)
        appointmentRepository.findScheduledBeforeDate(yesterday).forEach { appt ->
            appt.status = AppointmentStatus.NO_SHOW
            appointmentRepository.save(appt)
        }
    }
}
```

## Queue Manager

```kotlin
@Service
class QueueService(
    private val appointmentRepository: AppointmentRepository,
    private val notificationService: NotificationService
) {
    /**
     * Real-time queue for reception dashboard.
     */
    fun getLiveQueue(clinicId: UUID): QueueSummary {
        val today = LocalDate.now()
        val appointments = appointmentRepository.findByClinicAndDate(clinicId, today)

        val waiting = appointments.filter { it.status == AppointmentStatus.CHECKED_IN }
        val inProgress = appointments.filter { it.status == AppointmentStatus.IN_CONSULTATION }
        val completed = appointments.filter { it.status == AppointmentStatus.COMPLETED }
        val upcoming = appointments.filter { it.status == AppointmentStatus.SCHEDULED }

        val avgWaitMinutes = if (completed.isNotEmpty()) {
            calculateAverageWait(completed)
        } else 15

        return QueueSummary(
            waiting = waiting.map { it.toQueueItem() },
            inProgress = inProgress.map { it.toQueueItem() },
            completed = completed.size,
            upcoming = upcoming.size,
            estimatedWaitMinutes = waiting.size * avgWaitMinutes
        )
    }

    fun callNextPatient(clinicId: UUID, doctorId: UUID): AppointmentResponse? {
        val next = appointmentRepository.findNextInQueue(clinicId, doctorId)
        return next?.let {
            it.status = AppointmentStatus.IN_CONSULTATION
            val saved = appointmentRepository.save(it)
            notificationService.sendQueueCallNotification(saved)
            saved.toResponse()
        }
    }
}

data class QueueSummary(
    val waiting: List<QueueItem>,
    val inProgress: List<QueueItem>,
    val completed: Int,
    val upcoming: Int,
    val estimatedWaitMinutes: Int
)
```

---

# STEP 7 — FRONTEND IMPLEMENTATION

## Project Setup (next.config.ts)

```typescript
import type { NextConfig } from 'next'

const nextConfig: NextConfig = {
  experimental: {
    serverActions: { allowedOrigins: ['cliniq.app', '*.cliniq.app'] }
  },
  images: {
    remotePatterns: [
      { protocol: 'https', hostname: '**.cliniq.app' }
    ]
  },
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.NEXT_PUBLIC_API_URL}/api/:path*`
      }
    ]
  }
}

export default nextConfig
```

## Redux Store Structure

```typescript
// src/lib/store/index.ts
import { configureStore } from '@reduxjs/toolkit'
import { authSlice } from './slices/authSlice'
import { clinicSlice } from './slices/clinicSlice'
import { uiSlice } from './slices/uiSlice'
import { notificationSlice } from './slices/notificationSlice'

export const store = configureStore({
  reducer: {
    auth: authSlice.reducer,
    clinic: clinicSlice.reducer,
    ui: uiSlice.reducer,
    notifications: notificationSlice.reducer
  },
  middleware: (getDefault) => getDefault({ serializableCheck: false })
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch

// src/lib/store/slices/authSlice.ts
interface AuthState {
  user: {
    id: string
    email: string
    firstName: string
    lastName: string
    role: UserRole
    clinicId: string
    avatarUrl?: string
  } | null
  accessToken: string | null
  isAuthenticated: boolean
  isLoading: boolean
}
```

## TanStack Query API Hooks

```typescript
// src/lib/api/appointments.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'

export const appointmentKeys = {
  all: ['appointments'] as const,
  today: () => [...appointmentKeys.all, 'today'] as const,
  byDate: (date: string) => [...appointmentKeys.all, 'date', date] as const,
  byPatient: (patientId: string) => [...appointmentKeys.all, 'patient', patientId] as const,
}

export function useTodayQueue() {
  return useQuery({
    queryKey: appointmentKeys.today(),
    queryFn: () => apiClient.get<AppointmentResponse[]>('/api/v1/appointments/today'),
    refetchInterval: 30_000, // refresh every 30s
    staleTime: 15_000
  })
}

export function useCreateAppointment() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateAppointmentRequest) =>
      apiClient.post<AppointmentResponse>('/api/v1/appointments', data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: appointmentKeys.all })
      toast.success('Appointment created successfully')
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to create appointment')
    }
  })
}

export function useAvailableSlots(doctorId: string, date: string) {
  return useQuery({
    queryKey: ['slots', doctorId, date],
    queryFn: () =>
      apiClient.get<TimeSlot[]>(`/api/v1/schedule/slots?doctorId=${doctorId}&date=${date}`),
    enabled: Boolean(doctorId && date)
  })
}
```

## Reception Dashboard Layout

```typescript
// src/app/(dashboard)/reception/page.tsx
export default function ReceptionDashboard() {
  const { data: queue, isLoading } = useTodayQueue()
  const { data: stats } = useTodayStats()

  return (
    <DashboardShell>
      <DashboardHeader
        title="Reception"
        subtitle={format(new Date(), 'EEEE, MMMM d')}
        actions={
          <Button onClick={() => openBookingModal()}>
            <Plus className="mr-2 h-4 w-4" />
            New Appointment
          </Button>
        }
      />

      {/* Stats Row */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard
          label="Total Today"
          value={stats?.total ?? 0}
          icon={<Calendar className="h-5 w-5" />}
          color="blue"
        />
        <StatCard
          label="Waiting"
          value={stats?.waiting ?? 0}
          icon={<Clock className="h-5 w-5" />}
          color="amber"
        />
        <StatCard
          label="In Consultation"
          value={stats?.inProgress ?? 0}
          icon={<UserCheck className="h-5 w-5" />}
          color="green"
        />
        <StatCard
          label="Completed"
          value={stats?.completed ?? 0}
          icon={<CheckCircle className="h-5 w-5" />}
          color="teal"
        />
      </div>

      {/* Queue */}
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <QueueTable appointments={queue ?? []} isLoading={isLoading} />
        </div>
        <div>
          <QuickActions />
          <UpcomingAppointments />
        </div>
      </div>
    </DashboardShell>
  )
}
```

## Doctor Portal Page

```typescript
// src/app/(dashboard)/doctor/page.tsx
export default function DoctorPortal() {
  const { data: queue } = useDoctorQueue()
  const { data: activeConsult } = useActiveConsultation()

  return (
    <DashboardShell>
      <DashboardHeader title="My Queue" />
      <div className="grid gap-6 lg:grid-cols-5">

        {/* Queue list */}
        <div className="lg:col-span-2 space-y-3">
          {queue?.waiting.map((appt) => (
            <PatientQueueCard
              key={appt.id}
              appointment={appt}
              onCallNext={() => callNextPatient(appt.id)}
            />
          ))}
        </div>

        {/* Active consultation */}
        <div className="lg:col-span-3">
          {activeConsult ? (
            <ConsultationWorkspace appointment={activeConsult} />
          ) : (
            <EmptyConsultationState />
          )}
        </div>
      </div>
    </DashboardShell>
  )
}
```

## Booking Modal Component

```typescript
// src/components/appointments/BookingModal.tsx
export function BookingModal({ open, onClose }: Props) {
  const [step, setStep] = useState<'patient' | 'doctor' | 'slot' | 'confirm'>('patient')
  const [form, setForm] = useState<Partial<CreateAppointmentRequest>>({})
  const createAppt = useCreateAppointment()

  const { data: slots } = useAvailableSlots(
    form.doctorId ?? '',
    form.appointmentDate ?? ''
  )

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle>Book Appointment</DialogTitle>
          <BookingProgressBar step={step} />
        </DialogHeader>

        {step === 'patient' && (
          <PatientSearchStep
            onSelect={(patient) => {
              setForm(f => ({ ...f, patientId: patient.id }))
              setStep('doctor')
            }}
          />
        )}

        {step === 'doctor' && (
          <DoctorSelectStep
            onSelect={(doctor) => {
              setForm(f => ({ ...f, doctorId: doctor.id }))
              setStep('slot')
            }}
          />
        )}

        {step === 'slot' && (
          <SlotSelectStep
            slots={slots ?? []}
            onSelect={(date, time) => {
              setForm(f => ({ ...f, appointmentDate: date, startTime: time }))
              setStep('confirm')
            }}
          />
        )}

        {step === 'confirm' && (
          <ConfirmStep
            form={form}
            onSubmit={() => createAppt.mutate(form as CreateAppointmentRequest)}
            isLoading={createAppt.isPending}
          />
        )}
      </DialogContent>
    </Dialog>
  )
}
```

## Tailwind Theme Configuration

```typescript
// tailwind.config.ts
export default {
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // ClinIQ brand: calm teal/slate palette
        primary: {
          50:  '#f0fdfc',
          100: '#ccfbf1',
          400: '#2dd4bf',
          500: '#14b8a6',
          600: '#0d9488',
          700: '#0f766e',
          900: '#134e4a',
        },
        slate: {
          850: '#1a2332',
          950: '#0d1424'
        }
      },
      fontFamily: {
        sans: ['var(--font-geist-sans)', 'system-ui'],
        mono: ['var(--font-geist-mono)']
      },
      animation: {
        'slide-in': 'slideIn 0.2s ease-out',
        'fade-up': 'fadeUp 0.3s ease-out',
      }
    }
  }
}
```

---

# STEP 8 — CHATBOT & COMMUNICATION SYSTEM

## Communication Service (Spring Boot)

```kotlin
@Service
class CommunicationService(
    private val smsProvider: AfricasTalkingService,
    private val whatsappProvider: WhatsAppService,
    private val emailProvider: EmailService,
    private val communicationRepository: CommunicationRepository
) {

    fun sendAppointmentReminder(appointment: Appointment) {
        val patient = appointment.patient
        val message = buildReminderMessage(appointment)

        // WhatsApp preferred, fallback to SMS
        val channel = if (patient.phone.isNotBlank()) CommChannel.WHATSAPP else CommChannel.SMS

        val comm = Communication(
            clinicId = appointment.clinicId,
            patientId = patient.id,
            channel = channel,
            type = CommType.APPOINTMENT_REMINDER,
            body = message,
            appointmentId = appointment.id
        )

        try {
            when (channel) {
                CommChannel.WHATSAPP -> whatsappProvider.send(patient.phone, message)
                CommChannel.SMS -> smsProvider.send(patient.phone, message)
                else -> {}
            }
            comm.status = CommStatus.SENT
            comm.sentAt = Instant.now()
        } catch (e: Exception) {
            comm.status = CommStatus.FAILED
            comm.failedReason = e.message
        }

        communicationRepository.save(comm)
    }

    private fun buildReminderMessage(appt: Appointment): String {
        val time = appt.startTime.format(DateTimeFormatter.ofPattern("h:mm a"))
        val date = appt.appointmentDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        return """
            Hello ${appt.patient.firstName}! 👋

            Reminder: You have an appointment tomorrow.
            📅 Date: $date
            ⏰ Time: $time
            👨‍⚕️ Doctor: Dr. ${appt.doctor?.user?.firstName} ${appt.doctor?.user?.lastName}

            Please arrive 10 minutes early.
            To cancel or reschedule, call us at ${appt.clinic.phone}.

            See you tomorrow! ✅
        """.trimIndent()
    }
}
```

## Africa's Talking SMS Service

```kotlin
@Service
class AfricasTalkingService(
    @Value("\${africa-talking.api-key}") private val apiKey: String,
    @Value("\${africa-talking.username}") private val username: String
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val smsService: SmsService by lazy {
        val at = AfricasTalking.initialize(username, apiKey)
        AfricasTalking.getService(AfricasTalking.ServiceName.SMS)
    }

    fun send(phone: String, message: String): String {
        val normalized = normalizePhone(phone)
        val responses = smsService.send(message, listOf(normalized), null, null, null)
        val first = responses.first()
        log.info("SMS sent to {}: status={}", phone, first.status)
        return first.messageId
    }

    private fun normalizePhone(phone: String): String {
        return when {
            phone.startsWith("+") -> phone
            phone.startsWith("07") -> "+254${phone.substring(1)}"
            phone.startsWith("01") -> "+254${phone.substring(1)}"
            phone.startsWith("254") -> "+$phone"
            else -> "+254$phone"
        }
    }
}
```

## M-Pesa STK Push Service

```kotlin
@Service
class MpesaService(
    @Value("\${mpesa.consumer-key}") private val consumerKey: String,
    @Value("\${mpesa.consumer-secret}") private val consumerSecret: String,
    @Value("\${mpesa.shortcode}") private val shortcode: String,
    @Value("\${mpesa.passkey}") private val passkey: String,
    @Value("\${mpesa.callback-url}") private val callbackUrl: String,
    private val restTemplate: RestTemplate,
    private val paymentRepository: PaymentRepository
) {
    fun initiateSTKPush(phone: String, amount: Long, invoiceId: UUID): StkPushResponse {
        val token = getAccessToken()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val password = Base64.getEncoder().encodeToString(
            "$shortcode$passkey$timestamp".toByteArray()
        )
        val normalizedPhone = normalizeMpesaPhone(phone)

        val request = mapOf(
            "BusinessShortCode" to shortcode,
            "Password" to password,
            "Timestamp" to timestamp,
            "TransactionType" to "CustomerPayBillOnline",
            "Amount" to amount,
            "PartyA" to normalizedPhone,
            "PartyB" to shortcode,
            "PhoneNumber" to normalizedPhone,
            "CallBackURL" to "$callbackUrl?invoiceId=$invoiceId",
            "AccountReference" to "ClinIQ-$invoiceId",
            "TransactionDesc" to "Clinic Payment"
        )

        val headers = HttpHeaders().apply {
            setBearerAuth(token)
            contentType = MediaType.APPLICATION_JSON
        }

        return restTemplate.postForObject(
            "https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest",
            HttpEntity(request, headers),
            StkPushResponse::class.java
        ) ?: throw RuntimeException("M-Pesa request failed")
    }

    @PostMapping("/callback")
    fun handleCallback(body: MpesaCallback, @RequestParam invoiceId: UUID) {
        val result = body.Body.stkCallback
        if (result.ResultCode == 0) {
            val amount = result.CallbackMetadata.Item.find { it.Name == "Amount" }?.Value as? Double
            val txRef = result.CallbackMetadata.Item.find { it.Name == "MpesaReceiptNumber" }?.Value as? String
            paymentRepository.recordMpesaPayment(invoiceId, amount?.toLong() ?: 0L, txRef ?: "")
        }
    }
}
```

## Chatbot Service (Node.js)

```javascript
// chatbot/src/index.js
const express = require('express')
const { handleWhatsAppWebhook } = require('./handlers/whatsapp')
const { handleWebChat } = require('./handlers/webchat')

const app = express()
app.use(express.json())

// WhatsApp webhook
app.post('/webhook/whatsapp', handleWhatsAppWebhook)
app.get('/webhook/whatsapp', (req, res) => {
  // Meta verification
  const token = req.query['hub.verify_token']
  if (token === process.env.WHATSAPP_VERIFY_TOKEN) {
    res.send(req.query['hub.challenge'])
  } else {
    res.sendStatus(403)
  }
})

// Web chat
app.post('/chat', handleWebChat)

app.listen(5000)

// chatbot/src/handlers/whatsapp.js
const { processMessage } = require('../engine/processor')

async function handleWhatsAppWebhook(req, res) {
  const entry = req.body.entry?.[0]
  const message = entry?.changes?.[0]?.value?.messages?.[0]
  if (!message) return res.sendStatus(200)

  const from = message.from
  const text = message.text?.body || ''
  const clinicSlug = extractClinicFromContext(req)

  const response = await processMessage({ from, text, channel: 'WHATSAPP', clinicSlug })
  await sendWhatsAppReply(from, response.message)

  if (response.shouldEscalate) {
    await notifyStaffOfEscalation(clinicSlug, from, text)
  }

  res.sendStatus(200)
}

// chatbot/src/engine/processor.js
const { getClinicInfo } = require('../integrations/cliniqApi')
const { matchIntent } = require('./intentMatcher')

async function processMessage({ from, text, channel, clinicSlug }) {
  const clinic = await getClinicInfo(clinicSlug)
  const intent = matchIntent(text)

  switch (intent) {
    case 'BOOK_APPOINTMENT':
      return handleBookingFlow(from, clinic)
    case 'GET_HOURS':
      return { message: formatHours(clinic) }
    case 'GET_LOCATION':
      return { message: formatLocation(clinic) }
    case 'GET_SERVICES':
      return { message: formatServices(clinic) }
    case 'CANCEL_APPOINTMENT':
      return handleCancellation(from, clinic)
    case 'SPEAK_TO_HUMAN':
      return { message: '🙋 I\'m connecting you to our team now. Please hold on.', shouldEscalate: true }
    default:
      return {
        message: `Hello! 👋 Welcome to ${clinic.name}. I'm here to help!\n\nReply with:\n1️⃣ Book appointment\n2️⃣ Clinic hours\n3️⃣ Our location\n4️⃣ Our services\n5️⃣ Talk to staff`
      }
  }
}

// Intent matching (keyword-based, no AI required)
function matchIntent(text) {
  const lower = text.toLowerCase().trim()
  if (/\b(book|appointment|schedule|appoint)\b/.test(lower)) return 'BOOK_APPOINTMENT'
  if (/\b(hour|open|time|when|close)\b/.test(lower)) return 'GET_HOURS'
  if (/\b(where|location|address|direction|map)\b/.test(lower)) return 'GET_LOCATION'
  if (/\b(service|offer|treat|special|do you)\b/.test(lower)) return 'GET_SERVICES'
  if (/\b(cancel|reschedule|change)\b/.test(lower)) return 'CANCEL_APPOINTMENT'
  if (/\b(human|agent|staff|person|talk|speak)\b/.test(lower)) return 'SPEAK_TO_HUMAN'
  return 'UNKNOWN'
}
```

---

# STEP 9 — DEVOPS & DEPLOYMENT

## docker-compose.yml (Development)

```yaml
version: '3.9'

services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: cliniq
      POSTGRES_USER: cliniq
      POSTGRES_PASSWORD: cliniq_dev_password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U cliniq"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --requirepass redis_dev_password
    volumes:
      - redis_data:/data

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      DATABASE_URL: jdbc:postgresql://db:5432/cliniq
      DATABASE_USER: cliniq
      DATABASE_PASSWORD: cliniq_dev_password
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: redis_dev_password
      JWT_SECRET: dev_jwt_secret_at_least_64_characters_long_change_in_production_please
      SPRING_PROFILES_ACTIVE: dev
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_started
    volumes:
      - ./backend:/app

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    ports:
      - "3000:3000"
    environment:
      NEXT_PUBLIC_API_URL: http://backend:8080
      NEXT_PUBLIC_WS_URL: ws://backend:8080
    depends_on:
      - backend

  chatbot:
    build:
      context: ./chatbot
    ports:
      - "5000:5000"
    environment:
      CLINIQ_API_URL: http://backend:8080
      WHATSAPP_VERIFY_TOKEN: dev_verify_token
    depends_on:
      - backend

volumes:
  postgres_data:
  redis_data:
```

## docker-compose.prod.yml

```yaml
version: '3.9'

services:
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./devops/nginx/cliniq.conf:/etc/nginx/conf.d/cliniq.conf:ro
      - ./certbot/conf:/etc/letsencrypt:ro
      - ./certbot/www:/var/www/certbot:ro
    depends_on:
      - backend
      - frontend

  certbot:
    image: certbot/certbot
    volumes:
      - ./certbot/conf:/etc/letsencrypt
      - ./certbot/www:/var/www/certbot

  db:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    # NOT exposed to internet in prod

  redis:
    image: redis:7-alpine
    restart: unless-stopped
    command: redis-server --requirepass ${REDIS_PASSWORD} --maxmemory 256mb --maxmemory-policy allkeys-lru
    volumes:
      - redis_data:/data

  backend:
    image: ghcr.io/your-org/cliniq-backend:${VERSION:-latest}
    restart: unless-stopped
    env_file: .env.prod
    depends_on:
      - db
      - redis
    deploy:
      resources:
        limits:
          memory: 1G

  frontend:
    image: ghcr.io/your-org/cliniq-frontend:${VERSION:-latest}
    restart: unless-stopped
    env_file: .env.prod

  chatbot:
    image: ghcr.io/your-org/cliniq-chatbot:${VERSION:-latest}
    restart: unless-stopped
    env_file: .env.prod

volumes:
  postgres_data:
  redis_data:
```

## Nginx Configuration

```nginx
# devops/nginx/cliniq.conf
upstream backend {
    server backend:8080;
    keepalive 32;
}

upstream frontend {
    server frontend:3000;
}

upstream chatbot {
    server chatbot:5000;
}

server {
    listen 80;
    server_name cliniq.app *.cliniq.app;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name cliniq.app;

    ssl_certificate /etc/letsencrypt/live/cliniq.app/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/cliniq.app/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512;

    # Security headers
    add_header X-Frame-Options "DENY";
    add_header X-Content-Type-Options "nosniff";
    add_header Referrer-Policy "strict-origin-when-cross-origin";
    add_header X-XSS-Protection "1; mode=block";
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains";

    # Gzip
    gzip on;
    gzip_types text/plain application/json text/css application/javascript;

    # API
    location /api/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_connect_timeout 30s;
        proxy_read_timeout 60s;

        # Rate limiting
        limit_req zone=api burst=20 nodelay;
    }

    # WebSocket
    location /ws/ {
        proxy_pass http://backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # Chatbot webhook
    location /webhook/ {
        proxy_pass http://chatbot;
    }

    # Frontend (everything else)
    location / {
        proxy_pass http://frontend;
        proxy_cache_bypass $http_upgrade;
    }
}

limit_req_zone $binary_remote_addr zone=api:10m rate=30r/m;
```

## GitHub Actions CI/CD

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  backend-test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: cliniq_test
          POSTGRES_USER: cliniq
          POSTGRES_PASSWORD: test_password
        ports:
          - 5432:5432
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Test
        working-directory: ./backend
        run: ./gradlew test
        env:
          DATABASE_URL: jdbc:postgresql://localhost:5432/cliniq_test
          DATABASE_USER: cliniq
          DATABASE_PASSWORD: test_password
          JWT_SECRET: test_secret_at_least_64_characters_long_for_testing_purposes_only

  frontend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json
      - run: cd frontend && npm ci
      - run: cd frontend && npm run lint
      - run: cd frontend && npm run build

# .github/workflows/cd-production.yml
name: Deploy Production

on:
  push:
    tags:
      - 'v*'

jobs:
  build-push:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build & Push Backend
        uses: docker/build-push-action@v5
        with:
          context: ./backend
          push: true
          tags: ghcr.io/${{ github.repository }}/cliniq-backend:${{ github.ref_name }}

      - name: Build & Push Frontend
        uses: docker/build-push-action@v5
        with:
          context: ./frontend
          push: true
          tags: ghcr.io/${{ github.repository }}/cliniq-frontend:${{ github.ref_name }}

      - name: Deploy to Hetzner
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.HETZNER_HOST }}
          username: deploy
          key: ${{ secrets.HETZNER_SSH_KEY }}
          script: |
            cd /opt/cliniq
            export VERSION=${{ github.ref_name }}
            docker-compose -f docker-compose.prod.yml pull
            docker-compose -f docker-compose.prod.yml up -d --no-build
            docker system prune -f
```

## Backup Strategy

```bash
#!/bin/bash
# devops/scripts/backup.sh

set -e

BACKUP_DIR="/opt/cliniq/backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RETENTION_DAYS=30

# PostgreSQL backup
docker exec cliniq_db_1 pg_dump \
  -U $DB_USER \
  -d $DB_NAME \
  --format=custom \
  --compress=9 \
  > "$BACKUP_DIR/db_$TIMESTAMP.dump"

# Encrypt backup
gpg --symmetric --batch --passphrase="$BACKUP_PASSPHRASE" \
  "$BACKUP_DIR/db_$TIMESTAMP.dump"
rm "$BACKUP_DIR/db_$TIMESTAMP.dump"

# Upload to S3/R2
aws s3 cp "$BACKUP_DIR/db_$TIMESTAMP.dump.gpg" \
  "s3://$BACKUP_BUCKET/cliniq/db/$TIMESTAMP.dump.gpg"

# Clean old backups
find "$BACKUP_DIR" -name "*.gpg" -mtime +$RETENTION_DAYS -delete

echo "Backup completed: $TIMESTAMP"
```

---

# STEP 10 — COMMERCIALIZATION STRATEGY

## SaaS Pricing Model (Kenya/East Africa market)

| Plan | Price (KES/month) | Target | Key Features |
|------|-------------------|--------|--------------|
| **Starter** | KES 4,999 | Solo doctors, small clinics | Appointments, 1 doctor, patient records, SMS reminders (50/mo), basic billing |
| **Growth** | KES 9,999 | Clinics with 2-5 staff | Everything + 5 doctors, WhatsApp bot, CRM campaigns, analytics, invoicing |
| **Pro** | KES 19,999 | Established clinics | Everything + unlimited doctors, custom chatbot, priority support, API access, white-label option |
| **Enterprise** | Custom | Hospital groups, chains | Multi-branch, custom integrations, dedicated support, SLA |

**Annual plans: 20% discount** → strong cash-flow driver.

## Onboarding Strategy (72-hour to live)

```
Day 1 (30 min):
  ✅ Account creation (clinic admin self-registers)
  ✅ Clinic profile setup (name, logo, services, hours)
  ✅ Add doctors + staff accounts
  ✅ Guided onboarding checklist in dashboard

Day 2 (1 hr):
  ✅ Import existing patients (CSV upload template)
  ✅ Set doctor schedules
  ✅ Connect M-Pesa for payments
  ✅ Configure WhatsApp chatbot (webhook setup guide)

Day 3:
  ✅ First appointment booked
  ✅ Test reminder sent
  ✅ Staff trained (video walkthrough links in dashboard)
```

## Clinic Acquisition Strategy

**Primary Channels (Kenya focus):**
1. **Direct sales** — Call on clinics in Nairobi, Mombasa, Kisumu. Offer 30-day free trial, no credit card.
2. **Healthcare associations** — Partner with Kenya Medical Association, Kenya Dental Association.
3. **Medical suppliers** — Bundle with equipment suppliers (pharmacies, lab equipment vendors).
4. **Social proof** — Case studies: "Clinic X reduced no-shows by 40% in 60 days."
5. **Referral program** — Clinic recommends ClinIQ to another → 2 months free.
6. **Content marketing** — "How to reduce no-shows" blog posts, free webinars for clinic owners.

## Feature Tiering & Upsell Opportunities

| Feature | Starter | Growth | Pro |
|---------|---------|--------|-----|
| Online booking page | ✅ | ✅ | ✅ |
| SMS reminders | 50/mo | 200/mo | Unlimited |
| WhatsApp bot | ❌ | ✅ | ✅ |
| CRM campaigns | ❌ | ✅ | ✅ |
| Analytics dashboard | Basic | Full | Full + Export |
| Patient portal | ❌ | ✅ | ✅ |
| API access | ❌ | ❌ | ✅ |
| White-label | ❌ | ❌ | Add-on (+KES 5k) |
| Insurance/NHIF billing | ❌ | Add-on | Add-on |
| Custom domain | ❌ | ✅ | ✅ |

**Upsell Paths:**
- Starter → Growth trigger: when clinic books >100 appts/month
- Growth → Pro trigger: when clinic has >4 doctors or requests analytics export
- Pro → Enterprise: multi-branch request

---

# STEP 11 — MVP EXECUTION PLAN

## Phase 1 — Foundation (Weeks 1–2)

**Goal: Clean, working system on localhost**

```
Backend:
  ✅ Spring Boot project setup + Gradle
  ✅ PostgreSQL + Flyway migrations (all tables)
  ✅ JWT authentication (login, refresh, logout)
  ✅ RBAC (roles, method security)
  ✅ User management (create, list)
  ✅ Patient CRUD
  ✅ Doctor schedule setup

Frontend:
  ✅ Next.js 14 setup + TypeScript + Tailwind + ShadCN
  ✅ Redux store + TanStack Query
  ✅ Auth flow (login page, protected routes)
  ✅ Layout shell (sidebar, header, mobile nav)
  ✅ Patient list + create patient form
```

## Phase 2 — Core Operations (Weeks 3–4)

**Goal: Clinic can manage appointments end-to-end**

```
Backend:
  ✅ Appointment engine (create, list, cancel, reschedule)
  ✅ Slot availability API
  ✅ Queue management
  ✅ Africa's Talking SMS integration
  ✅ Appointment reminder scheduler

Frontend:
  ✅ Reception dashboard (today's queue)
  ✅ Booking modal (4-step flow)
  ✅ Patient profile page (history, timeline)
  ✅ Doctor portal (queue view)
  ✅ Check-in flow
```

## Phase 3 — Billing & Communication (Weeks 5–6)

**Goal: Revenue tracking and patient communication live**

```
Backend:
  ✅ Invoice creation + PDF generation
  ✅ M-Pesa STK push integration
  ✅ Payment recording
  ✅ Communication logs
  ✅ WhatsApp webhook receiver
  ✅ Basic chatbot (keyword-based)

Frontend:
  ✅ Billing dashboard
  ✅ Invoice builder
  ✅ Payment recording UI
  ✅ Communication log view
  ✅ Revenue charts
```

## Phase 4 — CRM & Analytics (Weeks 7–8)

**Goal: Retention tools live, clinic ready to market**

```
Backend:
  ✅ CRM campaign creation + scheduling
  ✅ Patient segmentation (tag-based)
  ✅ Follow-up automation
  ✅ Analytics queries (no-show rate, revenue, retention)
  ✅ Audit log system

Frontend:
  ✅ Admin analytics dashboard
  ✅ CRM campaign builder
  ✅ No-show analytics
  ✅ Patient retention charts
  ✅ Staff management UI
```

## Phase 5 — Polish & Launch (Weeks 9–10)

**Goal: First paying clinic onboarded**

```
DevOps:
  ✅ Hetzner VPS setup
  ✅ Docker production build
  ✅ Nginx + SSL (Let's Encrypt)
  ✅ GitHub Actions CI/CD
  ✅ Automated backups (daily)
  ✅ Uptime monitoring (UptimeRobot)

Product:
  ✅ Onboarding flow tested
  ✅ CSV patient import
  ✅ Email/SMS tested in production
  ✅ Mobile responsiveness audit
  ✅ Performance audit (Lighthouse)
  ✅ OWASP security check

Business:
  ✅ Pricing page live
  ✅ Demo clinic configured
  ✅ Onboarding video recorded
  ✅ 3 pilot clinics identified for free trial
```

## Testing Strategy

```
Backend:
  - Unit tests: JUnit 5 + MockK for services
  - Integration tests: TestContainers (real PostgreSQL)
  - API tests: @SpringBootTest + MockMvc
  - Target: 80% coverage on service layer

Frontend:
  - Component tests: Vitest + React Testing Library
  - E2E: Playwright (booking flow, auth flow)
  - Visual regression: Chromatic (optional)

Load Testing:
  - k6 scripts for appointment booking under load
  - Target: 100 concurrent users, <500ms p95
```

## Launch Checklist

```
Technical:
  □ All Flyway migrations clean
  □ JWT secret rotated from default
  □ HTTPS enforced everywhere
  □ Rate limiting active
  □ Error monitoring (Sentry) connected
  □ Logs shipping (Loki/Grafana or Papertrail)
  □ Backups tested (restore drill)
  □ Load test passed

Business:
  □ Terms of Service + Privacy Policy pages live
  □ POPIA/HIPAA-adjacent data handling notice
  □ Subscription billing configured (Stripe or Flutterwave)
  □ Support channel ready (WhatsApp business number)
  □ Onboarding email sequence drafted
  □ Demo account ready for prospects
  □ Pricing page published
```

---

## Environment Variables Reference (.env.example)

```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/cliniq
DATABASE_USER=cliniq
DATABASE_PASSWORD=change_me

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=change_me

# JWT (generate with: openssl rand -base64 64)
JWT_SECRET=change_me_to_64_char_random_string

# Africa's Talking
AT_API_KEY=your_api_key
AT_USERNAME=your_username

# M-Pesa
MPESA_CONSUMER_KEY=
MPESA_CONSUMER_SECRET=
MPESA_SHORTCODE=
MPESA_PASSKEY=
MPESA_CALLBACK_URL=https://yourdomain.com/api/v1/payments/mpesa/callback

# WhatsApp Business
WHATSAPP_PHONE_ID=
WHATSAPP_ACCESS_TOKEN=
WHATSAPP_VERIFY_TOKEN=change_me

# Email (SMTP)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=
SMTP_PASSWORD=

# Storage (MinIO or S3)
STORAGE_ENDPOINT=
STORAGE_ACCESS_KEY=
STORAGE_SECRET_KEY=
STORAGE_BUCKET=cliniq-files

# App
FRONTEND_URL=http://localhost:3000
BACKEND_URL=http://localhost:8080
SPRING_PROFILES_ACTIVE=dev
```

---

*ClinIQ v1.0 — Built for East African Clinics. Engineered for SaaS Scale.*
*Stack: Kotlin/Spring Boot • Next.js/TypeScript • PostgreSQL • Redis • Docker*
