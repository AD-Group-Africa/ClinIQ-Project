# ClinIQ Backend — Database Inventory

**Flyway Version**: V1, V2, V3 (3 migrations applied)
**Database**: PostgreSQL 16.14
**Schema**: `public`

> This inventory was generated from `V1__initial_schema.sql` and verified against the current entity code. `V2__add_booked_by_to_appointments.sql` adds `booked_by`, `booked_via`, `cancellation_reason`, `chief_complaint`, `reminder_sent` to `appointments`. `V3__seed_data.sql` inserts demo data (clinic, super-admin, doctor, sample patient, sample appointment).

## Tables (22 created by V1)

### 1. clinics
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| name | VARCHAR(255) | NOT NULL |
| slug | VARCHAR(100) | UNIQUE, NOT NULL |
| phone | VARCHAR(20) |
| email | VARCHAR(255) |
| address | TEXT |
| city | VARCHAR(100) |
| country | VARCHAR(2) | DEFAULT 'KE' |
| timezone | VARCHAR(50) | DEFAULT 'Africa/Nairobi' |
| currency | VARCHAR(3) | DEFAULT 'KES' |
| subscription_tier | VARCHAR(50) | DEFAULT 'starter' |
| is_active | BOOLEAN | DEFAULT TRUE |
| settings | JSONB | DEFAULT '{}' |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | DEFAULT NOW() |

### 2. users
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | REFERENCES clinics(id) ON DELETE CASCADE |
| email | VARCHAR(255) | UNIQUE, NOT NULL |
| phone | VARCHAR(20) |
| password_hash | TEXT | NOT NULL |
| first_name | VARCHAR(100) | NOT NULL |
| last_name | VARCHAR(100) | NOT NULL |
| role | VARCHAR(20) | NOT NULL — SUPER_ADMIN, CLINIC_ADMIN, DOCTOR, etc. |
| avatar_url | TEXT |
| is_active | BOOLEAN | DEFAULT TRUE |
| is_verified | BOOLEAN | DEFAULT FALSE |
| last_login_at | TIMESTAMPTZ |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | DEFAULT NOW() |

### 3. audit_logs
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | NOT NULL, REFERENCES clinics(id) |
| user_id | UUID | REFERENCES users(id) |
| action | VARCHAR(100) | NOT NULL |
| entity_type | VARCHAR(50) |
| entity_id | UUID |
| old_values | JSONB |
| new_values | JSONB |
| ip_address | VARCHAR(45) |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |

### 4. patients
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | NOT NULL, REFERENCES clinics(id) ON DELETE CASCADE |
| user_id | UUID | REFERENCES users(id) ON DELETE SET NULL |
| patient_number | VARCHAR(50) | UNIQUE |
| first_name | VARCHAR(100) | NOT NULL |
| last_name | VARCHAR(100) | NOT NULL |
| phone | VARCHAR(20) | NOT NULL |
| email | VARCHAR(255) |
| date_of_birth | DATE |
| gender | VARCHAR(20) |
| national_id | VARCHAR(50) |
| blood_group | VARCHAR(5) | DEFAULT 'UNKNOWN' |
| allergies | TEXT[] | DEFAULT '{}' |
| emergency_contact_name | VARCHAR(200) |
| emergency_contact_phone | VARCHAR(20) |
| insurance_provider | VARCHAR(200) |
| insurance_number | VARCHAR(100) |
| address | TEXT |
| notes | TEXT |
| tags | TEXT[] | DEFAULT '{}' |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | DEFAULT NOW() |

### 5. doctors
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | NOT NULL, REFERENCES clinics(id) ON DELETE CASCADE |
| user_id | UUID | NOT NULL, REFERENCES users(id) |
| first_name | VARCHAR(100) | NOT NULL |
| last_name | VARCHAR(100) | NOT NULL |
| specialization | VARCHAR(200) |
| license_number | VARCHAR(100) |
| max_patients_per_day | INT | DEFAULT 20 |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |

### 6. appointments
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | REFERENCES clinics(id) ON DELETE — currently no CASCADE |
| patient_id | UUID | NOT NULL, REFERENCES patients(id) |
| doctor_id | UUID | REFERENCES doctors(id) |
| appointment_date | DATE | NOT NULL |
| start_time | TIME | NOT NULL |
| end_time | TIME |
| status | VARCHAR(30) | DEFAULT 'SCHEDULED' |
| queue_number | INT |
| visit_type | VARCHAR(50) |
| notes | TEXT |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | DEFAULT NOW__ **MISSING COLUMN**: `booked_by` (entity expects it, not in migration) |
| **MISSING COLUMN**: `booked_via` (entity has it, migration check needed) |

### 7. doctor_schedules
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | REFERENCES clinics(id) |
| doctor_id | UUID | NOT NULL, REFERENCES doctors(id) |
| day_of_week | SMALLINT | NOT NULL — 0=Sun, 1=Mon, ... |
| start_time | TIME | NOT NULL |
| end_time | TIME | NOT NULL |
| slot_duration | INT | DEFAULT 30 — minutes |

### 8. schedule_overrides
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | NOT NULL, REFERENCES clinics(id) |
| doctor_id | UUID | NOT NULL, REFERENCES doctors(id) |
| override_date | DATE | NOT NULL |
| start_time | TIME |
| end_time | TIME |
| is_available | BOOLEAN | DEFAULT TRUE |
| reason | TEXT |

### 9. reminders
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | NOT NULL, REFERENCES clinics(id) |
| appointment_id | UUID | REFERENCES appointments(id) |
| patient_id | UUID | REFERENCES patients(id) |
| type | VARCHAR(50) | DEFAULT 'SMS' |
| scheduled_at | TIMESTAMPTZ | NOT NULL |
| sent_at | TIMESTAMPTZ |
| status | VARCHAR(20) | DEFAULT 'PENDING' |

### 10. consultations
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | NOT NULL, REFERENCES clinics(id) |
| appointment_id | UUID | UNIQUE, REFERENCES appointments(id) ON DELETE SET NULL |
| patient_id | UUID | NOT NULL, REFERENCES patients(id) |
| doctor_id | UUID | REFERENCES doctors(id) |
| visit_number | INT | DEFAULT 1 |
| chief_complaint | TEXT |
| history | TEXT |
| examination | TEXT |
| diagnosis | TEXT |
| diagnosis_codes | TEXT[] |
| treatment_plan | TEXT |
| notes | TEXT |
| vitals | JSONB | DEFAULT '{}' |
| follow_up_date | DATE |
| follow_up_notes | TEXT |
| is_admitted | BOOLEAN | DEFAULT FALSE |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | DEFAULT NOW() |

### 11. prescriptions
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | NOT NULL, REFERENCES clinics(id) |
| consultation_id | UUID | NOT NULL, REFERENCES consultations(id) ON DELETE CASCADE |
| patient_id | UUID | NOT NULL, REFERENCES patients(id) |
| doctor_id | UUID | REFERENCES doctors(id) |
| medications | JSONB | NOT NULL, DEFAULT '[]' |
| notes | TEXT |
| is_dispensed | BOOLEAN | DEFAULT FALSE |
| created_at | TIMESTAMPTZ | DEFAULT NOW__ |

### 12. drugs
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | NOT NULL, REFERENCES clinics(id) ON DELETE CASCADE |
| name | VARCHAR(300) | NOT NULL |
| generic_name | VARCHAR(300) |
| brand_name | VARCHAR(300) |
| category | VARCHAR(50) | DEFAULT 'OTHER' |
| unit | VARCHAR(50) | DEFAULT 'TABLETS' |
| strength | VARCHAR(100) |
| description | TEXT |
| requires_prescription | BOOLEAN | DEFAULT TRUE |
| is_controlled | BOOLEAN | DEFAULT FALSE |
| reorder_level | INT | DEFAULT 10 |
| selling_price | DECIMAL(10,2) | DEFAULT 0 |
| is_active | BOOLEAN | DEFAULT TRUE |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | DEFAULT NOW() |

### 13. suppliers
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | NOT NULL, REFERENCES clinics(id) ON DELETE CASCADE |
| name | VARCHAR(200) | NOT NULL |
| contact_name | VARCHAR(200) |
| phone | VARCHAR(20) |
| email | VARCHAR(200) |
| address | TEXT |
| is_active | BOOLEAN | DEFAULT TRUE |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |

### 14. drug_batches
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | NOT NULL, REFERENCES clinics(id) ON DELETE CASCADE |
| drug_id | UUID | NOT NULL, REFERENCES drugs(id) ON DELETE CASCADE |
| supplier_id | UUID | REFERENCES suppliers(id) ON DELETE SET NULL |
| batch_number | VARCHAR(100) | NOT NULL |
| quantity_received | INT | NOT NULL |
| quantity_remaining | INT | NOT NULL |
| unit_cost | DECIMAL(10,2) | DEFAULT 0 |
| expiry_date | DATE | NOT NULL |
| received_by | UUID | REFERENCES users(id) |
| notes | TEXT |
| created_at | TIMESTAMPTZ | DEFAULT NOW__ |

### 15. drug_dispenses
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | NOT NULL, REFERENCES clinics(id) ON DELETE CASCADE |
| drug_id | UUID | NOT NULL, REFERENCES drugs(id) ON DELETE CASCADE |
| batch_id | UUID | REFERENCES drug_batches(id) ON DELETE SET NULL |
| consultation_id | UUID | REFERENCES consultations(id) ON DELETE SET NULL |
| patient_id | UUID | NOT NULL, REFERENCES patients(id) ON DELETE CASCADE |
| prescribed_by | UUID | REFERENCES users(id) |
| dispensed_by | UUID | REFERENCES users(id) |
| quantity | INT | NOT NULL |
| unit_price | DECIMAL(10,2) |
| total_price | DECIMAL(10,2) |
| instructions | TEXT |
| dispensed_at | TIMESTAMPTZ | DEFAULT NOW() |

### 16. lab_tests
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | NOT NULL, REFERENCES clinics(id) ON DELETE CASCADE |
| name | VARCHAR(300) | NOT NULL |
| code | VARCHAR(50) |
| category | VARCHAR(100) |
| description | TEXT |
| normal_range | TEXT |
| price | DECIMAL(10,2) | DEFAULT 0 |
| turnaround_hours | INT | DEFAULT 24 |
| is_active | BOOLEAN | DEFAULT TRUE |
| created_at | TIMESTAMPTZ | DEFAULT NOW__ |

### 17. lab_requests
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | NOT NULL, REFERENCES clinics(id) ON DELETE CASCADE |
| patient_id | UUID | NOT NULL, REFERENCES patients(id) ON DELETE CASCADE |
| doctor_id | UUID | REFERENCES doctors(id) |
| consultation_id | UUID | REFERENCES consultations(id) ON DELETE SET NULL |
| request_number | VARCHAR(50) | UNIQUE |
| status | VARCHAR(20) | DEFAULT 'REQUESTED' |
| priority | VARCHAR(10) | DEFAULT 'ROUTINE' |
| tests | JSONB | NOT NULL, DEFAULT '[]' |
| total_price | DECIMAL(10,2) | DEFAULT 0 |
| clinical_notes | TEXT |
| sample_type | VARCHAR(100) |
| sample_collected_at | TIMESTAMPTZ |
| due_at | TIMESTAMPTZ |
| completed_at | TIMESTAMPTZ |
| created_at | TIMESTAMPTZ | DEFAULT NOW__ |
| updated_at | TIMESTAMPTZ | DEFAULT NOW__ |

### 18. lab_results
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | NOT NULL, REFERENCES clinics(id) ON DELETE CASCADE |
| request_id | UUID | NOT NULL, REFERENCES lab_requests(id) ON DELETE CASCADE |
| test_id | UUID | REFERENCES lab_tests(id) ON DELETE SET NULL |
| test_name | VARCHAR(300) | NOT NULL |
| value | TEXT |
| unit | VARCHAR(50) |
| normal_range | TEXT |
| is_abnormal | BOOLEAN | DEFAULT FALSE |
| interpretation | TEXT |
| performed_by | UUID | REFERENCES users(id) |
| verified_by | UUID | REFERENCES users(id) |
| result_pdf_url | TEXT |
| created_at | TIMESTAMPTZ | DEFAULT NOW__ |
| updated_at | TIMESTAMPTZ | DEFAULT NOW__ |

### 19. invoices
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | REFERENCES clinics(id) |
| patient_id | UUID | NOT NULL, REFERENCES patients(id) |
| appointment_id | UUID | REFERENCES appointments(id) |
| invoice_number | VARCHAR(50) | UNIQUE, NOT NULL |
| issued_at | TIMESTAMPTZ | DEFAULT NOW__ |
| due_date | DATE |
| line_items | JSONB | DEFAULT '[]' |
| subtotal | DECIMAL(10,2) | DEFAULT 0 |
| discount | DECIMAL(10,2) | DEFAULT 0 |
| tax | DECIMAL(10,2) | DEFAULT 0 |
| total | DECIMAL(10,2) | DEFAULT 0 |
| paid_amount | DECIMAL(10,2) | DEFAULT 0 |
| status | VARCHAR(20) | DEFAULT 'DRAFT' |
| notes | TEXT |
| created_by | UUID | REFERENCES users(id) |
| created_at | TIMESTAMPTZ | DEFAULT NOW__ |
| updated_at | TIMESTAMPTZ | DEFAULT NOW__ |

### 20. payments
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | NOT NULL, REFERENCES clinics(id) |
| invoice_id | UUID | NOT NULL, REFERENCES invoices(id) |
| patient_id | UUID | REFERENCES patients(id) |
| amount | DECIMAL(10,2) | NOT NULL |
| method | VARCHAR(20) | NOT NULL — CASH, MPESA, CARD, etc. |
| reference | VARCHAR(100) |
| mpesa_phone | VARCHAR(20) |
| notes | TEXT |
| paid_at | TIMESTAMPTZ | DEFAULT NOW__ |
| recorded_by | UUID | REFERENCES users(id) |
| created_at | TIMESTAMPTZ | DEFAULT NOW__ |

### 21. communications
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | NOT NULL, REFERENCES clinics(id) |
| patient_id | UUID | NOT NULL, REFERENCES patients(id) |
| appointment_id | UUID | REFERENCES appointments(id) |
| channel | VARCHAR(10) | DEFAULT 'SMS' |
| type | VARCHAR(30) | NOT NULL |
| body | TEXT | NOT NULL |
| status | VARCHAR(20) | DEFAULT 'PENDING' |
| created_at | TIMESTAMPTZ | DEFAULT NOW__ |
| updated_at | TIMESTAMPTZ | DEFAULT NOW__ |

### 22. crm_campaigns
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() |
| clinic_id | UUID | REFERENCES clinics(id) |
| name | VARCHAR(200) | NOT NULL |
| description | TEXT |
| channel | VARCHAR(10) | NOT NULL |
| target_segment | JSONB | DEFAULT '{}' |
| message_template | TEXT | NOT NULL |
| scheduled_at | TIMESTAMPTZ |
| status | VARCHAR(20) | DEFAULT 'DRAFT' |
| sent_count | INT | DEFAULT 0 |
| delivered_count | INT | DEFAULT 0 |
| failed_count | INT | DEFAULT 0 |
| created_by | UUID | REFERENCES users(id) |
| created_at | TIMESTAMPTZ | DEFAULT NOW__ |
| updated_at | TIMESTAMPTZ | DEFAULT NOW__ |

### 23. v_drug_stock (view)
| Column | Type | Description |
|--------|------|-------------|
| clinic_id | UUID | Clinic identifier |
| drug_id | UUID | Drug identifier |
| name | VARCHAR(300) | Drug name |
| generic_name | VARCHAR(300) | Generic name |
| category | VARCHAR(50) | Drug category |
| unit | VARCHAR(50) | Unit of measure |
| strength | VARCHAR(100) | Strength |
| reorder_level | INT | reorder threshold |
| selling_price | DECIMAL(10,2) | Selling price |
| total_stock | INT | SUM of quantity_remaining |
| batches_expiring_soon | INT | Count of batches expiring < 30 days |
| nearest_expiry | DATE | Earliest expiry date |
| stock_status | VARCHAR(20) | OUT_OF_STOCK / LOW_STOCK / IN_STOCK |

## Indexes (12 created)

| Index | Table | Columns | Purpose |
|-------|-------|---------|---------|
| idx_patients_clinic_phone | patients | (clinic_id, phone) | Quick patient lookup by clinic+phone |
| idx_appointments_clinic_date | appointments | (clinic_id, appointment_date) | Appointments by clinic+date |
| idx_appointments_status | appointments | (clinic_id, status) | Filter appointments by status |
| idx_drug_batches_drug | drug_batches | (clinic_id, drug_id, expiry_date) | Batch tracking by drug |
| idx_drug_batches_expiry | drug_batches | (clinic_id, expiry_date) WHERE quantity_remaining > 0 | Low stock / expiry alerts |
| idx_lab_requests_patient | lab_requests | (clinic_id, patient_id, created_at DESC) | Lab requests by patient |
| idx_lab_requests_status | lab_requests | (clinic_id, status, priority) | Lab requests by status+priority |
| idx_consultations_patient | consultations | (clinic_id, patient_id, created_at DESC) | Consultations by patient |
| idx_invoices_clinic_patient | invoices | (clinic_id, patient_id, issued_at DESC) | Invoices by clinic+patient |
| + 3 additional indexes defined in migration |

## Views

| View | Description |
|------|-------------|
| `v_drug_stock` | Aggregated drug stock status with FEFO logic — sums quantities across batches, flags low stock and expiring batches. Used by pharmacy service. |

## ⚠️ Schema Issues & Inconsistencies

1. **`booked_by` column missing from `appointments`**
   - Entity `Appointment` has `@Column(name = "booked_by") val bookedBy: UUID? = null`
   - Migration `V1__initial_schema.sql` does NOT include `booked_by` column
   - **Result**: Hibernate `SchemaValidationException` on startup
   - **Fix needed**: Add `booked_by` column to migration or remove from entity

2. **`clinic_id` foreign key on `appointments`**
   - Migration: `clinic_id UUID REFERENCES clinics(id)` (no ON DELETE CASCADE)
   - Entity may expect different behavior
   - **Fix needed**: Verify and align

3. **`role` column on `users` vs Spring Security**
   - DB column: `VARCHAR(20)` with values `SUPER_ADMIN`, `CLINIC_ADMIN`, etc.
   - Spring Security `@PreAuthorize("hasAnyRole('ROLE_ADMIN')")` expects authorities
   - **Fix needed**: Implement Role entity or map values to GrantedAuthority

4. **`payments.method` values**
   - Expected: `CASH`, `MPESA`, `CARD`, etc.
   - No enum constraint in migration (VARCHAR without check)
   - **Fix needed**: Consider adding check constraint or enum table

5. **No `ON DELETE CASCADE` on `appointments.clinic_id`**
   - If clinic is deleted, appointments remain orphaned
   - **Fix needed**: Add CASCADE or SET NULL depending on business logic

## Flyway Schema History

- Table: `flyway_schema_history` (created automatically on first migration)
- Current state: At version `v1` (1 migration applied)
- `validate-on-migrate: true` in config — will re-validate on every run
- Baseline not yet set — fresh database only