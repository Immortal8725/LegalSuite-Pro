-- Client inbox per matter, and an optional firm note added to the plain-language stage summary.
CREATE TABLE IF NOT EXISTS matter_messages (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    case_id UUID NOT NULL,
    client_id UUID,
    sender_type VARCHAR(20) NOT NULL,
    sender_id UUID,
    sender_name VARCHAR(255),
    kind VARCHAR(30) NOT NULL DEFAULT 'message',
    body VARCHAR(8000),
    document_id UUID,
    attachment_name VARCHAR(255),
    attachment_size BIGINT,
    read_by_client_at TIMESTAMP WITH TIME ZONE,
    read_by_staff_at TIMESTAMP WITH TIME ZONE,
    read_by_staff_id UUID,
    created_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_matter_messages_case ON matter_messages (tenant_id, case_id, created_at);
CREATE INDEX IF NOT EXISTS idx_matter_messages_unread_staff ON matter_messages (tenant_id, sender_type, read_by_staff_at);

ALTER TABLE cases ADD COLUMN IF NOT EXISTS client_note VARCHAR(2000);
