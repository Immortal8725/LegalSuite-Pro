-- Firm's own photographs on the public site: a draft that the firm edits and a live copy that changes
-- only when Owlaw approves the branding, the same as name, tagline and theme.
CREATE TABLE IF NOT EXISTS public_site_photos (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    slot VARCHAR(64) NOT NULL,
    draft_ref VARCHAR(1000),
    draft_type VARCHAR(64),
    draft_version BIGINT,
    live_ref VARCHAR(1000),
    live_type VARCHAR(64),
    live_version BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_public_site_photos UNIQUE (tenant_id, slot)
);
