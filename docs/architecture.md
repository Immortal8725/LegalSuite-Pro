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

Staff ask from the matter workspace (`/cases/{id}`) or from the existing `/ai` page. The assistant is not a separate product surface.

`AiService` answers only for an authenticated staff role on the current tenant. A client-portal token is refused. A matter id that is not on that tenant is refused with no file contents. The packet passed into an answer is what that staff login can already open: parties, computed docket clocks, note text, and file names. Storage paths and file bytes are not included.

The default provider is `local` (`LEGALSUITE_AI_PROVIDER`). `MatterAssistant` answers from that packet. Deadline and next-step language uses the stored clocks and the product rules (docket hold, limited file). It does not invent case-law citations. Prompts are written to the tenant audit log.

Set `LEGALSUITE_AI_PROVIDER` to `openai` or `anthropic` and the matching `OPENAI_API_KEY` or `ANTHROPIC_API_KEY` to send **one matter’s** context to that vendor. A missing key or a failed call falls back to the on-tenant answer and says so. Docket-wide search never leaves the tenant, even when a key is set. Do not enable a vendor on a production domain until the Generative AI notice in `PRODUCT.md` is the notice you intend to give the firm.
