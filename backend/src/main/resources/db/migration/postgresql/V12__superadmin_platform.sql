-- Platform superadmin: firm suspension, platform action log, 2FA recovery codes, consent-based support access.
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS suspended_reason VARCHAR(500);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS status_before_suspend VARCHAR(32);

CREATE TABLE IF NOT EXISTS platform_actions (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    actor_id UUID,
    actor_email VARCHAR(255),
    action VARCHAR(128) NOT NULL,
    entity_type VARCHAR(64),
    entity_id VARCHAR(64),
    detail VARCHAR(1000),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_platform_actions_tenant ON platform_actions (tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_platform_actions_created ON platform_actions (created_at);

CREATE TABLE IF NOT EXISTS user_recovery_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    used_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_user_recovery_codes_user ON user_recovery_codes (user_id);

CREATE TABLE IF NOT EXISTS support_grants (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    granted_by UUID NOT NULL,
    granted_by_email VARCHAR(255),
    reason VARCHAR(500) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP(6) WITH TIME ZONE,
    revoked_by_email VARCHAR(255),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_support_grants_tenant ON support_grants (tenant_id);
