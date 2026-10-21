-- Operator soft delete of a firm. Purge is allowed 30 days later.
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS deleted_reason VARCHAR(500);
