-- Fee plan per matter: a deposit (fees in advance, paid into trust under LPA s 86) and fees due when a litigation stage is reached.
CREATE TABLE IF NOT EXISTS matter_milestones (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    case_id UUID NOT NULL,
    client_id UUID,
    label VARCHAR(200) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    stage VARCHAR(80),
    amount NUMERIC(14,2) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    invoice_id UUID,
    pay_url VARCHAR(500),
    trust_transaction_id UUID,
    triggered_at TIMESTAMP WITH TIME ZONE,
    paid_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_matter_milestones_case ON matter_milestones (tenant_id, case_id);
CREATE INDEX IF NOT EXISTS idx_matter_milestones_invoice ON matter_milestones (invoice_id);
