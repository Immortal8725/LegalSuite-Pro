# Architecture

LegalSuite Pro is a **modular monolith**. One Spring Boot process owns every bounded context (auth, practice, finance, voice, comms, and draft help). One Next.js app is the attorney desk, public landing pages, client portal, and e-sign surface.

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

1. Caller ID is resolved without requiring a purchase. Order: the firm's active rented DID, else a verified personal number saved in the app (mobile or landline), else `TWILIO_VOICE_FROM`, else the first IncomingPhoneNumber already on the Twilio account, else the first verified Outgoing Caller ID on that account. If none exist, the API tells staff to verify a personal number or set `TWILIO_VOICE_FROM`.
2. Staff choose a matter, or explicitly mark the call as not on a matter.
3. Emergency numbers are refused. They stay on the device dialer.
4. Twilio rings the attorney's own phone, then dials the destination with that caller ID.
5. Hangup updates the same `CallRecord`. A matter call still writes a time entry.

Twilio credentials are environment variables only: `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_PUBLIC_BASE_URL` (the public API address Twilio uses for the bridge and status callbacks), and optional `TWILIO_VOICE_FROM`. The Integrations toggle does not store a password. Webhooks require a valid `X-Twilio-Signature`. No South Africa End-User bundle is part of this path. After an account leaves trial, the free trial From number is gone.

Native CallKit (iOS) and ConnectionService (Android) are not in this slice. The Flutter client starts the same bridge. The cellular dialer is what rings. An in-app incoming-call UI would need a VoIP push entitlement and is a follow-up.

## Staff assistant

Staff ask from the matter workspace (`/cases/{id}`) or from the existing `/ai` page. The assistant is not a separate product surface.

`AiService` answers only for an authenticated staff role on the current tenant. A client-portal token is refused. A matter id that is not on that tenant is refused with no file contents. The packet passed into an answer is what that staff login can already open: parties, computed docket clocks, note text, and file names. Storage paths and file bytes are not included.

The default provider is `local` (`LEGALSUITE_AI_PROVIDER`). `MatterAssistant` answers from that packet. Deadline and next-step language uses the stored clocks and the product rules (docket hold, limited file). It does not invent case-law citations. Prompts are written to the tenant audit log.

Set `LEGALSUITE_AI_PROVIDER` to `openai` or `anthropic` and the matching `OPENAI_API_KEY` or `ANTHROPIC_API_KEY` to send **one matter’s** context to that vendor. A missing key or a failed call falls back to the on-tenant answer and says so. Docket-wide search never leaves the tenant, even when a key is set. Do not enable a vendor on a production domain until the Staff assistant notice in `PRODUCT.md` is the notice you intend to give the firm.
