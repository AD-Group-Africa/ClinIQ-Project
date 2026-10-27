ALTER TABLE doctor_schedules
ALTER COLUMN day_of_week TYPE INTEGER
USING day_of_week::INTEGER;