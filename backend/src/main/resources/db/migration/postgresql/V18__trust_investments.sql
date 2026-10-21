-- Trust money invested under section 86(3) (surplus, interest to the Fidelity Fund) or 86(4) (on a client's instruction, interest to the client).
CREATE TABLE IF NOT EXISTS trust_investments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    trust_account_id UUID,
    client_id UUID,
    case_id UUID,
    section VARCHAR(10) NOT NULL,
    institution VARCHAR(200),
    account_number VARCHAR(80),
    principal NUMERIC(38,2) NOT NULL,
    interest_earned NUMERIC(38,2) NOT NULL,
    interest_paid_out NUMERIC(38,2) NOT NULL,
    opened_on DATE NOT NULL,
    closed_on DATE,
    notes VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_trust_inv_tenant ON trust_investments (tenant_id);
