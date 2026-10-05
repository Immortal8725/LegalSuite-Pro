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

`modules` is a catalog. `tenant_modules` stores enabled flags. Core slugs cannot be disabled. Section 86 trust is core on the Light seat and is not a month-end add-on. The Light subscription itself is a seat line on the usage preview. Public-network minutes are the usage. The demo tenants have the catalog enabled so the sample files can be opened.

## Voice

In-app calls are included in the Light seat. `VoiceService` records duration and a `callType` of `webrtc` or `pstn`. Public-network cost uses the per-minute rate from the environment. If that rate is blank, cost stays zero and the minutes are still recorded. There is no included minute bundle. Recording is opt-in on hangup.

## AI

`AiService` never calls a vendor. `HeuristicAi` scores intake, drafts letters, and searches this tenant's cases, clients, and leads. Prompts are written to the audit log, not sent outbound.
