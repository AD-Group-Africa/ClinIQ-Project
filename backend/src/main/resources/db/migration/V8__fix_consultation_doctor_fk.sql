-- V8__fix_consultation_doctor_fk.sql
-- The consultations.doctor_id column was FK'd to doctors(id) but the service stores the User id
-- (auth.userId) as doctorId. The DemoUserSeeder creates Users but not Doctors, so the FK
-- blocks consultation creation for the demo doctor. The doctor reference is advisory;
-- the authoritative clinic isolation is via consultations.clinic_id. Drop the strict FK.

ALTER TABLE consultations DROP CONSTRAINT IF EXISTS consultations_doctor_id_fkey;
-- Keep the column but without FK; application enforces clinic isolation via clinic_id
