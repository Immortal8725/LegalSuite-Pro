# LegalSuite Pro

Practice system for a solo South African attorney. Light is **R1,199 per month for one attorney** and includes section 86 trust. In-app calls are included. Public-network minutes are pay-what-you-use. There is no minute bundle and no unlimited voice. A local number is optional at about **R79 per month**, or bundled when the operator includes it.

This repository is a **modular monolith**: Next.js (App Router) in `frontend/` and Spring Boot 3.4 in `backend/`. There is no Eureka mesh. Stripe and Twilio keys are not in the repo. The default draft help does not call a model vendor.

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
# API — http://127.0.0.1:18081
cd backend && ./mvnw -DskipTests spring-boot:run

# Web — pick an unused port
cd frontend && npm install && npm run dev -- -p 43123 -H 0.0.0.0
```

H2 console: `http://127.0.0.1:18081/h2-console` (JDBC URL `jdbc:h2:mem:legalsuite`).

Health: `GET http://127.0.0.1:18081/api/v1/health` returns `{ success, data: { status, database, databaseUp } }`. It does not include secrets or the JDBC URL.

The Next.js dev server rewrites `/api/*` to the Spring Boot process.

## Hosted deploy

The demo boots on in-memory H2 and drops data when the process stops. A pilot host uses Postgres. TLS, the domain name, Stripe, and Twilio are still things a person does. See [Still needs a person](#still-needs-a-person).

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

## Still needs a person

- Twilio account and KYC before a real caller ID or public-network call.
- Stripe secret, publishable key, and webhook secret. Card numbers never go in this repo.
- A domain and TLS certificates.
- `LEGALSUITE_JWT_SECRET` and a real `DB_PASSWORD` on that host.
- The carrier's per-minute price in `LEGALSUITE_PSTN_OUTBOUND_PER_MIN` and `LEGALSUITE_PSTN_INBOUND_PER_MIN`.
- Merge earlier draft pull requests if you want them on main: matter workspace, draft help, copy scrub, and firm caller ID. Do not force-push main.
- Counsel review of the legal pack before it is the text on a production domain.

## What shipped (all eight phases)

1. **Foundation** — JWT auth, tenant isolation, firm registration, onboarding, app shell.
2. **Landing + practice** — Auto-generated public site, intake, cases, clients, contacts, documents, calendar, tasks.
3. **Financial** — Timers, invoices, section 86 / IOLTA trust (no overdraw), expenses. Trust is in the Light seat. Public-network minutes are the usage bill.
4. **Communication** — Internal messages, in-app voice (included), public-network minutes recorded for the usage invoice, call registry, recording opt-in.
5. **Advanced** — Conflicts, reports, module toggles, team, settings, global search.
6. **Mobile** — Responsive web + PWA; Flutter client in `mobile/` (`flutter run` after `flutter create .`).
7. **AI & integrations** — Local heuristic assistant (summarize, draft, intake screen, chat over the docket), document merge templates, built-in e-sign, connect/disconnect hub, audit log. No vendor keys.
8. **Launch polish** — This README, star ERD and class diagrams in `docs/`, tests, PWA manifest.
9. **The unique loop** — Hire pipeline (party-aware conflict → **signed waiver instrument** if needed → **limited file** → mandate e-sign unlocks appearance and posts the pledged retainer).
10. **South Africa** — Prescription Act / RAF Act s 23 / Act 40 of 2002 / LRA s 191 clocks; RICA + LPC recording ethics; LPA s 86 three-way recon; per-client ledgers; VAT 15% on fee invoices; clock-generated tasks and docket hold.
11. **Texas remains** — Smith & Associates still runs CPRC chapters 16, 74, 101.
12. **Inspection pack** — FFC gate, bank CSV, POPIA/PAIA, TOTP, identity-bound e-sign, RAF 1 pack.

## Architecture

Firms (tenants) are the security boundary. Every row carries `tenant_id`. JWT claims set `TenantContext` for the request. Modules are catalog rows; core modules cannot be switched off. Add-on monthly prices roll into the firm's usage invoice.

See [docs/architecture.md](docs/architecture.md), [docs/erd.md](docs/erd.md), and [docs/class-diagrams.md](docs/class-diagrams.md).

## Stack

- Frontend: Next.js 15, React 19, Tailwind, shadcn-style primitives
- Backend: Spring Boot 3.4, Java 21, JPA, H2 (local), JWT (jjwt)
- Voice: WebSocket `/ws/signal` plus HTTP inbox fallback
- Mobile: Flutter (Dart) against `/api/v1`

The hosted path is Postgres (`SPRING_PROFILES_ACTIVE=postgres` plus `docker compose up postgres`) behind TLS that you terminate. TOTP is in Settings. Demo users stay without it so `password` still works. Do not treat that as forced 2FA.

## Inspection pack (in product)

- **FFC** — LPA s 84 number and expiry on the firm record. ZA trust movements refuse without a current certificate.
- **Bank CSV** — Trust page imports FNB / Standard / ABSA CSV into the three-way bank leg.
- **POPIA / PAIA** — Information officer, generated s 51 manual, operator acknowledgement.
- **TOTP** — RFC 6238 enrol/confirm/disable. Login returns `{ requiresTotp: true }` (still HTTP 200) until the code is supplied.
- **ECT Act s 13** — Public sign requires an identity number; the hash includes it. Not a SANAS-accredited CSP.
- **RAF 1 pack** — Compile from C-2001 (or any RAF-track matter). Not CaseLines e-lodgement.

The Ndlovu demo is still a sample file: H2 unless you start Postgres, unbalanced recon (bank short R11,750), overdue Act 40 on C-2002. Those reds are intentional. Twilio, Stripe, the domain, and TLS are not.
