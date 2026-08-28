-- V5__align_entities_with_schema.sql
-- Aligns the schema with the JPA entities so that `ddl-auto: validate`
-- (dev profile) passes and the application starts.
--
-- Appointment entity:
--   - `type` (AppointmentType enum) was not in the schema (only visit_type)
--   - `rescheduled_from` (UUID) was not in the schema
-- Reminder entity:
--   - maps send_at / channel / is_sent / created_at, which were not in the schema

ALTER TABLE appointments
    ADD COLUMN type VARCHAR(30) DEFAULT 'CONSULTATION',
    ADD COLUMN rescheduled_from UUID REFERENCES appointments(id);

ALTER TABLE reminders
    ADD COLUMN send_at TIMESTAMPTZ,
    ADD COLUMN channel VARCHAR(10),
    ADD COLUMN is_sent BOOLEAN DEFAULT FALSE,
    ADD COLUMN created_at TIMESTAMPTZ DEFAULT NOW();

COMMENT ON COLUMN appointments.type IS 'Appointment type (CONSULTATION, FOLLOW_UP, PROCEDURE, CHECK_UP, WALK_IN, TELECONSULTATION)';