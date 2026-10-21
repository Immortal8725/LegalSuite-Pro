-- Firm slugs become subdomains (<slug>.owlaw.co.za), so each must be a valid DNS label:
-- lowercase a-z, 0-9 and hyphens, no leading or trailing hyphen, at most 63 characters, and not a reserved name.
-- Only slugs that break those rules are changed; the platform account is left alone. Old values are kept
-- in tenant_slug_history so they can be traced. A clash gets the first 8 characters of the firm id appended.
CREATE TABLE IF NOT EXISTS tenant_slug_history (
    tenant_id UUID NOT NULL,
    old_slug VARCHAR(255) NOT NULL,
    new_slug VARCHAR(255) NOT NULL,
    changed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);

DROP TABLE IF EXISTS slug_fix;
CREATE TEMP TABLE slug_fix AS
SELECT id, slug AS old_slug,
       trim(both '-' from left(trim(both '-' from regexp_replace(regexp_replace(lower(slug), '[^a-z0-9-]+', '-', 'g'), '-+', '-', 'g')), 63)) AS new_slug
FROM tenants
WHERE slug <> 'legalsuite'
  AND (slug !~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$'
       OR slug IN ('www', 'api', 'mail', 'smtp', 'pop', 'imap', 'ftp', 'app', 'admin', 'platform', 'owlaw', 'support', 'status'));

UPDATE slug_fix SET new_slug = 'firm' WHERE new_slug = '';
UPDATE slug_fix SET new_slug = left(new_slug, 58) || '-law'
WHERE new_slug IN ('www', 'api', 'mail', 'smtp', 'pop', 'imap', 'ftp', 'app', 'admin', 'platform', 'owlaw', 'support', 'status');
UPDATE slug_fix f SET new_slug = trim(both '-' from left(f.new_slug, 54)) || '-' || left(f.id::text, 8)
WHERE EXISTS (SELECT 1 FROM tenants t WHERE t.slug = f.new_slug AND t.id <> f.id)
   OR (SELECT count(*) FROM slug_fix g WHERE g.new_slug = f.new_slug) > 1;

INSERT INTO tenant_slug_history (tenant_id, old_slug, new_slug)
SELECT id, old_slug, new_slug FROM slug_fix WHERE old_slug <> new_slug;

UPDATE tenants t SET slug = f.new_slug, updated_at = now()
FROM slug_fix f WHERE t.id = f.id AND f.old_slug <> f.new_slug;
DROP TABLE slug_fix;
