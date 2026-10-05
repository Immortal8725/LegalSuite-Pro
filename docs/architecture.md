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

In-app WebRTC is free. `VoiceService` records duration and a `callType` of `webrtc` or `pstn_outbound` / `pstn_inbound`. PSTN cost is stored on `CallRecord.totalCost` for the usage invoice. Recording is opt-in. `CallEthics` still decides the spoken-notice rule for the firm country.

Public-network dialing is a callback bridge, not a browser softphone:

1. The firm rents a local DID or verifies a physical landline (`FirmPhoneNumber`, one row per firm).
2. Staff choose a matter, or explicitly mark the call as not on a matter.
3. Emergency numbers are refused. They stay on the device dialer.
4. Twilio rings the attorney's own phone, then dials the destination with the firm number as caller ID.
5. Hangup updates the same `CallRecord`. A matter call still writes a time entry.

Twilio credentials are environment variables only: `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, and `TWILIO_PUBLIC_BASE_URL` (the public API address Twilio uses for the bridge and status callbacks). The Integrations toggle does not store a password. Webhooks require a valid `X-Twilio-Signature`.

Native CallKit (iOS) and ConnectionService (Android) are not in this slice. The Flutter client starts the same bridge. The cellular dialer is what rings. An in-app incoming-call UI would need a VoIP push entitlement and is a follow-up.

## AI

`AiService` never calls a vendor. `HeuristicAi` scores intake, drafts letters, and searches this tenant's cases, clients, and leads. Prompts are written to the audit log, not sent outbound.
