-- V7__doctor_schedules_day_of_week_type.sql
-- doctor_schedules.day_of_week is mapped to Kotlin Int in the DoctorSchedule
-- entity; the schema declared SMALLINT. Align the column type so Hibernate
-- `ddl-auto: validate` passes.

ALTER TABLE doctor_schedules
    ALTER COLUMN day_of_week TYPE INTEGER;