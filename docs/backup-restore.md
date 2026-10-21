# Backups and restore runbook

## What runs
- **Nightly backup** (`.github/workflows/nightly-backup.yml`): 17:40 SAST, Monday to Saturday. GitHub calls `POST /api/v1/ops/backup` on the API with the `X-Backup-Token` header. The API exports every table (except Flyway history) to gzip JSON lines and encrypts it with AES-256-GCM. GitHub keeps each file as a workflow artifact for 14 days.
- **Keep alive** (`keep-alive.yml`): pings the API and web every 10 minutes, 06:00 to 18:00 SAST, Monday to Saturday. Its run history is the uptime record shown on `/status`.
- **Status page**: `https://owlaw.co.za/status` shows API, database, last backup and the last keep-alive checks. API: `GET /api/v1/status` (no secrets).

Everything is free: GitHub Actions minutes on a public repo, artifacts, Render free tier.

## Secrets
| Where | Name | What |
| --- | --- | --- |
| Render owlaw-api env | `BACKUP_ENCRYPTION_KEY` | base64 of 32 random bytes. **Keep a copy offline (password manager).** Without it a backup cannot be opened. |
| Render owlaw-api env | `BACKUP_TOKEN` | 32+ random characters |
| GitHub repo secret | `BACKUP_TOKEN` | same value as on Render |

GitHub never holds the encryption key, so a leaked artifact is unreadable.

## Restore (about 20 minutes)
1. Download the newest artifact: GitHub, Actions, "Nightly backup", latest green run, Artifacts. Unzip to get `owlaw-YYYY-MM-DD.owlb`.
2. Create the target database (any free Postgres, such as a new Render free database, Neon or Supabase).
3. Point the API at it (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`) and let it start once, so Flyway creates the schema. With `LEGALSUITE_DEMO_DATA=false` no demo rows are created.
4. Stop the API (suspend the service), then on any machine:
   ```bash
   pip install cryptography
   BACKUP_ENCRYPTION_KEY='<from password manager>' python3 scripts/backup-restore.py owlaw-YYYY-MM-DD.owlb > restore.sql
   psql "postgresql://USER:PASS@HOST:5432/DB?sslmode=require" -v ON_ERROR_STOP=1 -f restore.sql
   ```
   The script truncates each table and reloads it in one transaction, with foreign key triggers off during the load.
5. Resume the API, open `/status` and sign in with a known account to check.
6. Uploaded files (documents, logos) live on the API's disk or bucket, not in the database. On the free tier they are not in this backup.

## Test a backup
Run the workflow by hand (Actions, Nightly backup, Run workflow), download the file and do step 4 against a scratch local Postgres.
