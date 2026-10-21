-- One-off cleanup, reviewed: soft delete the two test firms registered on 21 Oct 2026 while debugging the
-- post-registration landing page (PR #39). Scoped to the exact slug AND name AND creation date, so it can
-- never match any other firm. On a database without these firms (tests, fresh installs) it changes nothing.
-- The live database has no external access, so a pg_dump was not possible. Instead the exact rows this
-- migration changes are copied first into backup tables (ops_backup_v14_*), so the change can be undone in SQL.
-- Soft delete only: no firm data is removed. Purge, if wanted, is done later from the Firms screen after 30 days.

CREATE TABLE IF NOT EXISTS ops_backup_v14_tenants AS
SELECT * FROM tenants
WHERE created_at >= TIMESTAMP WITH TIME ZONE '2026-10-21 00:00:00+02'
  AND created_at <  TIMESTAMP WITH TIME ZONE '2026-10-22 00:00:00+02'
  AND (slug, firm_name) IN (
    ('regtest-attorneys-1792599084545', 'Regtest Attorneys 1792599084545'),
    ('regtest-attorneys-1792600722720', 'Regtest Attorneys 1792600722720')
  );

CREATE TABLE IF NOT EXISTS ops_backup_v14_public_sites AS
SELECT * FROM tenant_public_sites WHERE tenant_id IN (SELECT id FROM ops_backup_v14_tenants);

UPDATE tenants
SET status_before_suspend = status,
    status = 'deleted',
    deleted_at = now(),
    deleted_reason = 'Test firm from registration debugging on 21 Oct 2026, removed by migration V14',
    updated_at = now()
WHERE status IN ('trial', 'active')
  AND created_at >= TIMESTAMP WITH TIME ZONE '2026-10-21 00:00:00+02'
  AND created_at <  TIMESTAMP WITH TIME ZONE '2026-10-22 00:00:00+02'
  AND (slug, firm_name) IN (
    ('regtest-attorneys-1792599084545', 'Regtest Attorneys 1792599084545'),
    ('regtest-attorneys-1792600722720', 'Regtest Attorneys 1792600722720')
  );

-- End their sessions.
DELETE FROM refresh_tokens
WHERE user_id IN (
  SELECT u.id FROM users u JOIN tenants t ON t.id = u.tenant_id
  WHERE t.status = 'deleted'
    AND t.deleted_reason = 'Test firm from registration debugging on 21 Oct 2026, removed by migration V14'
);

-- Take them out of the approval queue.
UPDATE tenant_public_sites SET publish_status = 'draft'
WHERE tenant_id IN (
  SELECT id FROM tenants WHERE status = 'deleted'
    AND deleted_reason = 'Test firm from registration debugging on 21 Oct 2026, removed by migration V14'
);
