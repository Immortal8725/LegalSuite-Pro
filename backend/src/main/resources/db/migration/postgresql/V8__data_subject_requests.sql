-- POPIA data subject requests: deletion or anonymisation asked for by a client or a firm user, decided by a firm admin.
CREATE TABLE IF NOT EXISTS data_subject_requests (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    subject_type VARCHAR(255),
    subject_id UUID,
    subject_name VARCHAR(255),
    kind VARCHAR(255),
    status VARCHAR(255),
    requested_by UUID,
    requested_by_role VARCHAR(255),
    reason VARCHAR(2000),
    decision_note VARCHAR(2000),
    decided_by UUID,
    outcome VARCHAR(4000),
    created_at TIMESTAMP(6) WITH TIME ZONE,
    decided_at TIMESTAMP(6) WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_dsr_tenant ON data_subject_requests (tenant_id, created_at);
