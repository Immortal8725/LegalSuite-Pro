-- Client invoice payment links (PayFast) and each firm's own PayFast merchant settings.
CREATE TABLE IF NOT EXISTS firm_payment_settings (
    tenant_id UUID PRIMARY KEY,
    enabled BOOLEAN NOT NULL,
    mode VARCHAR(10) NOT NULL,
    merchant_id VARCHAR(40),
    merchant_key_enc VARCHAR(500),
    passphrase_enc VARCHAR(500),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS invoice_payment_links (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    amount NUMERIC(38,2) NOT NULL,
    mode VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    pf_payment_id VARCHAR(80),
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_pay_links_invoice ON invoice_payment_links (tenant_id, invoice_id);
