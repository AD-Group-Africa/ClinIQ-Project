-- ============================================================
-- V1__initial_schema.sql — Complete ClinIQ database schema
-- ============================================================

-- Enums (as PostgreSQL text types — Hibernate handles)
-- We define them as checks or just use VARCHAR; JPA enums are strings.
-- No CREATE TYPE needed unless using native enums; for simplicity we use VARCHAR.

-- ============================================================
-- 1. CORE: clinics, users, audit
-- ============================================================
CREATE TABLE IF NOT EXISTS clinics (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) UNIQUE NOT NULL,
    phone           VARCHAR(20),
    email           VARCHAR(255),
    address         TEXT,
    city            VARCHAR(100),
    country         VARCHAR(2) DEFAULT 'KE',
    logo_url        TEXT,
    website         TEXT,
    timezone        VARCHAR(50) DEFAULT 'Africa/Nairobi',
    currency        VARCHAR(3) DEFAULT 'KES',
    subscription_tier VARCHAR(50) DEFAULT 'starter',
    is_active       BOOLEAN DEFAULT TRUE,
    settings        JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID REFERENCES clinics(id) ON DELETE CASCADE,
    email           VARCHAR(255) UNIQUE NOT NULL,
    phone           VARCHAR(20),
    password_hash   TEXT NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    role            VARCHAR(20) NOT NULL,      -- SUPER_ADMIN, CLINIC_ADMIN, DOCTOR, etc.
    avatar_url      TEXT,
    is_active       BOOLEAN DEFAULT TRUE,
    is_verified     BOOLEAN DEFAULT FALSE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id),
    user_id         UUID REFERENCES users(id),
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(50),
    entity_id       UUID,
    old_values      JSONB,
    new_values      JSONB,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 2. PATIENTS, DOCTORS
-- ============================================================
CREATE TABLE IF NOT EXISTS patients (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    patient_number  VARCHAR(50) UNIQUE,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(20) NOT NULL,
    email           VARCHAR(255),
    date_of_birth   DATE,
    gender          VARCHAR(20),
    national_id     VARCHAR(50),
    blood_group     VARCHAR(5) DEFAULT 'UNKNOWN',
    allergies       TEXT[] DEFAULT '{}',
    emergency_contact_name VARCHAR(200),
    emergency_contact_phone VARCHAR(20),
    insurance_provider VARCHAR(200),
    insurance_number VARCHAR(100),
    address         TEXT,
    notes           TEXT,
    tags            TEXT[] DEFAULT '{}',
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS doctors (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id),
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    specialization  VARCHAR(200),
    license_number  VARCHAR(100),
    max_patients_per_day INT DEFAULT 20,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 3. APPOINTMENTS
-- ============================================================
CREATE TABLE IF NOT EXISTS appointments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id),
    patient_id      UUID NOT NULL REFERENCES patients(id),
    doctor_id       UUID REFERENCES doctors(id),
    appointment_date DATE NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME,
    status          VARCHAR(30) DEFAULT 'SCHEDULED',  -- SCHEDULED, CONFIRMED, CHECKED_IN, IN_CONSULTATION, COMPLETED, NO_SHOW, CANCELLED
    queue_number    INT,
    visit_type      VARCHAR(50),
    notes           TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS doctor_schedules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id),
    doctor_id       UUID NOT NULL REFERENCES doctors(id),
    day_of_week     SMALLINT NOT NULL,  -- 0=Sun, 1=Mon,...
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    slot_duration   INT DEFAULT 30      -- minutes
);

CREATE TABLE IF NOT EXISTS schedule_overrides (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id),
    doctor_id       UUID NOT NULL REFERENCES doctors(id),
    override_date   DATE NOT NULL,
    start_time      TIME,
    end_time        TIME,
    is_available    BOOLEAN DEFAULT TRUE,
    reason          TEXT
);

CREATE TABLE IF NOT EXISTS reminders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id),
    appointment_id  UUID REFERENCES appointments(id),
    patient_id      UUID REFERENCES patients(id),
    type            VARCHAR(50) DEFAULT 'SMS',
    scheduled_at    TIMESTAMPTZ NOT NULL,
    sent_at         TIMESTAMPTZ,
    status          VARCHAR(20) DEFAULT 'PENDING'
);

-- ============================================================
-- 4. CONSULTATIONS & PRESCRIPTIONS
-- ============================================================
CREATE TABLE IF NOT EXISTS consultations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id),
    appointment_id  UUID UNIQUE REFERENCES appointments(id) ON DELETE SET NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    doctor_id       UUID REFERENCES doctors(id),
    visit_number    INT DEFAULT 1,
    chief_complaint TEXT,
    history         TEXT,
    examination     TEXT,
    diagnosis       TEXT,
    diagnosis_codes TEXT[],
    treatment_plan  TEXT,
    notes           TEXT,
    vitals          JSONB DEFAULT '{}',
    follow_up_date  DATE,
    follow_up_notes TEXT,
    is_admitted     BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS prescriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id),
    consultation_id UUID NOT NULL REFERENCES consultations(id) ON DELETE CASCADE,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    doctor_id       UUID REFERENCES doctors(id),
    medications     JSONB NOT NULL DEFAULT '[]',
    notes           TEXT,
    is_dispensed    BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 5. PHARMACY (FEFO inventory)
-- ============================================================
CREATE TABLE IF NOT EXISTS drugs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
    name            VARCHAR(300) NOT NULL,
    generic_name    VARCHAR(300),
    brand_name      VARCHAR(300),
    category        VARCHAR(50) DEFAULT 'OTHER',
    unit            VARCHAR(50) DEFAULT 'TABLETS',
    strength        VARCHAR(100),
    description     TEXT,
    requires_prescription BOOLEAN DEFAULT TRUE,
    is_controlled   BOOLEAN DEFAULT FALSE,
    reorder_level   INT DEFAULT 10,
    selling_price   DECIMAL(10,2) DEFAULT 0,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS suppliers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    contact_name    VARCHAR(200),
    phone           VARCHAR(20),
    email           VARCHAR(200),
    address         TEXT,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS drug_batches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
    drug_id         UUID NOT NULL REFERENCES drugs(id) ON DELETE CASCADE,
    supplier_id     UUID REFERENCES suppliers(id) ON DELETE SET NULL,
    batch_number    VARCHAR(100) NOT NULL,
    quantity_received INT NOT NULL,
    quantity_remaining INT NOT NULL,
    unit_cost       DECIMAL(10,2) DEFAULT 0,
    expiry_date     DATE NOT NULL,
    received_by     UUID REFERENCES users(id),
    notes           TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS drug_dispenses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
    drug_id         UUID NOT NULL REFERENCES drugs(id) ON DELETE CASCADE,
    batch_id        UUID REFERENCES drug_batches(id) ON DELETE SET NULL,
    consultation_id UUID REFERENCES consultations(id) ON DELETE SET NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    prescribed_by   UUID REFERENCES users(id),
    dispensed_by    UUID REFERENCES users(id),
    quantity        INT NOT NULL,
    unit_price      DECIMAL(10,2),
    total_price     DECIMAL(10,2),
    instructions    TEXT,
    dispensed_at    TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 6. LABORATORY
-- ============================================================
CREATE TABLE IF NOT EXISTS lab_tests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
    name            VARCHAR(300) NOT NULL,
    code            VARCHAR(50),
    category        VARCHAR(100),
    description     TEXT,
    normal_range    TEXT,
    price           DECIMAL(10,2) DEFAULT 0,
    turnaround_hours INT DEFAULT 24,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS lab_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
    patient_id      UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    doctor_id       UUID REFERENCES doctors(id) ON DELETE SET NULL,
    consultation_id UUID REFERENCES consultations(id) ON DELETE SET NULL,
    request_number  VARCHAR(50) UNIQUE,
    status          VARCHAR(20) DEFAULT 'REQUESTED',
    priority        VARCHAR(10) DEFAULT 'ROUTINE',
    tests           JSONB NOT NULL DEFAULT '[]',
    total_price     DECIMAL(10,2) DEFAULT 0,
    clinical_notes  TEXT,
    sample_type     VARCHAR(100),
    sample_collected_at TIMESTAMPTZ,
    due_at          TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS lab_results (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
    request_id      UUID NOT NULL REFERENCES lab_requests(id) ON DELETE CASCADE,
    test_id         UUID REFERENCES lab_tests(id) ON DELETE SET NULL,
    test_name       VARCHAR(300) NOT NULL,
    value           TEXT,
    unit            VARCHAR(50),
    normal_range    TEXT,
    is_abnormal     BOOLEAN DEFAULT FALSE,
    interpretation  TEXT,
    performed_by    UUID REFERENCES users(id),
    verified_by     UUID REFERENCES users(id),
    result_pdf_url  TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 7. BILLING & PAYMENTS
-- ============================================================
CREATE TABLE IF NOT EXISTS invoices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id),
    patient_id      UUID NOT NULL REFERENCES patients(id),
    appointment_id  UUID REFERENCES appointments(id),
    invoice_number  VARCHAR(50) UNIQUE NOT NULL,
    issued_at       TIMESTAMPTZ DEFAULT NOW(),
    due_date        DATE,
    line_items      JSONB DEFAULT '[]',
    subtotal        DECIMAL(10,2) DEFAULT 0,
    discount        DECIMAL(10,2) DEFAULT 0,
    tax             DECIMAL(10,2) DEFAULT 0,
    total           DECIMAL(10,2) DEFAULT 0,
    paid_amount     DECIMAL(10,2) DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'DRAFT',
    notes           TEXT,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id),
    invoice_id      UUID NOT NULL REFERENCES invoices(id),
    patient_id      UUID REFERENCES patients(id),
    amount          DECIMAL(10,2) NOT NULL,
    method          VARCHAR(20) NOT NULL,  -- CASH, MPESA, CARD, etc.
    reference       VARCHAR(100),
    mpesa_phone     VARCHAR(20),
    notes           TEXT,
    paid_at         TIMESTAMPTZ DEFAULT NOW(),
    recorded_by     UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 8. COMMUNICATION
-- ============================================================
CREATE TABLE IF NOT EXISTS communications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id),
    patient_id      UUID NOT NULL REFERENCES patients(id),
    appointment_id  UUID REFERENCES appointments(id),
    channel         VARCHAR(10) DEFAULT 'SMS',
    type            VARCHAR(30) NOT NULL,
    body            TEXT NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 9. CRM
-- ============================================================
CREATE TABLE IF NOT EXISTS crm_campaigns (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id),
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    channel         VARCHAR(10) NOT NULL,
    target_segment  JSONB DEFAULT '{}',
    message_template TEXT NOT NULL,
    scheduled_at    TIMESTAMPTZ,
    status          VARCHAR(20) DEFAULT 'DRAFT',
    sent_count      INT DEFAULT 0,
    delivered_count INT DEFAULT 0,
    failed_count    INT DEFAULT 0,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 10. INDEXES for performance
-- ============================================================
CREATE INDEX idx_patients_clinic_phone ON patients(clinic_id, phone);
CREATE INDEX idx_appointments_clinic_date ON appointments(clinic_id, appointment_date);
CREATE INDEX idx_appointments_status ON appointments(clinic_id, status);
CREATE INDEX idx_drug_batches_drug ON drug_batches(clinic_id, drug_id, expiry_date);
CREATE INDEX idx_drug_batches_expiry ON drug_batches(clinic_id, expiry_date) WHERE quantity_remaining > 0;
CREATE INDEX idx_lab_requests_patient ON lab_requests(clinic_id, patient_id, created_at DESC);
CREATE INDEX idx_lab_requests_status ON lab_requests(clinic_id, status, priority);
CREATE INDEX idx_consultations_patient ON consultations(clinic_id, patient_id, created_at DESC);
CREATE INDEX idx_invoices_clinic_patient ON invoices(clinic_id, patient_id, issued_at DESC);

-- ============================================================
-- 11. VIEW for drug stock (required by pharmacy service)
-- ============================================================
CREATE OR REPLACE VIEW v_drug_stock AS
SELECT
    d.clinic_id,
    d.id AS drug_id,
    d.name,
    d.generic_name,
    d.category,
    d.unit,
    d.strength,
    d.reorder_level,
    d.selling_price,
    COALESCE(SUM(b.quantity_remaining), 0) AS total_stock,
    COUNT(b.id) FILTER (WHERE b.expiry_date < CURRENT_DATE + INTERVAL '30 days' AND b.quantity_remaining > 0) AS batches_expiring_soon,
    MIN(b.expiry_date) FILTER (WHERE b.quantity_remaining > 0) AS nearest_expiry,
    CASE
        WHEN COALESCE(SUM(b.quantity_remaining), 0) = 0 THEN 'OUT_OF_STOCK'
        WHEN COALESCE(SUM(b.quantity_remaining), 0) <= d.reorder_level THEN 'LOW_STOCK'
        ELSE 'IN_STOCK'
    END AS stock_status
FROM drugs d
LEFT JOIN drug_batches b ON b.drug_id = d.id AND b.clinic_id = d.clinic_id
WHERE d.is_active = TRUE
GROUP BY d.clinic_id, d.id, d.name, d.generic_name, d.category, d.unit,
         d.strength, d.reorder_level, d.selling_price;
