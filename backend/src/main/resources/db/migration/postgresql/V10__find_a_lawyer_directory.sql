-- Find a Lawyer directory: listing profile, practitioner LPC verification, POPIA enquiry records, daily counts.
CREATE TABLE IF NOT EXISTS directory_profiles (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    practice_areas_json VARCHAR(2000),
    provinces_json VARCHAR(2000),
    cities_json VARCHAR(2000),
    languages_json VARCHAR(2000),
    consult_options_json VARCHAR(2000),
    fixed_consult_fee NUMERIC(10, 2),
    response_time VARCHAR(32),
    bio VARCHAR(600),
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_directory_profiles_tenant UNIQUE (tenant_id)
);

CREATE TABLE IF NOT EXISTS practitioner_registrations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    lpc_number VARCHAR(64) NOT NULL,
    fidelity_expires_on DATE,
    status VARCHAR(32) NOT NULL,
    verification_note VARCHAR(500),
    verified_at TIMESTAMP(6) WITH TIME ZONE,
    verified_by UUID,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_practitioner_reg_tenant ON practitioner_registrations (tenant_id);
CREATE INDEX IF NOT EXISTS idx_practitioner_reg_status ON practitioner_registrations (status);

CREATE TABLE IF NOT EXISTS directory_enquiries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    lead_id UUID NOT NULL,
    conflict_check_id UUID,
    conflict_status VARCHAR(32),
    consent_at TIMESTAMP(6) WITH TIME ZONE,
    consent_version VARCHAR(32),
    created_at TIMESTAMP(6) WITH TIME ZONE,
    purge_after TIMESTAMP(6) WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_directory_enquiries_purge ON directory_enquiries (purge_after);
CREATE INDEX IF NOT EXISTS idx_directory_enquiries_lead ON directory_enquiries (lead_id);

CREATE TABLE IF NOT EXISTS directory_daily_stats (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    stat_day DATE NOT NULL,
    profile_views INTEGER NOT NULL,
    search_appearances INTEGER NOT NULL,
    enquiries INTEGER NOT NULL,
    CONSTRAINT uk_directory_daily_stat UNIQUE (tenant_id, stat_day)
);
