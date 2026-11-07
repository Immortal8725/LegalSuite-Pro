-- Invite codes and the firms that signed up with one. No reward amounts are stored.
CREATE TABLE IF NOT EXISTS firm_referral_codes (
    tenant_id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS firm_referrals (
    id UUID PRIMARY KEY,
    referrer_tenant_id UUID NOT NULL,
    referred_tenant_id UUID NOT NULL UNIQUE,
    referred_firm_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE
);
