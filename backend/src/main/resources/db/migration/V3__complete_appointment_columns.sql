ALTER TABLE appointments
    ADD COLUMN booked_via VARCHAR(50) DEFAULT 'PORTAL',
    ADD COLUMN type VARCHAR(50) DEFAULT 'CONSULTATION',
    ADD COLUMN reminder_sent BOOLEAN DEFAULT FALSE,
    ADD COLUMN cancellation_reason TEXT,
    ADD COLUMN rescheduled_from UUID;