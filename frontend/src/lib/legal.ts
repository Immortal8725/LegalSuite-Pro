export type LegalDoc = {
  slug: string;
  title: string;
  section: string;
  body: string;
};

export const LEGAL_DOCS: LegalDoc[] = [
  {
    slug: "privacy",
    title: "Privacy & Cookie Policy",
    section: "10.1",
    body: `Controller: the operator of LegalSuite Pro.

What we process
- Account data: name, email, firm slug, role, hashed password, optional TOTP secret.
- Practice data the firm puts in the product: clients, matters, documents, trust ledgers, call metadata, signatures, PAIA manuals, FFC numbers.
- Usage: login times, audit log, module flags, PSTN minute counts.
- Cookies / local storage: ls_access, ls_refresh, ls_user, ls_tenant (session). First-party only. No advertising pixels in the demo.

Purpose and lawful basis (POPIA ss 11–12)
- Perform the contract to host a practice system.
- Legitimate interest / compliance: security, audit, FFC gating, recon.
- Consent: call recording (opt-in).

Attorney-client privilege. Matter text belongs to the tenant. The assistant does not send prompts to a vendor unless the operator sets LEGALSUITE_AI_PROVIDER and a key (see Staff assistant). The operator is an operator under POPIA ss 20–22 for tenant files.

Rights. Access, correction, deletion, objection, and complaint to the Information Regulator (South Africa). Texas users may also use applicable US state rights.

Cookies. Strictly necessary session storage only. The demo does not set third-party cookies.`,
  },
  {
    slug: "acceptable-use",
    title: "Acceptable Use Policy",
    section: "10.2",
    body: `You may not:
- Use the product to practise without a current FFC (or equivalent bar authority) where the law requires one.
- Bypass the hire gate, limited file, docket hold, or per-client trust ledger.
- Attempt to access another tenant’s rows, tokens, or documents.
- Run malware, scraping that degrades the service, or credential stuffing.
- Record a call without the in-product opt-in and the spoken notice the ethics engine requires.
- Upload CSAM, or use the product to commit fraud, money laundering, or unlicensed financial services.
- Represent in-app hashes as SANAS-accredited signatures or a RAF 1 pack as e-lodged with the Fund.

We may suspend a tenant that breaks this policy. Trust ledgers are not deleted solely to hide an inspector exception.`,
  },
  {
    slug: "event-privacy",
    title: "Event Privacy Policy",
    section: "10.3",
    body: `Applies if LegalSuite Pro hosts a webinar, launch, or in-person event.

- We collect name, email, firm, and dietary/access needs you give us.
- Purpose: run the event, send joining instructions, follow up once.
- We do not sell attendee lists.
- Recordings (if any) are announced before the session.
- Photos of a public event may be used on the marketing site; object at registration.
- Retention: 24 months after the event unless you ask us to delete sooner (subject to tax/audit).`,
  },
  {
    slug: "visitor-privacy",
    title: "Visitor Privacy Policy",
    section: "10.4",
    body: `Applies to people who open the marketing site, a firm landing page, these legal pages, or send an intake without a staff account.

- We process IP address and user-agent on the host for security.
- Intake fields become a lead on that firm’s tenant, not the operator’s marketing database.
- Public sign pages collect name, identity number, and a drawn signature. The identity number is hashed into the instrument (ECT Act s 13 analogue) and is not shown back in full.
- Do not submit another person’s special personal information unless you are entitled to.`,
  },
  {
    slug: "dispute",
    title: "Dispute Policy",
    section: "10.5",
    body: `1. Write to the operator (and, if the dispute is about a matter file, to the firm’s information officer).
2. We try to resolve operational disputes in 15 business days.
3. Trust and FFC disputes are referred to the firm’s director; the product will not “fix” an unbalanced recon without a written explanation.
4. If unresolved: mediation, then the High Court of South Africa, Gauteng Local Division, Johannesburg, unless a consumer forum has jurisdiction.
5. Nothing here limits a complaint to the Legal Practice Council, the Information Regulator, or a US state bar.`,
  },
  {
    slug: "terms",
    title: "Terms of Use",
    section: "10.6",
    body: `The service. A modular-monolith practice application: web app, JSON API, optional Flutter client. Features are those listed in PRODUCT.md.

Accounts. One tenant per firm slug. You keep credentials confidential. TOTP is available; demo seeds do not force it.

Your data. You own the matter files. You licence us to host them to provide the service.

No legal advice. Docket dates are computed from statutes cited on the clock. Confirm interruptions, condonation, and service. Fitness scores are a checklist, not an LPC audit.

Liability. To the extent permitted by the CPA and other mandatory law, liability is capped at fees paid in the three months before the claim. We are not liable for missed prescription, an uncertified recon, or a claim the Fund rejects.

ECT Act. Identity-bound signatures in the product are an advanced-signature analogue as between the parties. They are not accredited by a SANAS authentication service provider.`,
  },
  {
    slug: "generative-ai",
    title: "Staff assistant",
    section: "10.7",
    body: `The assistant is assistive. It is not legal advice, not a court e-filing system, and not CaseLines. A human attorney remains responsible.

Default: on-tenant rules for the matter the staff member already has open. Prompts are written to the audit log on the tenant and are not sent outbound.

Optional vendor. An operator may set LEGALSUITE_AI_PROVIDER to openai or anthropic and supply OPENAI_API_KEY or ANTHROPIC_API_KEY. Until that is set, no prompt leaves the tenant. When it is set, the prompt and that one matter’s staff-visible context (not file bytes, not other matters) are sent to that vendor. A missing key stays on the tenant.

Enabling a vendor is a cross-border transfer. Do not enable it on a production domain until a POPIA s 72 mechanism and the operator agreement you intend to rely on are in place.

The assistant does not invent case-law citations as fact. Deadlines follow the matter’s computed clocks. Outputs can be wrong. Tenant prompts are not used to train a shared model.`,
  },
  {
    slug: "merchant",
    title: "Merchant Services Agreement",
    section: "10.8",
    body: `Applies if the operator (or a connected Stripe-class provider) takes card payments for subscriptions or usage invoices.

- The firm is the merchant of record for client fee invoices unless an order form says otherwise. The operator is the merchant of record for LegalSuite Pro subscriptions.
- Chargebacks: keep the engagement and invoice that support the debit.
- PCI: this repository does not store raw card PAN. A production Stripe connection must use Stripe-hosted fields.
- The in-app “connect Stripe” control is a stub until keys exist.
- Trust money is never mixed with subscription charges. Section 86 / IOLTA ledgers are not a payment gateway.`,
  },
  {
    slug: "dpa",
    title: "Data Processing Agreement",
    section: "10.9",
    body: `POPIA ss 20–22 operator terms between the firm (responsible party) and LegalSuite Pro (operator).

1. Subject. Hosting of client files, ledgers, users, signatures, and logs.
2. Instructions. Process only to provide the product, backups, and security. No secondary marketing.
3. Confidentiality. Privilege is the firm’s to assert.
4. Security. JWT + tenant_id, optional TOTP, audit log. Production must add TLS, PostgreSQL, backups. The demo’s H2 database is not that programme.
5. Subprocessors. Only those listed under Service Providers, plus any the firm connects (Stripe, Twilio).
6. Breach. Notify the firm without undue delay.
7. Deletion. On written request after termination, delete or return tenant data except records kept by law.
8. PAIA. The firm remains responsible for its s 51 manual. The product can generate a starting text.`,
  },
  {
    slug: "subprocessors",
    title: "Service Providers, Sub-processors, and Affiliates",
    section: "10.10",
    body: `Operator (LegalSuite Pro): host and support; tenant database; in product.

Vercel (optional): web front end; request logs; UI can publish, API needs a JVM.

PostgreSQL host (optional): all tenant rows; docker-compose / postgres profile; not the default.

Stripe: subscriptions and invoices; connect stub.

Twilio: PSTN metadata and caller ID when the firm connects it. Credentials stay in the server environment.

Authenticator apps: TOTP; shared secret stays on the user row.

OpenAI or Anthropic (optional): model answers for one matter; off unless LEGALSUITE_AI_PROVIDER and a key are set.

Affiliates: none listed. Firms may not treat a demo “connected” integration as a live subprocessor until keys are real.`,
  },
];

export function legalBySlug(slug: string) {
  return LEGAL_DOCS.find((d) => d.slug === slug);
}
