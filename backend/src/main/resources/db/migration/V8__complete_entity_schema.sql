-- Complete missing columns required by JPA entities

-- Communications
ALTER TABLE communications
    ADD COLUMN subject VARCHAR(255),
    ADD COLUMN sent_at TIMESTAMPTZ,
    ADD COLUMN failed_reason TEXT;

-- Doctor schedules
ALTER TABLE doctor_schedules
    ADD COLUMN slot_duration_minutes INT DEFAULT 30,
    ADD COLUMN is_active BOOLEAN DEFAULT TRUE,
    ADD COLUMN created_at TIMESTAMPTZ DEFAULT NOW();

-- Doctors
ALTER TABLE doctors
    ADD COLUMN qualification VARCHAR(255),
    ADD COLUMN bio TEXT,
    ADD COLUMN consultation_fee NUMERIC(12,2) DEFAULT 0,
    ADD COLUMN is_available BOOLEAN DEFAULT TRUE;

-- Reminders
ALTER TABLE reminders
    ADD COLUMN send_at TIMESTAMPTZ,
    ADD COLUMN channel VARCHAR(50),
    ADD COLUMN is_sent BOOLEAN DEFAULT FALSE,
    ADD COLUMN created_at TIMESTAMPTZ DEFAULT NOW();

-- Schedule overrides
ALTER TABLE schedule_overrides
    ADD COLUMN created_at TIMESTAMPTZ DEFAULT NOW();