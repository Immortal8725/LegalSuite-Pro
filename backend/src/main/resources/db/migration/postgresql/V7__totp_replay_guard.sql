-- A TOTP code is accepted once: the last time step used is kept per user.
ALTER TABLE users ADD COLUMN IF NOT EXISTS totp_last_step BIGINT;
