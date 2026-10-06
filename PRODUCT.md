# LegalSuite Pro

**Status:** Solo South African pilot. The Light seat and the hosted Postgres path are in the repo. Twilio KYC, Stripe keys, a domain, and TLS are still operator work.  
**Source of truth for what the product is.** Run instructions live in [README.md](README.md). Architecture in [docs/architecture.md](docs/architecture.md).  
**Effective:** 16 September 2026. Operator: LegalSuite Pro (the software described in this repository).

A multi-tenant practice system for a solo South African attorney. A firm registers once. It gets a public website, an intake form, a docket that watches statutory clocks, a hire gate that will not let an unsigned file appear, and a trust ledger that cannot spend another client’s money. Light is R1,199 per month for one attorney and includes section 86 trust. In-app calls are included. Public-network minutes are pay-what-you-use. There is no minute bundle and no unlimited voice. A local number is optional at about R79 per month, or bundled when the operator includes it.

South Africa is the default jurisdiction. Texas still runs on a second tenant so both docket engines can be compared.

This file is not legal advice. The legal pack at the end is the product’s starting texts for privacy, acceptable use, the staff assistant, payments, and operators. A live firm must have them reviewed by counsel before they go on a production domain.

---

## 1. Who it is for

| Persona | What they do here |
| --- | --- |
| Director / owner | Registers the firm, stores the Fidelity Fund Certificate, appoints the information officer, certifies (or refuses) three-way recon |
| Attorney / associate | Runs the hire loop, works the docket, records time, lodges a RAF 1 pack, records opt-in calls |
| Bookkeeper | Posts trust movements, imports the bank CSV, explains an unbalanced month |
| Prospective client | Fills `/firm/{slug}` intake, signs a mandate or waiver at `/sign/{id}` |
| Existing client | Signs into the client portal for matters and invoices |

It is **not** a court e-filing system, a bank, a SANAS-accredited signature CSP, or CaseLines.

---

## 2. The product loops (what makes it a practice system)

1. **Hire is a gate.** Website consult → party-aware conflict → signed waiver instrument if needed → **limited file** → signed mandate → appearance authorised and the pledged retainer posts to trust. Billable time and trust movements wait until the mandate is signed.
2. **The clock is the home screen.** RAF Act s 23, Prescription Act ss 11–13, Act 40 of 2002 s 3, LRA s 191 (ZA). Texas CPRC ch. 16, 74, 101 and Estates Code on the other tenant. Overdue lodge/notice/referral clocks spawn a task and **block trial status**.
3. **The phone is on the file.** In-app WebRTC is free. Outbound PSTN is a callback bridge: the attorney's phone rings, then the other party sees a verified personal number (mobile or landline), `TWILIO_VOICE_FROM`, or a rented local number if the firm already has one. Buying a number is optional. No South Africa regulatory bundle is required for this path. A matter (or an explicit non-matter) is required before dial. Emergency numbers stay on the device dialer. Hangup writes a time entry when the call is on a matter. Recording is opt-in. RICA s 4 is one-party; the product still requires spoken notice (LPC ethics + POPIA). SMS and WhatsApp use the same Twilio account. Email uses the firm's SMTP server, or is logged on the firm when SMTP is not set. The attorney remains responsible for outbound client contact.
4. **Trust that an inspector can read.** Per-client ledgers. LPA ss 86–87 three-way: bank statement = cashbook = sum of client ledgers. A withdrawal cannot spend another client’s money. ZA trust will not move without a **current FFC** (LPA s 84).
5. **A seat, plus the phone you use.** Light is R1,199 per month for one attorney. The seat includes the practice desk and section 86 trust. Public-network minutes are pay-what-you-use and roll onto the month-end usage invoice. SMS and WhatsApp also roll onto the usage invoice. Email through the firm's SMTP server is not metered. There is no included minute bundle and no unlimited voice tier. A local number is optional at about R79 per month, or bundled with the seat. In-app calls stay on the seat.
6. **Draft help on the file.** Summaries, drafts, and intake screening stay on the tenant row unless the operator sets a model-vendor key. Tenant files are not used to train a shared model. The attorney remains responsible.

---

## 3. Phases (all shipped)

| # | Phase | In product |
| --- | --- | --- |
| 1 | Foundation | JWT, tenant isolation, register, onboarding, app shell |
| 2 | Landing + practice | Public `/firm/{slug}`, intake, cases, clients, contacts, documents, calendar, tasks |
| 3 | Financial | Timers, fee invoices (ZA VAT 15%), s 86 / IOLTA trust, expenses |
| 4 | Communication | Internal messages, WebRTC voice, PSTN caller ID, SMS, WhatsApp, email, call registry, leads |
| 5 | Advanced | Conflicts, reports, modules, team, settings, search |
| 6 | Mobile | Responsive web + PWA; Flutter client in `mobile/` |
| 7 | Assistant and integrations | On-tenant draft help, merge templates, in-app e-sign, connect hub, audit log |
| 8 | Polish | README, ERD, class diagrams, tests, PWA manifest |
| 9 | Hire loop | Signed waiver, limited file, engagement unlock, pledged retainer post |
| 10 | South Africa | Prescription / RAF / Act 40 / CCMA clocks, RICA, LPA s 86, VAT, docket hold |
| 11 | Texas | Smith & Associates still runs CPRC 16 / 74 / 101 |
| 12 | Inspection pack | FFC gate, bank CSV, POPIA/PAIA, TOTP, identity-bound e-sign, RAF 1 pack |

---

## 4. Surfaces

| Surface | Route | Who |
| --- | --- | --- |
| Marketing | `/` | Public |
| Firm site + intake | `/firm/{slug}` | Public. Only published sites, and only features a platform operator has approved. |
| Attorney login | `/login` (requires firm slug) | Staff |
| Client portal | `/portal`, `/portal/login` | Clients |
| Public sign | `/sign/{id}` | Signer (identity number required) |
| Docket | `/dashboard` | Staff |
| Practice fitness | `/fitness` | Staff |
| Trust / s 86 | `/trust` | Staff |
| Hire pipeline | `/leads` | Staff |
| Settings (FFC, PAIA, TOTP) | `/settings` | Staff |
| Legal pack | `/legal/{slug}` | Public |

API prefix: `/api/v1`. Envelope: `{ success, data, message }`.

---

## 5. Plans and modules

**Plan (seeded):** Light. R1,199 per month. One attorney. Currency is ZAR.

**In the seat (not a separate charge):** cases, clients, contacts, calendar, tasks, documents, time, billing, messages, conflicts, reports, audit, in-app voice, and section 86 trust.

**Phone:** public-network minutes are pay-what-you-use. `includedVoiceMinutes` is 0. A negative minute cap is treated as 0, not unlimited. A local number is optional at about R79 per month, or bundled. The per-minute carrier rate is `LEGALSUITE_PSTN_OUTBOUND_PER_MIN` and `LEGALSUITE_PSTN_INBOUND_PER_MIN`. Leave them blank until the carrier price is known. Blank does not invent a rate.

**Not in the seat price:** card collection (Stripe) and the public-network bridge (Twilio). Both stay off until keys are in the environment. Draft help stays on the tenant unless the operator sets a model-vendor key.

---

## 6. Inspection pack (phase 12)

| Capability | Behaviour | Honest limit |
| --- | --- | --- |
| FFC | Number + expiry on the firm. ZA trust refuses without a current certificate | Store the number; not a live LPC registry check |
| Bank CSV | FNB / Standard / ABSA-style CSV sets the bank leg of three-way recon | Not Open Banking |
| POPIA / PAIA | Information officer, generated PAIA s 51 manual, operator acknowledgement | Starting manual, not a substitute for counsel |
| TOTP | RFC 6238 enrol / confirm / disable. Login returns `{ requiresTotp: true }` (HTTP 200, no tokens) until the code is supplied | Demo users stay without 2FA so `password` works |
| ECT Act s 13 | Sign hash includes the signer’s identity number | Advanced-signature analogue. **Not** a SANAS-accredited CSP |
| RAF 1 | Compile a lodge pack from a RAF-track matter (C-2001) | **Not** CaseLines or Fund e-lodgement |

**Demo fitness (Ndlovu): 11 / 14.** Three reds are intentional:

1. Three-way recon short **R11,750** (bank R438,250 vs book/ledgers R450,000). Do not certify.
2. C-2002 Act 40 s 3 notice is overdue, so trial is blocked.
3. Production ops on the H2 demo: TLS, a public domain, Stripe keys, and Twilio KYC are not done. Postgres is the hosted path (`docker compose` and `SPRING_PROFILES_ACTIVE=postgres`). The fitness row turns green only when that profile is actually running.

---

## 7. Demo tenants

| Firm | Slug | Staff | Portal |
| --- | --- | --- | --- |
| Ndlovu & Partners (Sandton, GP) | `ndlovu-partners` | `thabo@ndlovulaw.co.za` / `lindiwe@ndlovulaw.co.za` / `sipho@ndlovulaw.co.za` · `password` | `nomsa@example.com` / `portal123` |
| Smith & Associates (Austin, TX) | `smith-associates` | `john@smithlaw.com` · `password` | `sarah@example.com` / `portal123` |

Ndlovu seed: FFC `FFC-GP-2026-44821` (expires 2027-12-31, holder Thabo Ndlovu), information officer Thabo, PAIA generated, POPIA operator acknowledged, bank-feed timestamp set. Matters C-2001 RAF, C-2002 City of Johannesburg (Act 40 overdue), C-2003 CCMA, C-2004 estate, C-2005 contract.

---

## 8. Stack and tenancy

- **Web:** Next.js 15, React 19, Tailwind, shadcn-style primitives.
- **API:** Spring Boot 3.4, Java 21, JPA, JWT. Default H2 (`ddl-auto: create-drop`) for the demo. Hosted path: `SPRING_PROFILES_ACTIVE=postgres`, `docker compose up postgres`, and `GET /api/v1/health`. The Postgres profile refuses the checked-in development JWT secret.
- **Voice and messages:** `/ws/signal` plus HTTP inbox fallback for in-app WebRTC. PSTN uses Twilio (`TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_PUBLIC_BASE_URL`, optional `TWILIO_VOICE_FROM`). SMS and WhatsApp use the same account (`TWILIO_SMS_FROM`, `TWILIO_WHATSAPP_FROM`, optional `TWILIO_MESSAGING_SERVICE_SID`). Email uses `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, and `SMTP_FROM`. Those values stay in the server environment. A purchased number is not required. With no SMTP host, email is logged on the firm and is not delivered.
- **Mobile:** Flutter against `/api/v1`.

Firms are the security boundary. Every row carries `tenant_id`. JWT writes `TenantContext`. Public routes (`landing`, `intake`, `sign`, login, register) never leak another firm’s rows. Prompts and documents stay on the tenant.

---

## 9. Still needs a person

Coded in this repo: Postgres via Docker, `.env.example` for Twilio, Stripe, SMS, WhatsApp, and SMTP with no secrets, `GET /api/v1/health`, and the hosted runbook in [README.md](README.md).

A person still has to:

- Approve Twilio and finish KYC before a public number or outbound PSTN call can be real. Calls are real when `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, and `TWILIO_PUBLIC_BASE_URL` are set, plus a caller ID the account already owns or has verified (`TWILIO_VOICE_FROM`, an in-app verified personal number, or a number already on the account). Upgraded accounts no longer have the free trial From number. Trial accounts, if any remain, can only call verified destinations. SMS is real when `TWILIO_SMS_FROM` or a rented SMS-capable number is set. A verified landline cannot send SMS. WhatsApp sandbox recipients must join the sandbox first (`TWILIO_WHATSAPP_FROM` blank uses `+14155238886`). Email is real when SMTP is set; otherwise it is a dry run. Native CallKit is not built yet.
- Create the Stripe account and put keys in the environment. Do not commit them. Card fields must be Stripe-hosted.
- Choose a domain, put TLS in front of the web and API processes, and point `TWILIO_PUBLIC_BASE_URL` at the public HTTPS origin.
- Set `LEGALSUITE_JWT_SECRET` and a non-default `DB_PASSWORD` on that host.
- Set the per-minute PSTN rate from the carrier price. Do not treat a blank rate as a bundle of free minutes.
- Have counsel review the legal pack before it is published on a production domain.

Not in the product: live Open Banking, a SANAS-accredited signature, CaseLines or RAF e-lodgement, SSO, and backups beyond what the operator runs.

---

## 10. Legal pack

These texts are the product’s public legal documents. They apply to **LegalSuite Pro the software** (the operator of this repository and any production domain that serves it). They do **not** replace a firm’s own engagement letter, PAIA manual, or FFC.

Publish these ten documents on the public site at `/legal/{slug}` (footer on `/`). Long-form control copy is this file.

Footer set: Privacy & Cookie Policy · Acceptable Use Policy · Event Privacy Policy · Visitor Privacy Policy · Dispute Policy · Terms of Use · Staff assistant · Merchant Services Agreement · Data Processing Agreement · Service Providers, Sub-processors, and Affiliates.

Governing law for the **operator-customer** relationship: Republic of South Africa (POPIA, PAIA, ECT Act 25 of 2002, CPA where it applies). Texas firms using the product remain bound by their own professional rules for the practice; this pack governs use of the software.

---

### 10.1 Privacy & Cookie Policy

**Controller:** the operator of LegalSuite Pro.  
**Information officer (demo / until production appointment):** the firm owner on the tenant; for the operator, the address published on the marketing site.

**What we process**

- Account data: name, email, firm slug, role, hashed password, optional TOTP secret.
- Practice data **the firm puts in the product**: clients, matters, documents, trust ledgers, call metadata, signatures, PAIA manuals, FFC numbers.
- Usage: login times, audit log, module flags, PSTN minute counts.
- Cookies / local storage: `ls_access`, `ls_refresh`, `ls_user`, `ls_tenant` (session). First-party only. No advertising pixels in the demo.

**Purpose and lawful basis (POPIA ss 11–12)**

- Perform the contract to host a practice system.
- Legitimate interest / compliance: security, audit, FFC gating, recon.
- Consent: call recording (opt-in), marketing cookies if ever added (none today).

**Attorney-client privilege.** Matter text, notes, and documents belong to the **tenant**. The assistant does not send prompts to a vendor unless the operator sets `LEGALSUITE_AI_PROVIDER` and a key (see §10.7). The operator is an **operator** under POPIA ss 20–22 for tenant files (see the DPA). The operator does not use tenant files to train a public model.

**Retention.** Tenant data until the firm closes the account or a statutory period the firm sets. Audit rows follow the firm’s file-retention policy. Demo H2 data dies when the process dies.

**Rights.** Access, correction, deletion, objection, and complaint to the Information Regulator (South Africa). Texas users may also use applicable US state rights. Requests go to the information officer on the firm record, or to the operator for operator-held account data.

**Cookies.** Strictly necessary session cookies/local storage. The demo does not set third-party cookies.

**International transfers.** Demo runs in-process. Production hosting, if outside the Republic, requires a POPIA s 72 transfer mechanism and an operator agreement.

---

### 10.2 Acceptable Use Policy

You may not:

- Use the product to practise without a current FFC (or equivalent bar authority) where the law requires one.
- Bypass the hire gate, limited file, docket hold, or per-client trust ledger.
- Attempt to access another tenant’s rows, tokens, or documents.
- Run malware, scraping that degrades the service, or credential stuffing.
- Record a call without the in-product opt-in and the spoken notice the ethics engine requires.
- Upload CSAM, or use the product to commit fraud, money laundering, or unlicensed financial services.
- Represent in-app hashes as SANAS-accredited signatures or a RAF 1 pack as e-lodged with the Fund.

We may suspend a tenant that breaks this policy. Trust ledgers are not deleted solely to hide an inspector exception.

---

### 10.3 Event Privacy Policy

Applies if LegalSuite Pro hosts a webinar, launch, or in-person event.

- We collect name, email, firm, and dietary/access needs you give us.
- Purpose: run the event, send joining instructions, follow up once.
- We do not sell attendee lists.
- Recordings (if any) are announced before the session. Opt out by leaving or emailing the organiser.
- Photos of a public event may be used on the marketing site; say so at registration if you object.
- Retention: 24 months after the event unless you ask us to delete sooner (subject to tax/audit).

---

### 10.4 Visitor Privacy Policy

Applies to people who open `/`, `/firm/{slug}`, `/legal/*`, or send an intake without creating a staff account.

- We process the IP address and user-agent on the host for security.
- Intake fields (name, contact, opposing party, narrative) become a **lead on that firm’s tenant**, not the operator’s marketing database.
- Public sign pages collect name, identity number, and a drawn signature. The identity number is hashed into the instrument (ECT Act s 13 analogue) and is not shown back in full.
- Do not submit another person’s special personal information unless you are entitled to.

---

### 10.5 Dispute Policy

1. Write to the operator (and, if the dispute is about a matter file, to the firm’s information officer).
2. We try to resolve operational disputes in 15 business days.
3. Trust and FFC disputes are referred to the firm’s director; the product will not “fix” an unbalanced recon without a written explanation.
4. If unresolved: mediation, then the High Court of South Africa, Gauteng Local Division, Johannesburg, unless a consumer forum has jurisdiction.
5. Nothing here limits a complaint to the Legal Practice Council, the Information Regulator, or a US state bar.

---

### 10.6 Terms of Use

**The service.** A modular-monolith practice application: web app, JSON API, optional Flutter client. Features are those listed in this PRODUCT.md.

**Accounts.** One tenant per firm slug. You keep credentials confidential. TOTP is available; demo seeds do not force it.

**Your data.** You own the matter files. You licence us to host them to provide the service. You warrant you have a lawful basis to load client data.

**No legal advice.** Docket dates are computed from statutes cited on the clock. Confirm interruptions, condonation, and service. Fitness scores are a checklist, not an LPC audit.

**Fees.** Light is R1,199 per month for one attorney and includes section 86 trust. Public-network minutes are pay-what-you-use. There is no minute bundle. A local number is optional at about R79 per month, or bundled. Prices on `/` are in rand.

**Warranties.** The software is provided as a running demo in this repository. Production SLAs exist only under a signed order form.

**Liability.** To the extent permitted by the CPA and other mandatory law, liability is capped at fees paid in the three months before the claim. We are not liable for missed prescription, an uncertified recon, or a claim the Fund rejects.

**Termination.** You may export documents you uploaded and close the tenant. We may terminate for Acceptable Use breaches.

**ECT Act.** Electronic communications and identity-bound signatures in the product are intended to satisfy ECT Act s 13 for **advanced** electronic signatures as between the parties using this demo. They are **not** accredited by a SANAS authentication service provider.

---

### 10.7 Staff assistant

- The assistant is **assistive**. It is not legal advice, not a court e-filing system, and not CaseLines. A human attorney remains responsible.
- **Default:** on-tenant rules for the matter the staff member already has open (parties, computed clocks, notes, file names). Prompts are written to the **audit log** on the tenant and are not sent outbound.
- **Optional vendor.** An operator may set `LEGALSUITE_AI_PROVIDER` to `openai` or `anthropic` and supply `OPENAI_API_KEY` or `ANTHROPIC_API_KEY`. Until that is set, no prompt leaves the tenant. When it is set, the prompt and the single matter’s staff-visible context (not file bytes, not other matters) are sent to that vendor to produce an answer. A missing key or a failed call stays on the tenant and says so.
- Enabling a vendor is a cross-border transfer. Do not enable it on a production domain until a POPIA s 72 mechanism and the operator agreement you intend to rely on are in place. This section is the product’s notice of that path.
- The assistant does not invent case-law citations as fact. Deadline and next-step suggestions follow the matter’s computed clocks and the product rules (docket hold, limited file). Confirm interruptions, condonation, and service.
- Do not paste another client’s privileged text into a chat that people without a need-to-know can open.
- Outputs can be wrong. We do not use tenant prompts to train a shared model.
- Privilege stripping is a rough check, not a guarantee. Treat the assistant as inside the firm, not as a court reporter.

---

### 10.8 Merchant Services Agreement

Applies if the operator (or a connected Stripe-class provider) takes card payments for subscriptions or usage invoices.

- The firm is the merchant of record for **client fee invoices** unless we say otherwise in an order form. The operator is the merchant of record for **LegalSuite Pro subscriptions**.
- Chargebacks: you must keep the engagement and invoice that support the debit.
- Refunds of unused subscription days: as on the invoice. Public-network minutes already used are not refundable.
- PCI: we do not store raw card PAN in this repository. A production Stripe connection must use Stripe-hosted fields.
- The in-app “connect Stripe” control is a **stub** until keys exist. Do not treat a green “connected” flag in the demo as a live merchant account.
- Trust money is **never** mixed with subscription charges. s 86 / IOLTA ledgers are not a payment gateway.

---

### 10.9 Data Processing Agreement (operator)

This DPA is the POPIA ss 20–22 operator terms between **the firm (responsible party)** and **LegalSuite Pro (operator)** for personal information in the tenant.

1. **Subject.** Hosting of client files, ledgers, users, signatures, and logs.
2. **Instructions.** Process only to provide the product, backups, and security. No secondary marketing.
3. **Confidentiality.** Staff and subprocessors are bound to confidentiality. Privilege is the firm’s to assert.
4. **Security.** Access control (JWT + tenant_id), optional TOTP, audit log. Production must add TLS, PostgreSQL, backups, and a written security programme. The demo’s H2 database is **not** that programme.
5. **Subprocessors.** Only those listed in §10.10, plus any the firm connects (Stripe, Twilio).
6. **Breach.** Notify the firm without undue delay after becoming aware, with enough fact for a Regulator notification if required.
7. **Deletion.** On written request after termination, delete or return tenant data except records we must keep by law.
8. **Audit.** The firm may request the fitness snapshot, audit log export, and (in production) a SOC-style summary when one exists.
9. **International.** No transfer outside the hosting region without a s 72 mechanism.
10. **PAIA.** The firm remains responsible for its s 51 manual. The product can generate a starting text; the firm must appoint an information officer.

---

### 10.10 Service providers, subprocessors, and affiliates

| Party | Role | Data | Status in this repo |
| --- | --- | --- | --- |
| Operator (LegalSuite Pro) | Host, support | Tenant database | In product |
| Vercel (optional) | Web front end | Request logs, cookies | UI can publish; API needs a JVM |
| PostgreSQL host (optional) | Database | All tenant rows | `docker-compose` / postgres profile; **not** default |
| Stripe | Light subscription and usage invoices | Billing details | Keys via environment. Toggle is not a live account. |
| Twilio | PSTN, SMS, WhatsApp | Call and message metadata, numbers, caller ID | Real sends when server env vars are set. No keys in the repo. |
| Google/Apple authenticator apps | TOTP | Shared secret stays on the user row | In product; user-chosen app |
| OpenAI or Anthropic (optional) | Model answers for one matter | Prompt and staff-visible matter context, not file bytes | Off unless `LEGALSUITE_AI_PROVIDER` and a key are set |

Affiliates: none listed. If the operator group adds a company that can see tenant data, this table will be updated **before** that access starts.

Firms may not treat a demo “connected” integration as a live subprocessor until keys are real.

---

## 11. Document control

| Version | Date | Notes |
| --- | --- | --- |
| 1.0 | 16 September 2026 | All twelve phases recorded. Legal pack added to match the public policy set. |
| 1.1 | 5 October 2026 | Matter-scoped staff assistant. Model vendor stays off unless an operator sets a key. |
| 1.2 | 5 October 2026 | Light seat at R1,199 for one attorney. Phone is pay-what-you-use. Trust is in the seat. Hosted Postgres path, health check, and env template recorded. |
| 1.3 | 5 October 2026 | SMS, WhatsApp, and email from a matter or contact. Messaging is pay-what-you-use. Email via SMTP is not metered. |

Questions about the **software**: the repository owner.  
Questions about a **matter**: the firm on the tenant, not the operator.
