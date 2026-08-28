# Database — ClinIQ Backend

## Overview
- **Database**: PostgreSQL 16
- **ORM**: Spring Data JPA with Hibernate
- **Migrations**: Flyway (automatic on app startup, `ddl-auto: none`)
- **Connection pooling**: HikariCP
- **Schema versioning**: V1 → V2 → V3 migration sequence

## Flyway Migration History

### V1 — Initial Schema
- Creates all 23 base tables
- Core entities: users, clinics, doctors, patients, appointments, consultations, prescriptions, drugs, batches, dispenses, lab tests/requests/results, communications, invoices, payments
- No appointment-specific columns beyond original design

### V2 — Appointment Enhancements
Adds 6 columns to `appointments` table:
- `booked_by` (UUID, FK → users)
- `booked_via` (VARCHAR(50), default 'PORTAL')
- `cancellation_reason` (VARCHAR(200))
- `chief_complaint` (TEXT)
- `reminder_sent` (BOOLEAN, default FALSE)
- `rescheduled_from` (UUID, self-referential)

Also adds column comments documenting each field.

### V3 — Data Seeding
Seeds initial data:
- **Clinic**: default "demo" clinic
- **SUPER_ADMIN**: admin user with BCrypt hash `Admin123!`
- **DOCTOR**: test doctor user
- **Patient**: PAT001 test patient
- **Appointment**: sample appointment record
- **Consultation**: sample consultation record

## Entity Relationship Diagram (simplified)

```
clinic (1) → (N) doctor
clinic (1) → (N) patient
clinic (1) → (N) appointment
clinic (1) → (N) consultation
clinic (1) → (N) pharmacy drug
clinic (1) → (N) lab lab_request

doctor (1) → (1) user
patient (1) → (1) user

appointment (1) → (N) consultation
appointment (1) → (1) patient
appointment (1) → (1) doctor

consultation (1) → (N) prescription
consultation (1) → (1) appointment

drug_dispense (1) → (1) drug
drug_dispense (1) → (1) batch

lab_request (1) → (1) patient
lab_request (1) → (1) doctor
lab_request (1) → (N) lab_result
```

## Key Tables & Columns

### `appointments`
| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `clinic_id` | UUID | Foreign key to clinics |
| `patient_id` | UUID | Foreign key to users (patient) |
| `doctor_id` | UUID | Foreign key to doctors |
| `appointment_date` | DATE | Appointment date |
| `start_time` | TIME | Start time |
| `end_time` | TIME | End time |
| `status` | VARCHAR | SCHEDULED/CONFIRMED/CHECKED_IN/IN_CONSULTATION/COMPLETED/CANCELLED/NO_SHOW |
| `type` | VARCHAR | Consultation/Follow-up/Emergency |
| `queue_number` | INT | Queue position |
| `booked_by` | UUID | Who booked it |
| `booked_via` | VARCHAR | PORTAL/CHATBOT |
| `chief_complaint` | TEXT | Patient's main concern |
| `cancellation_reason` | VARCHAR | Why cancelled |
| `reminder_sent` | BOOLEAN | Whether reminder was sent |
| `rescheduled_from` | UUID | Original appointment ID if rescheduled |

### `users`
| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `clinic_id` | UUID | FK to clinic |
| `email` | VARCHAR | Unique within clinic |
| `password` | VARCHAR | BCrypt hash |
| `role` | ENUM | SUPER_ADMIN/CLINIC_ADMIN/DOCTOR/PATIENT/RECEPTIONIST |
| `is_active` | BOOLEAN | Account status |
| `created_at` | DATETIME | Creation timestamp |

### `clinics`
| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `name` | VARCHAR | Clinic name |
| `slug` | VARCHAR | Unique slug |
| `subscription_tier` | VARCHAR | starter/pro/enterprise |
| `is_active` | BOOLEAN | Whether clinic is active |

### `prescriptions`
| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `clinic_id` | UUID | FK to clinic |
| `consultation_id` | UUID | FK to consultation |
| `patient_id` | UUID | FK to user/patient |
| `doctor_id` | UUID | FK to doctor |
| `medications` | TEXT | Prescribed medications |
| `notes` | TEXT | Additional notes |

### `lab_requests`
| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `clinic_id` | UUID | FK to clinic |
| `patient_id` | UUID | FK to patient |
| `doctor_id` | UUID | FK to doctor |
| `request_number` | VARCHAR | e.g., LAB-2024-0001 |
| `priority` | ENUM | NORMAL/URGENT |
| `clinical_notes` | TEXT |
| `sample_type` | VARCHAR | BLOOD/URINE/OTHER |
| `total_price` | DECIMAL |
| `due_at` | DATETIME |

### `lab_results`
| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `request_id` | UUID | FK to lab_request |
| `test_id` | UUID | FK to lab test |
| `value` | VARCHAR | Test result value |
| `unit` | VARCHAR | e.g., %, mm³, pg/mL |
| `normal_range` | VARCHAR | Expected range |
| `is_abnormal` | BOOLEAN |
| `interpretation` | TEXT |

### `invoices`
| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `clinic_id` | UUID | FK to clinic |
| `patient_id` | UUID | FK to patient |
| `invoice_number` | VARCHAR | e.g., INV-2024-00001 |
| `appointment_id` | UUID | FK to appointment |
| `subtotal` | DECIMAL |
| `discount` | DECIMAL |
| `tax` | DECIMAL |
| `total` | DECIMAL |
| `paid_amount` | DECIMAL |
| `balance` | DECIMAL |
| `status` | ENUM | PENDING/PAID/REFUNDED |
| `due_date` | DATE |

### `payments`
| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `clinic_id` | UUID | FK to clinic |
| `invoice_id` | UUID | FK to invoice |
| `amount` | DECIMAL |
| `method` | ENUM | CASH/M-PESA/CARD |
| `reference` | VARCHAR |
| `paid_at` | DATETIME |

## Indexes & Performance
- `idx_appointments_clinic_date` — `clinic_id + appointment_date` for queue queries
- `idx_appointments_patient` — patient appointment history
- `idx_appointments_doctor_date` — doctor schedule queries
- `idx_users_clinic_role` — clinic + role filtering
- `idx_clinics_slug` — slug-based clinic lookup
- Full-text search on `chief_complaint` and `notes` columns (PostgreSQL)

## Connection Configuration
From `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:db}:5432/${DB_NAME:cliniq}
    username: ${DB_USER:cliniq}
    password: ${DB_PASSWORD:...}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
  jpa:
    hibernate:
      ddl-auto: none  # Flyway-managed
    show-sql: false
    properties:
      hibernate:
        format_sql: true
  flyway:
    enabled: true
    baseline-on-migrate: true
    validate-on-migrate: false
```

## Backup & Restore
See `BACKUP_RESTORE.md` for pg_dump/pg_restore procedures.
Flyway migrations must be up-to-date before restoring any backup.