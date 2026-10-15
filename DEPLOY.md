# Owlaw deploy checklist

Work through this top to bottom for each environment (staging, then production). Tick each line. Nothing here needs a code change.

## 1. Before you start

- [ ] A domain for the app, for example `app.example.co.za`, and (optional) a root for firm public sites, for example `example.co.za` so a firm lives at `ndlovu-partners.example.co.za`.
- [ ] A host: one Linux VM with Docker (the compose file in this repo), or AWS (see [docs/aws-hosting-checklist.md](docs/aws-hosting-checklist.md)).
- [ ] A managed Postgres 15+ or the compose `postgres` service. Postgres 17 is what the test suite runs against.
- [ ] An SMTP account for invites and client email (for example Amazon SES, Mailgun, SendGrid, or the firm's Microsoft 365 / Google Workspace relay).
- [ ] PayFast merchant account (sandbox first; live only after KYC).
- [ ] Twilio account (only if the firm will use phone calls, SMS or WhatsApp).

## 2. Environment variables

Copy `.env.example` to `.env` on the host. Never commit `.env`. Every secret is read from the environment only; none is stored in the database or in git.

| Variable | Required | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | yes | `postgres`. The API refuses to start on this profile without the next four. |
| `DATABASE_URL` | yes | `jdbc:postgresql://host:5432/legalsuite`. Compose sets it for you. |
| `DB_USER`, `DB_PASSWORD` | yes | Strong, unique password. |
| `LEGALSUITE_JWT_SECRET` | yes | `openssl rand -hex 32`. Changing it signs everyone out. |
| `APP_URL` | yes | Public https address of the web app. Used in invite links. |
| `CORS_ALLOWED_ORIGINS` | yes | Exact origins, comma separated. Usually the same as `APP_URL`. `*` is refused. |
| `LEGALSUITE_OPERATOR_EMAIL`, `LEGALSUITE_OPERATOR_PASSWORD` | first start | Creates the platform operator on an empty database. Forced password change at first sign-in. Remove the password from `.env` afterwards. |
| `LEGALSUITE_DEMO_DATA` | no | Leave `false` in production. `true` creates demo firms whose password is `password`. |
| `FORWARD_HEADERS_STRATEGY` | behind a proxy | `native` when a proxy sets `X-Forwarded-For` (compose sets this). Otherwise `none`. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_STARTTLS` | for email | Without SMTP, invites fall back to a one-time password shown to the admin. |
| `PAYFAST_MERCHANT_ID`, `PAYFAST_MERCHANT_KEY`, `PAYFAST_PASSPHRASE`, `PAYFAST_ENV`, `PAYFAST_RETURN_URL`, `PAYFAST_CANCEL_URL`, `PAYFAST_NOTIFY_URL` | for product billing | See section 5. Keep `PAYFAST_SANDBOX_LOCAL=false`. |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_PUBLIC_BASE_URL`, `TWILIO_VOICE_FROM`, `TWILIO_SMS_FROM`, `TWILIO_WHATSAPP_FROM` | for phone | See section 6. |
| `TWILIO_API_KEY_SID`, `TWILIO_API_KEY_SECRET`, `TWILIO_TWIML_APP_SID`, `TWILIO_PUSH_CREDENTIAL_SID` | for in-app calls in the attorney app | API key pair signs the Voice token. TwiML App Voice URL is `/api/v1/voice/twilio/app-dial`. Push credential only for incoming calls. See mobile/README.md. |
| `LEGALSUITE_PSTN_OUTBOUND_PER_MIN`, `LEGALSUITE_PSTN_INBOUND_PER_MIN` | for phone billing | Rand per minute, default 1.49. Blank records duration at zero cost. |
| `LEGALSUITE_PSTN_LANDLINE_PER_MIN` | optional | Rand per minute when the firm's own landline is the caller ID, default 8.90. |
| `NEXT_PUBLIC_API_URL` | web build | Where the web server proxies `/api`. Compose uses `http://api:18081`. Baked in at build time. |
| `NEXT_PUBLIC_PUBLIC_SITE_ROOT` | optional | Root domain for firm public-site subdomains. |
| `LEGALSUITE_UPLOAD_DIR` | yes on a VM | Folder for uploaded documents and payment proofs. Compose mounts a volume at `/app/uploads`. |
| `LEGALSUITE_REFRESH_TOKEN_MS` | optional | Refresh token lifetime in milliseconds, default 604800000 (7 days). |
| `JPA_DDL_AUTO` | optional | Hibernate schema mode on the postgres profile, default `validate`. Flyway owns the schema; do not change this on a live system. |
| `OPENAI_MODEL`, `OPENAI_BASE_URL` | optional | Model and endpoint when `LEGALSUITE_AI_PROVIDER=openai`. Defaults `gpt-4o-mini` and `https://api.openai.com/v1` (any OpenAI-compatible endpoint works). |
| `ANTHROPIC_MODEL`, `ANTHROPIC_BASE_URL` | optional | Model and endpoint when `LEGALSUITE_AI_PROVIDER=anthropic`. Defaults `claude-3-5-haiku-latest` and `https://api.anthropic.com/v1`. |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PUBLISHABLE_KEY` | not used | Left from an earlier billing design. Product billing is PayFast. Leave blank. |
| `NEXT_OUTPUT` | web build | `standalone` in the Docker image (set by `frontend/Dockerfile`). Leave unset for `next start`. |
| `NODE_ENV` | web | Set by Next.js (`production` for `next build` and `next start`). Production adds HSTS and drops `unsafe-eval` from the CSP. Do not set it by hand. |
| `LEGALSUITE_CLAMAV_HOST`, `LEGALSUITE_CLAMAV_PORT`, `LEGALSUITE_CLAMAV_TIMEOUT_MS` | optional | ClamAV (clamd) virus scan of every document upload. Off when the host is blank; port 3310, timeout 15000 ms. When on, uploads are refused while clamd is unreachable. |
| `LEGALSUITE_HSTS_ALWAYS`, `LEGALSUITE_HSTS_MAX_AGE` | optional | Send HSTS from the API on every response (when the proxy does not forward `X-Forwarded-Proto`), and its max age in seconds (default 31536000). See section 4. |

## 3. Start it

```bash
cp .env.example .env            # then edit
docker compose --profile hosted up -d --build
docker compose ps               # api and web should become "healthy"
curl -fsS http://127.0.0.1:18081/api/v1/health
```

`data.database` must be `postgres` and `data.databaseUp` must be `true`. The endpoint returns HTTP 503 when the database is down, so point the load balancer health check at it.

Flyway creates the schema on an empty database and upgrades an older one (see [docs/migrations.md](docs/migrations.md)). Upgrading a database that an older build created with `ddl-auto`: take a backup first, then start the new build once; Flyway baselines it at V1 and applies V2 onwards.

## 4. Domain and TLS

- [ ] Put a TLS proxy in front of the web container (Caddy, nginx, or a cloud load balancer). Compose binds every port to `127.0.0.1` only.
- [ ] Point `app.example.co.za` at the proxy. For firm public sites, add a wildcard DNS record `*.example.co.za` and a wildcard certificate, and set `NEXT_PUBLIC_PUBLIC_SITE_ROOT=example.co.za`.
- [ ] The proxy must pass `X-Forwarded-For` and `X-Forwarded-Proto`, and must allow WebSocket upgrades on `/ws/` (in-app calls).
- [ ] In-app call signalling (`/ws/signal`) only accepts browsers whose origin is `APP_URL` or in `CORS_ALLOWED_ORIGINS`, and only with a signed-in staff token. If calls do not connect, check those two values first.
- [ ] Only the proxy is public. Postgres and the API port stay private.
- [ ] Set HSTS at the proxy as well, so every response (including proxy error pages) carries it. Caddy sends nothing by default; add it:

  ```
  app.example.co.za {
      header Strict-Transport-Security "max-age=31536000; includeSubDomains"
      reverse_proxy 127.0.0.1:3000
  }
  ```

  nginx: `add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;` inside the `server { listen 443 ssl; ... }` block, plus a port 80 server that only returns `301 https://$host$request_uri`. Add `preload` only once every subdomain is on HTTPS for good.
- [ ] The web app sends HSTS on production builds. The API sends it on HTTPS requests; with `FORWARD_HEADERS_STRATEGY=native` it sees the proxy's `X-Forwarded-Proto`. If your proxy cannot forward that header, set `LEGALSUITE_HSTS_ALWAYS=true` (`LEGALSUITE_HSTS_MAX_AGE` changes the age, default one year).
- [ ] Check the headers: `curl -sI https://app.example.co.za/login` shows `Content-Security-Policy`, `Strict-Transport-Security`, `X-Frame-Options: DENY` and `X-Content-Type-Options: nosniff`.

## 5. PayFast (product billing)

- [ ] Sandbox first: `PAYFAST_ENV=sandbox` with the sandbox merchant ID, key and passphrase from the PayFast sandbox dashboard.
- [ ] `PAYFAST_NOTIFY_URL=https://app.example.co.za/api/v1/product-billing/payfast/itn`. It must be reachable from the internet. PayFast posts the ITN there.
- [ ] `PAYFAST_RETURN_URL` and `PAYFAST_CANCEL_URL` point at `https://app.example.co.za/product-billing`.
- [ ] Run one seat checkout and one minutes charge in sandbox and confirm the firm's billing page updates. Details in [BILLING.md](BILLING.md).
- [ ] Live only after KYC: switch to `PAYFAST_ENV=live` and the live credentials. `PAYFAST_SANDBOX_LOCAL` is ignored in live mode, but leave it `false`.

## 6. Twilio (phone, SMS, WhatsApp)

- [ ] Upgrade the account out of trial (trial can only reach verified numbers).
- [ ] Set `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN` and `TWILIO_PUBLIC_BASE_URL` (the public https origin of the API, for example `https://app.example.co.za`).
- [ ] Verify a caller ID or set `TWILIO_VOICE_FROM`. For SMS set `TWILIO_SMS_FROM` or a messaging service. For WhatsApp, register a sender (the sandbox is for testing only).
- [ ] Connect Twilio under Integrations in the app, then place one test call and send one test SMS.
- [ ] Confirm `LEGALSUITE_PSTN_OUTBOUND_PER_MIN`, `LEGALSUITE_PSTN_INBOUND_PER_MIN` (1.49) and `LEGALSUITE_PSTN_LANDLINE_PER_MIN` (8.90).

## 7. Email

- [ ] Set the SMTP variables. Use port 587 with STARTTLS or 465 with TLS. `SMTP_FROM` must be an address the provider allows you to send as.
- [ ] Add SPF, DKIM and DMARC records for the sending domain so invites do not land in spam.
- [ ] Invite a test user and open the link. It works once and expires after 72 hours.

## 8. Backups and restore

- [ ] Daily Postgres backup kept off the host: `pg_dump -Fc legalsuite > legalsuite-$(date +%F).dump` (or the managed service's automated snapshots with point-in-time recovery).
- [ ] Back up the uploads volume or folder on the same schedule (documents and payment proofs live there, not in the database).
- [ ] Keep at least 30 days. Encrypt backups at rest.
- [ ] Test a restore into a scratch database before go-live, and every quarter after: `pg_restore -d legalsuite_restore legalsuite-YYYY-MM-DD.dump`.

## 9. POPIA notes

Owlaw holds personal information of clients, opposing parties and staff, some of it special personal information (health records in RAF matters, criminal records, identity numbers).

- [ ] Each firm records its Information Officer in Settings and registers them with the Information Regulator.
- [ ] Each firm generates and publishes its PAIA manual from Settings.
- [ ] The platform operator signs an operator agreement (POPIA s 21) with each firm. Firms acknowledge it in onboarding.
- [ ] Host in South Africa where possible (for example AWS `af-south-1`). If data leaves South Africa, confirm the s 72 cross-border conditions are met.
- [ ] Access is role based and every change is in the audit log. Review the audit log monthly.
- [ ] Data subject requests (POPIA s 23-25) are self-service. Clients use "Your personal information" in the portal, firm users the same card in Settings: download as JSON (portal also ZIP with their files) and ask for deletion. A firm admin approves or refuses (refusal needs a reason) under Settings, Privacy requests. Approval anonymises the person; matters, invoices, payments, trust ledger and the audit log are kept for the legal retention periods with the identifying details removed. Admins can also export a client (`GET /api/v1/privacy/clients/{id}/export?format=zip`) or log a request taken by phone (`POST /api/v1/privacy/clients/{id}/erasure`). Every export, request and decision is in the audit log. Access tokens already issued to an anonymised user expire within `LEGALSUITE_ACCESS_TOKEN_MS` (default 1 hour); refresh tokens are revoked at once.
- [ ] Security compromise: notify the Information Regulator and affected data subjects as soon as reasonably possible (s 22). Keep the incident steps with the backup steps.
- [ ] Retention: closed matters are kept for the period the firm's rules require, then deleted. Backups age out on the same schedule.

## 10. After go-live

- [ ] Sign in as the platform operator, change the password, remove `LEGALSUITE_OPERATOR_PASSWORD` from `.env`, and restart the API.
- [ ] Turn on two-factor sign-in for every owner and admin (Settings).
- [ ] Watch the API log for `Sign-in attempts throttled` and `WARN` lines from the startup guard.
- [ ] Run `npm audit --omit=dev` (web) and update the Spring Boot patch version each month.

## Known limits

- Login throttling is held in memory per API instance. On more than one instance, also rate limit `/api/v1/auth/login` and `/api/v1/portal/login` at the load balancer.
- Documents are stored on the API's disk (`LEGALSUITE_UPLOAD_DIR`), so run one API instance with a persistent volume, or a shared file system (EFS) if you scale out.
- The web app keeps its refresh token in browser storage. The Content-Security-Policy limits script sources to the app itself to reduce the risk.
