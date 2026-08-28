-- V9__fix_lab_doctor_fk.sql
-- lab_requests.doctor_id was FK'd to doctors(id) but the service stores the User id
-- (auth.userId). The DemoUserSeeder creates Users but the FK blocks lab request creation.
-- Drop the strict FK; clinic isolation is via lab_requests.clinic_id.

ALTER TABLE lab_requests DROP CONSTRAINT IF EXISTS lab_requests_doctor_id_fkey;
-- Also fix appointments if it has similar FK (defensive)
ALTER TABLE appointments DROP CONSTRAINT IF EXISTS appointments_doctor_id_fkey;
