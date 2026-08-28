-- V4__add_clinic_subscription_expiry.sql
-- Adds the clinic subscription expiry column required by the Clinic entity.
-- This supports SaaS commercial readiness: a clinic's software subscription
-- (distinct from patient medical billing) can expire / be renewed.

ALTER TABLE clinics
    ADD COLUMN subscription_expires_at TIMESTAMPTZ;

COMMENT ON COLUMN clinics.subscription_expires_at IS
    'When the clinic''s ClinIQ software subscription expires (NULL = no expiry / perpetual trial).';