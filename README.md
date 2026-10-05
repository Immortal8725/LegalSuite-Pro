# LegalSuite Pro

Multi-tenant practice platform for law firms. A firm registers once, receives a public website and intake form, then turns modules on as the docket grows. In-app WebRTC voice is free. PSTN minutes and add-on modules invoice at month end.

This repository is a **modular monolith**: Next.js (App Router) in `frontend/` and Spring Boot 3.4 in `backend/`. There is no Eureka mesh and no required OpenAI or Stripe keys.

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

The Next.js dev server rewrites `/api/*` to the Spring Boot process.

## What shipped (all eight phases)

1. **Foundation** — JWT auth, tenant isolation, firm registration, onboarding, app shell.
2. **Landing + practice** — Auto-generated public site, intake, cases, clients, contacts, documents, calendar, tasks.
3. **Financial** — Timers, invoices, IOLTA trust (no overdraw), expenses. Usage add-ons stay on the month-end invoice.
4. **Communication** — Internal messages, WebRTC voice (in-app free), PSTN callback bridge with a rented local number or a verified landline as caller ID, call registry, recording opt-in. Emergency numbers stay on the device dialer.
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
- Voice: WebSocket `/ws/signal` for in-app WebRTC. Public-network calls use a Twilio callback bridge (`TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_PUBLIC_BASE_URL`). Do not commit those values.
- Mobile: Flutter (Dart) against `/api/v1`

Production would swap H2 for PostgreSQL (`SPRING_PROFILES_ACTIVE=postgres` plus `docker compose up postgres`) and put the API behind TLS. TOTP 2FA is in Settings; demo users stay without it so `password` still works. The UI can publish to Vercel; the Java API needs a JVM host.

## Inspection pack (in product)

- **FFC** — LPA s 84 number and expiry on the firm record. ZA trust movements refuse without a current certificate.
- **Bank CSV** — Trust page imports FNB / Standard / ABSA CSV into the three-way bank leg.
- **POPIA / PAIA** — Information officer, generated s 51 manual, operator acknowledgement.
- **TOTP** — RFC 6238 enrol/confirm/disable. Login returns `{ requiresTotp: true }` (still HTTP 200) until the code is supplied.
- **ECT Act s 13** — Public sign requires an identity number; the hash includes it. Not a SANAS-accredited CSP.
- **RAF 1 pack** — Compile from C-2001 (or any RAF-track matter). Not CaseLines e-lodgement.

Still demo, not production: H2 create-drop, unbalanced Ndlovu recon (bank short R11,750), overdue Act 40 on C-2002.
