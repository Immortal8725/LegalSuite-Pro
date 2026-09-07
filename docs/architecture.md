# Architecture

LegalSuite Pro is a **modular monolith**. One Spring Boot process owns every bounded context (auth, practice, finance, voice, comms, AI). One Next.js app is the attorney desk, public landing pages, client portal, and e-sign surface.

```
Browser / PWA / Flutter
        │  /api/v1  +  /ws/signal
        ▼
Spring Boot (tenant filter + JWT)
        │
        ▼
JPA  →  H2 (dev) / PostgreSQL (prod)
```

## Tenancy

`JwtAuthFilter` parses the bearer token, writes `tenantId`, `userId`, `role`, and `email` into `TenantContext`, then clears them in `finally`. Repositories always query `findBy…AndTenantId`. Public routes (`/landing/{slug}`, `POST /intake/{slug}`, `/sign/{id}`, login/register) skip the filter's auth requirement but still never leak another firm's rows.

## Modules

`modules` is a catalog. `tenant_modules` stores enabled flags. Core slugs (`cases`, `clients`, `calendar`, …) cannot be disabled. Add-on prices (`trust`, `esignatures`, `ai`, …) are summed on the Modules screen for the month-end invoice. The demo Smith & Associates tenant has every module on.

## Voice

In-app WebRTC is free. `VoiceService` records duration and a `callType` of `webrtc` or `pstn`. PSTN cost is stored on `CallRecord.totalCost` for the usage invoice. Recording is opt-in on hangup.

## AI

`AiService` never calls a vendor. `HeuristicAi` scores intake, drafts letters, and searches this tenant's cases, clients, and leads. Prompts are written to the audit log, not sent outbound.
