-- V3__seed_data.sql
-- Seed data for initial deployment.
--
-- Creates the demo clinic, plus a demo patient and appointment for the
-- Patient vertical slice.
--
-- IMPORTANT: demo users (admin, doctor, nurse, receptionist, finance) are
-- NOT created here. They are created by DemoUserSeeder on application start
-- using real BCrypt hashes generated at runtime. This guarantees that the
-- documented test passwords actually work.

-- 1. Insert default clinic
INSERT INTO clinics (name, slug, email, phone, address, city, country, timezone, currency, subscription_tier, is_active, settings)
VALUES (
    'ClinIQ Demo Clinic',
    'demo',
    'demo@cliniq.app',
    '+254700000000',
    'Nairobi, Kenya',
    'Nairobi',
    'KE',
    'Africa/Nairobi',
    'KES',
    'pro',
    TRUE,
    '{}'
) ON CONFLICT (slug) DO NOTHING;

-- 2. Insert sample patient for the demo clinic
INSERT INTO patients (clinic_id, patient_number, first_name, last_name, phone, email, date_of_birth, gender, blood_group, allergies, emergency_contact_name, emergency_contact_phone, insurance_provider, insurance_number, address, notes, tags)
SELECT
    c.id,
    'PAT001',
    'John',
    'Doe',
    '+254700000003',
    'john.doe@example.com',
    '1980-05-15',
    'MALE',
    'O_POS',
    ARRAY[]::TEXT[],
    'Jane Doe',
    '+254700000004',
    'Amana Health',
    'ANH123456',
    'Nairobi, Kenya',
    'New patient registration',
    ARRAY['new']::TEXT[]
FROM clinics c
WHERE c.slug = 'demo'
  AND NOT EXISTS (
    SELECT 1 FROM patients p WHERE p.clinic_id = c.id AND p.patient_number = 'PAT001'
  );

-- 3. Insert sample appointment (doctor_id is NULL; populated once the doctor user is seeded)
INSERT INTO appointments (clinic_id, patient_id, doctor_id, appointment_date, start_time, end_time, status, queue_number, visit_type, notes, created_at)
SELECT
    c.id,
    p.id,
    NULL,
    CURRENT_DATE,
    '09:00:00',
    '10:00:00',
    'SCHEDULED',
    1,
    'Consultation',
    'First consultation - John Doe',
    NOW()
FROM clinics c
JOIN patients p ON p.clinic_id = c.id AND p.patient_number = 'PAT001'
WHERE c.slug = 'demo'
  AND NOT EXISTS (
    SELECT 1 FROM appointments a
    WHERE a.clinic_id = c.id
      AND a.patient_id = p.id
      AND a.appointment_date = CURRENT_DATE
  );

-- End of V3 seed data
