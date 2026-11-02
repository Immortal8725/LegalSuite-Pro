-- South African litigation tracks and stages, separate from the matter status.
ALTER TABLE cases ADD COLUMN IF NOT EXISTS litigation_track VARCHAR(255);
ALTER TABLE cases ADD COLUMN IF NOT EXISTS court_level VARCHAR(255);
ALTER TABLE cases ADD COLUMN IF NOT EXISTS litigation_stage VARCHAR(255);

CREATE TABLE IF NOT EXISTS matter_stage_events (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    case_id UUID,
    track VARCHAR(255),
    stage VARCHAR(255),
    entered_on DATE,
    note VARCHAR(255),
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_matter_stage_events_case ON matter_stage_events (tenant_id, case_id);
CREATE INDEX IF NOT EXISTS idx_cases_litigation_track ON cases (tenant_id, litigation_track);
