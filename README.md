# LegalSuite Pro

Practice system for a solo South African attorney. Light is **R1,199 per month for one attorney** and includes section 86 trust. In-app calls are included. Public-network minutes are pay-what-you-use. There is no minute bundle and no unlimited voice. A local number is optional at about **R79 per month**, or bundled when the operator includes it.

This repository is a **modular monolith**: Next.js (App Router) in `frontend/` and Spring Boot 3.4 in `backend/`. There is no Eureka mesh and no required OpenAI or Stripe keys. The staff assistant stays on the tenant unless `LEGALSUITE_AI_PROVIDER` is set; see [docs/architecture.md](docs/architecture.md).

## Demo (seeded on boot)

South Africa is the default tenant. Texas remains on a second firm so both docket engines can be compared.

| Role | Firm slug | Email | Password |
| --- | --- | --- | --- |
| Director | `ndlovu-partners` | `thabo@ndlovulaw.co.za` | `password` |
| Director | `ndlovu-partners` | `lindiwe@ndlovulaw.co.za` | `password` |
| Associate | `ndlovu-partners` | `sipho@ndlovulaw.co.za` | `password` |
| Client portal | `ndlovu-partners` | `nomsa@example.com` | `portal123` |
| Managing partner (Texas) | `smith-associates` | `john@smithlaw.com` | `password` |
| Client portal (Texas) | `smith-associates` | `sarah@example.com` | `portal123` |

Public sites: `/firm/ndlovu-partners` and `/firm/smith-associates`

On the Sandton tenant, open **Practice fitness** and **Trust**. The section 86 three-way is deliberately unbalanced (bank short R11,750). C-2002 has an overdue Act 40 s 3 notice; trial status is blocked until you lodge, serve, or apply for condonation.

## Run locally

Requires **Java 21** and **Node 20+**.

```bash
chmod +x scripts/dev.sh
./scripts/dev.sh
```

Or two terminals:

```bash
# API at http://127.0.0.1:18081
cd backend && ./mvnw -DskipTests spring-boot:run

# Web. Pick an unused port.
cd frontend && npm install && npm run dev -- -p 43123 -H 0.0.0.0
```

H2 console: `http://127.0.0.1:18081/h2-console` (JDBC URL `jdbc:h2:mem:legalsuite`).

Health: `GET http://127.0.0.1:18081/api/v1/health` returns `{ success, data: { status, database, databaseUp } }`. It does not include secrets or the JDBC URL.

The Next.js dev server rewrites `/api/*` to the Spring Boot process.

## Hosted deploy

The demo boots on in-memory H2 and drops data when the process stops. A pilot host uses Postgres. TLS, the domain name, Stripe, and Twilio are still things a person does.

1. Copy the template and edit it on the host. Do not commit the result.

```bash
cp .env.example .env
```

Set `LEGALSUITE_JWT_SECRET` to a unique string of at least 32 characters, and set `DB_PASSWORD` to something other than the laptop default. Leave Twilio and Stripe blank until those accounts exist. Leave the per-minute rates blank until the carrier price is known. A blank rate records duration at zero cost. It is not a free minute bundle.

2. Start Postgres and wait until it is healthy.

```bash
docker compose up -d postgres
docker compose ps
```

3. Run the API on the Postgres profile. The profile refuses to start if the JWT secret is missing or still the development value from `application.yml`.

```bash
set -a && source .env && set +a
cd backend && ./mvnw -DskipTests spring-boot:run
```

`DATABASE_URL` defaults to `jdbc:postgresql://127.0.0.1:5432/legalsuite` when `SPRING_PROFILES_ACTIVE=postgres`.

4. Run the web app with the API origin the browser should use.

```bash
cd frontend && npm install && NEXT_PUBLIC_API_URL=http://127.0.0.1:18081 npm run build && npm start
```

5. Check the process.

```bash
curl -fsS http://127.0.0.1:18081/api/v1/health
```

`data.database` must be `postgres` and `data.databaseUp` must be true. Put TLS in front of both ports before any client uses the host. Do not expose the H2 console. The Postgres profile turns the H2 console off.

### All-in Docker (optional)

`docker compose --profile hosted up --build` builds the API and the web image and starts them with Postgres. It requires `LEGALSUITE_JWT_SECRET` in `.env`. The web container rewrites `/api` to `http://api:18081`. Publish that through your TLS proxy on port 3000. This path is for a single host. It is not a multi-region deploy.

## What shipped (all eight phases)

1. **Foundation.** JWT auth, tenant isolation, firm registration, onboarding, and the app shell.
2. **Landing and practice.** Public site, intake, cases, clients, contacts, documents, calendar, and tasks.
3. **Financial.** Timers, invoices, section 86 / IOLTA trust (no overdraw), and expenses. Trust is in the Light seat. Public-network minutes are the usage bill.
4. **Communication.** Internal messages, WebRTC voice (in-app free), a PSTN callback bridge (verified personal number or `TWILIO_VOICE_FROM`; renting a local number is optional), call registry, and recording opt-in. PSTN minutes are recorded for invoicing. Emergency numbers stay on the device dialer.
5. **Advanced.** Conflicts, reports, module toggles, team, settings, and global search.
6. **Mobile.** Responsive web and a PWA. Flutter client in `mobile/` (`flutter run` after `flutter create .`).
7. **Assistant and integrations.** Staff draft help on the matter workspace and on `/ai` (summarize, draft, intake screen, and questions about the file). Answers are drafts. The attorney remains responsible. The default stays on the tenant. `LEGALSUITE_AI_PROVIDER=openai|anthropic` plus `OPENAI_API_KEY` or `ANTHROPIC_API_KEY` opts into a model vendor for a single matter. A missing key falls back on the tenant. Also includes document merge templates, built-in e-sign, the connect/disconnect hub, and the audit log.
8. **Launch polish.** This README, ERD and class diagrams in `docs/`, tests, and the PWA manifest.
9. **Hire pipeline.** Party-aware conflict, then a **signed waiver instrument** if needed, then a **limited file**, then a mandate e-sign that unlocks appearance and posts the pledged retainer.
10. **South Africa.** Prescription Act, RAF Act s 23, Act 40 of 2002, and LRA s 191 clocks. RICA and LPC recording ethics. LPA s 86 three-way recon. Per-client ledgers. VAT 15% on fee invoices. Clock-generated tasks and docket hold.
11. **Texas.** Smith & Associates still runs CPRC chapters 16, 74, and 101.
12. **Inspection pack.** FFC gate, bank CSV, POPIA/PAIA, TOTP, identity-bound e-sign, and the RAF 1 pack.

## Architecture

Firms (tenants) are the security boundary. Every row carries `tenant_id`. JWT claims set `TenantContext` for the request. Modules are catalog rows; core modules cannot be switched off. Add-on monthly prices roll into the firm's usage invoice.

See [docs/architecture.md](docs/architecture.md), [docs/erd.md](docs/erd.md), and [docs/class-diagrams.md](docs/class-diagrams.md).

## Stack

- Frontend: Next.js 15, React 19, Tailwind, shadcn-style primitives
- Backend: Spring Boot 3.4, Java 21, JPA, H2 (local), JWT (jjwt)
- Voice: WebSocket `/ws/signal` for in-app WebRTC. Public-network calls use a Twilio callback bridge. See [Public network calls](#public-network-calls). Do not commit Twilio secrets.
- Mobile: Flutter (Dart) against `/api/v1`

The hosted path is Postgres (`SPRING_PROFILES_ACTIVE=postgres` plus `docker compose up postgres`) behind TLS that you terminate. TOTP is in Settings. Demo users stay without it so `password` still works. Do not treat that as forced 2FA.

## Public network calls

Outbound calls stay a callback bridge. The attorney's phone rings first. Twilio then dials the other party and presents a caller ID. This is not a browser softphone.

Set these on the API process. Names and an empty template are in `backend/.env.example`. Do not commit real values. Spring does not load that file on its own.

| Variable | Required | Role |
| --- | --- | --- |
| `TWILIO_ACCOUNT_SID` | yes | Twilio account |
| `TWILIO_AUTH_TOKEN` | yes | API auth. Never stored in the app |
| `TWILIO_PUBLIC_BASE_URL` | yes | Public https origin Twilio uses for the bridge and status webhooks |
| `TWILIO_VOICE_FROM` | no | E.164 number this account already owns or has verified, used when the firm has no saved caller ID |
| `LEGALSUITE_PSTN_OUTBOUND_PER_MIN` | no | Carrier rate per outbound minute. Blank records duration at zero cost. It is not a free bundle. |
| `LEGALSUITE_PSTN_INBOUND_PER_MIN` | no | Carrier rate per inbound minute. Same blank-rate rule. |

No IncomingPhoneNumber purchase is required. No South Africa End-User or regulatory bundle is required for this path.

When Place call leaves caller ID on Automatic, resolution order is:

1. The firm's active rented Twilio DID, if one is already saved in the app.
2. Else the firm's verified personal number saved in the app (mobile or landline). The stored kind stays `verified_landline`.
3. Else `TWILIO_VOICE_FROM`, if set.
4. Else the first IncomingPhoneNumber already on the Twilio account (covers a leftover trial number).
5. Else the first verified Outgoing Caller ID on the Twilio account.
6. If none of those exist, the API says to verify a personal number in the app or set `TWILIO_VOICE_FROM`.

An upgraded Twilio account no longer has the free trial From number. Verify a personal mobile or landline in Voice Calls, or set `TWILIO_VOICE_FROM`. A trial account, if one is still in use, can only call destinations Twilio has already verified. An upgraded account can call any number the account's geo permissions allow.

Also connect the Twilio toggle on Integrations. That toggle does not store a password. Emergency numbers stay on the device dialer. Recording stays opt-in. Matter calls still write a time entry and PSTN minutes still roll into the usage invoice.

## Inspection pack (in product)

- **FFC.** LPA s 84 number and expiry on the firm record. ZA trust movements refuse without a current certificate.
- **Bank CSV.** The trust page imports FNB, Standard, or ABSA CSV into the three-way bank leg.
- **POPIA / PAIA.** Information officer, generated s 51 manual, and operator acknowledgement.
- **TOTP.** RFC 6238 enrol, confirm, and disable. Login returns `{ requiresTotp: true }` (still HTTP 200) until the code is supplied.
- **ECT Act s 13.** Public sign requires an identity number, and the hash includes it. Not a SANAS-accredited CSP.
- **RAF 1 pack.** Compile from C-2001, or any RAF-track matter. Not CaseLines e-lodgement.

Still demo, not production: H2 create-drop, unbalanced Ndlovu recon (bank short R11,750), overdue Act 40 on C-2002.
