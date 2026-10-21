-- Find a Lawyer: operator can hide a listing, with a reason the firm can see.
ALTER TABLE directory_profiles ADD COLUMN IF NOT EXISTS operator_hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE directory_profiles ADD COLUMN IF NOT EXISTS hidden_reason VARCHAR(500);
ALTER TABLE directory_profiles ADD COLUMN IF NOT EXISTS hidden_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE directory_enquiries ADD COLUMN IF NOT EXISTS practice_area VARCHAR(64);
