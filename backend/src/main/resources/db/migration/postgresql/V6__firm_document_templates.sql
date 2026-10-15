-- Firm-branded document templates: letterhead branding on the firm, uploaded firm DOCX templates,
-- and document field values remembered per matter.
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS ref_prefix character varying(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS footer_text character varying(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS letterhead_colour character varying(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS trust_account_name character varying(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS trust_account_number character varying(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS bank_name character varying(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS branch_name character varying(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS branch_code character varying(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS logo_path character varying(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS logo_mime character varying(255);

ALTER TABLE document_templates ADD COLUMN IF NOT EXISTS source_format character varying(255);
ALTER TABLE document_templates ADD COLUMN IF NOT EXISTS storage_path character varying(255);
ALTER TABLE document_templates ADD COLUMN IF NOT EXISTS description character varying(1000);

CREATE TABLE IF NOT EXISTS matter_field_values (
    id uuid NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    case_id uuid NOT NULL,
    field_key character varying(80) NOT NULL,
    field_value character varying(2000),
    updated_at timestamp(6) with time zone,
    CONSTRAINT uk_matter_field_values_case_key UNIQUE (case_id, field_key)
);
CREATE INDEX IF NOT EXISTS idx_matter_field_values_tenant_case ON matter_field_values (tenant_id, case_id);
