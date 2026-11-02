-- ECTA simple electronic signatures: evidence captured at signing, email OTP, and the signed PDF.
ALTER TABLE signature_requests ADD COLUMN IF NOT EXISTS document_id UUID;
ALTER TABLE signature_requests ADD COLUMN IF NOT EXISTS signed_document_id UUID;
ALTER TABLE signature_requests ADD COLUMN IF NOT EXISTS typed_name VARCHAR(255);
ALTER TABLE signature_requests ADD COLUMN IF NOT EXISTS signer_ip VARCHAR(64);
ALTER TABLE signature_requests ADD COLUMN IF NOT EXISTS signer_user_agent VARCHAR(500);
ALTER TABLE signature_requests ADD COLUMN IF NOT EXISTS otp_hash VARCHAR(128);
ALTER TABLE signature_requests ADD COLUMN IF NOT EXISTS otp_expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE signature_requests ADD COLUMN IF NOT EXISTS otp_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE signature_requests ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_signature_requests_client ON signature_requests (tenant_id, client_id);
