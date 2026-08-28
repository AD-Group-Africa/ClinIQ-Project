-- V6__align_remaining_entities.sql
-- Completes the entity/schema alignment started in V5 so that Hibernate
-- `ddl-auto: validate` (dev profile) passes for every mapped entity.

-- doctors: entity expects qualification, bio, consultation_fee, is_available
ALTER TABLE doctors
    ADD COLUMN qualification     VARCHAR(200),
    ADD COLUMN bio               TEXT,
    ADD COLUMN consultation_fee  DECIMAL(10,2) DEFAULT 0,
    ADD COLUMN is_available      BOOLEAN DEFAULT TRUE;

-- doctor_schedules: entity expects slot_duration_minutes, is_active, created_at
ALTER TABLE doctor_schedules
    ADD COLUMN slot_duration_minutes INT DEFAULT 30,
    ADD COLUMN is_active             BOOLEAN DEFAULT TRUE,
    ADD COLUMN created_at            TIMESTAMPTZ DEFAULT NOW();

-- schedule_overrides: entity expects created_at
ALTER TABLE schedule_overrides
    ADD COLUMN created_at TIMESTAMPTZ DEFAULT NOW();

-- communications: entity expects subject, external_ref, sent_at, delivered_at, failed_reason
ALTER TABLE communications
    ADD COLUMN subject       VARCHAR(255),
    ADD COLUMN external_ref  VARCHAR(100),
    ADD COLUMN sent_at       TIMESTAMPTZ,
    ADD COLUMN delivered_at  TIMESTAMPTZ,
    ADD COLUMN failed_reason TEXT;