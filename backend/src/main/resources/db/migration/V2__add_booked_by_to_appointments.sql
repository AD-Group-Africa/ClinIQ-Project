-- V2__add_booked_by_to_appointments.sql
-- Add missing booked_by column to appointments table
-- This column tracks which user created/Booked the appointment

ALTER TABLE appointments
ADD COLUMN booked_by UUID NULL REFERENCES users(id),
ADD COLUMN booked_via VARCHAR(50) DEFAULT 'PORTAL',
ADD COLUMN cancellation_reason VARCHAR(200),
ADD COLUMN chief_complaint TEXT,
ADD COLUMN reminder_sent BOOLEAN DEFAULT FALSE;

COMMENT ON COLUMN appointments.booked_by IS 'User who booked the appointment; references users table';
COMMENT ON COLUMN appointments.booked_via IS 'Channel via which the appointment was booked';