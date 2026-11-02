-- Change client on a matter and merge duplicate clients: identity number on the client, hidden history in the portal,
-- an audit row per change, and trust-to-trust transfers between client ledgers that need a second approver.
ALTER TABLE clients ADD COLUMN IF NOT EXISTS id_number VARCHAR(40);
ALTER TABLE matter_messages ADD COLUMN IF NOT EXISTS hidden_from_client BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS client_changes (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL,
    case_id UUID,
    from_client_id UUID NOT NULL,
    to_client_id UUID NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    actor_id UUID,
    show_history BOOLEAN NOT NULL DEFAULT FALSE,
    popia_flag BOOLEAN NOT NULL DEFAULT FALSE,
    popia_detail VARCHAR(1000),
    counts_json VARCHAR(4000),
    created_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_client_changes_tenant ON client_changes (tenant_id, created_at);

CREATE TABLE IF NOT EXISTS trust_client_transfers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    change_id UUID,
    trust_account_id UUID NOT NULL,
    case_id UUID,
    from_client_id UUID NOT NULL,
    to_client_id UUID NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(1000),
    requested_by UUID,
    decided_by UUID,
    out_transaction_id UUID,
    in_transaction_id UUID,
    created_at TIMESTAMP WITH TIME ZONE,
    decided_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_trust_client_transfers_tenant ON trust_client_transfers (tenant_id, status);
