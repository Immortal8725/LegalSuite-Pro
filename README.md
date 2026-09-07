# LegalSuite Pro

Multi-tenant practice platform for law firms. A firm registers once, receives a public website and intake form, then turns modules on as the docket grows. In-app WebRTC voice is free. PSTN minutes and add-on modules invoice at month end.

This repository is a **modular monolith**: Next.js (App Router) in `frontend/` and Spring Boot 3.4 in `backend/`. There is no Eureka mesh and no required OpenAI or Stripe keys.

## Demo (seeded on boot)

| Role | Firm slug | Email | Password |
| --- | --- | --- | --- |
| Managing partner | `smith-associates` | `john@smithlaw.com` | `password` |
| Partner | `smith-associates` | `maria@smithlaw.com` | `password` |
| Associate | `smith-associates` | `alex@smithlaw.com` | `password` |
| Client portal | `smith-associates` | `sarah@example.com` | `portal123` |

Public site: `/firm/smith-associates`

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
4. **Communication** — Internal messages, WebRTC voice (in-app free; PSTN recorded for invoicing), call registry, recording opt-in.
5. **Advanced** — Conflicts, reports, module toggles, team, settings, global search.
6. **Mobile** — Responsive web + PWA; Flutter client in `mobile/` (`flutter run` after `flutter create .`).
7. **AI & integrations** — Local heuristic assistant (summarize, draft, intake screen, chat over the docket), document merge templates, built-in e-sign, connect/disconnect hub, audit log. No vendor keys.
8. **Launch polish** — This README, star ERD and class diagrams in `docs/`, tests, PWA manifest.
9. **The unique loop** — Hire pipeline (conflict → matter → engagement e-sign → IOLTA retainer), SOL/filing as the home screen, month-end usage invoice (modules + PSTN), call ethics with opt-in recording parked on the matter. Client text does not leave the tenant.

## Architecture

Firms (tenants) are the security boundary. Every row carries `tenant_id`. JWT claims set `TenantContext` for the request. Modules are catalog rows; core modules cannot be switched off. Add-on monthly prices roll into the firm's usage invoice.

See [docs/architecture.md](docs/architecture.md), [docs/erd.md](docs/erd.md), and [docs/class-diagrams.md](docs/class-diagrams.md).

## Stack

- Frontend: Next.js 15, React 19, Tailwind, shadcn-style primitives
- Backend: Spring Boot 3.4, Java 21, JPA, H2 (local), JWT (jjwt)
- Voice: WebSocket `/ws/signal` plus HTTP inbox fallback
- Mobile: Flutter (Dart) against `/api/v1`

Production would swap H2 for PostgreSQL and put the API behind TLS. The UI can publish to Vercel; the Java API needs a JVM host.
