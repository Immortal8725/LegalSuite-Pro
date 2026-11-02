-- Web Push subscriptions: one row per browser or phone that opted in. Staff rows carry user_id, portal rows carry client_id.
CREATE TABLE IF NOT EXISTS push_subscriptions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID,
    client_id UUID,
    endpoint VARCHAR(1000) NOT NULL,
    p256dh VARCHAR(200) NOT NULL,
    auth VARCHAR(100) NOT NULL,
    user_agent VARCHAR(300),
    created_at TIMESTAMP WITH TIME ZONE,
    last_success_at TIMESTAMP WITH TIME ZONE,
    failures INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_push_subscriptions_endpoint ON push_subscriptions (endpoint);
CREATE INDEX IF NOT EXISTS idx_push_subscriptions_user ON push_subscriptions (tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_push_subscriptions_client ON push_subscriptions (tenant_id, client_id);
