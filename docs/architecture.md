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

`JwtAuthFilter` parses the bearer token, writes `tenantId`, `userId`, `role`, and `email` into `TenantContext`, then clears them in `finally`. Repositories always query `findBy…AndTenantId`. Public routes (`/landing/{slug}`, `/public/sites/{slug}`, `POST /intake/{slug}`, `/sign/{id}`, login/register) skip the filter's auth requirement but still never leak another firm's rows.

The public marketing site is a separate surface from the staff desk. A firm owner toggles features. A platform operator (`superadmin`) approves an enable, and a publish, before that feature is returned by the public API. See [tenant-public-site.md](tenant-public-site.md).

## Modules

`modules` is a catalog. `tenant_modules` stores enabled flags. Core slugs cannot be disabled. Section 86 trust is in the Light seat and is not a month-end add-on. The Light subscription itself is a seat line on the usage preview. Public-network minutes are the usage. The demo tenants have the catalog enabled so the sample files can be opened.

Product billing is a separate path. `PayFastCheckoutService` builds a signed seat subscription (or a card-token checkout). `PayFastItnService` checks the signature and asks PayFast to confirm the ITN before a seat becomes active. `MinutesChargeJob` is a manual trigger that invoices PSTN minutes and, when a token is on file, calls the adhoc API. `billing_accounts` holds seat status, the last payment, and the token. Those rows are not client `invoices` and not trust ledgers. See [BILLING.md](../BILLING.md).

## Voice

In-app calls are included in the Light seat. `VoiceService` records duration and a `callType` of `webrtc` or `pstn_outbound` / `pstn_inbound`. Public-network cost uses `LEGALSUITE_PSTN_OUTBOUND_PER_MIN` and `LEGALSUITE_PSTN_INBOUND_PER_MIN`. If that rate is blank, cost stays zero and the minutes are still recorded. There is no included minute bundle. Recording is opt-in. `CallEthics` still decides the spoken-notice rule for the firm country.

Public-network dialing is a callback bridge, not a browser softphone:

1. Caller ID is resolved without requiring a purchase. Order: the firm's active rented DID, else a verified personal number saved in the app (mobile or landline), else `TWILIO_VOICE_FROM`, else the first IncomingPhoneNumber already on the Twilio account, else the first verified Outgoing Caller ID on that account. If none exist, the API tells staff to verify a personal number or set `TWILIO_VOICE_FROM`.
2. Staff choose a matter, or explicitly mark the call as not on a matter.
3. Emergency numbers are refused. They stay on the device dialer.
4. Twilio rings the attorney's own phone, then dials the destination with that caller ID.
5. Hangup updates the same `CallRecord`. A matter call still writes a time entry.

Twilio credentials are environment variables only: `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_PUBLIC_BASE_URL` (the public API address Twilio uses for the bridge and status callbacks), and optional `TWILIO_VOICE_FROM`. The Integrations toggle does not store a password. Webhooks require a valid `X-Twilio-Signature`. No South Africa End-User bundle is part of this path. After an account leaves trial, the free trial From number is gone.

Native CallKit (iOS) and ConnectionService (Android) are not in this slice. The Flutter client starts the same bridge. The cellular dialer is what rings. An in-app incoming-call UI would need a VoIP push entitlement and is a follow-up.

## SMS, WhatsApp, and email

`OutboundMessageService` sends from the voice page, a matter, a client, or a contact. The matter id is stored when staff choose one. Every attempt is an `outbound_messages` row and an audit event on that tenant.

SMS and WhatsApp use the same `TwilioGateway` as the call bridge (`sendMessage`). There is not a second Twilio client. Emergency numbers are refused. SMS comes from `TWILIO_SMS_FROM`, a messaging service SID, or a rented local number. WhatsApp comes from `TWILIO_WHATSAPP_FROM`, or the Twilio sandbox number `+14155238886` when that variable is blank. A recipient must join the sandbox before a sandbox message will arrive. Outside the 24 hour session, send an approved template content SID.

Email uses `SmtpMailer` (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM`). When the host or from address is missing, the send is a dry run: the row is kept, the API log records it, and nothing is delivered. Successful SMS and WhatsApp unit costs land on the month-end usage invoice. Email is not metered.

## Staff assistant

Staff ask from the matter workspace (`/cases/{id}`) or from the existing `/ai` page. The assistant is not a separate product surface.

`AiService` answers only for an authenticated staff role on the current tenant. A client-portal token is refused. A matter id that is not on that tenant is refused with no file contents. The packet passed into an answer is what that staff login can already open: parties, computed docket clocks, note text, and file names. Storage paths and file bytes are not included.

The default provider is `local` (`LEGALSUITE_AI_PROVIDER`). `MatterAssistant` answers from that packet. Deadline and next-step language uses the stored clocks and the product rules (docket hold, limited file). It does not invent case-law citations. Prompts are written to the tenant audit log.

Set `LEGALSUITE_AI_PROVIDER` to `openai` or `anthropic` and the matching `OPENAI_API_KEY` or `ANTHROPIC_API_KEY` to send **one matter’s** context to that vendor. A missing key or a failed call falls back to the on-tenant answer and says so. Docket-wide search never leaves the tenant, even when a key is set. Do not enable a vendor on a production domain until the Staff assistant notice in `PRODUCT.md` is the notice you intend to give the firm.
