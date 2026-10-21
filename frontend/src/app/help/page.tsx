import Link from "next/link";

export const metadata = { title: "Help centre | Owlaw", description: "How to use Owlaw practice management for South African law firms." };

const TOPICS: { id: string; title: string; body: string[] }[] = [
  { id: "start", title: "Getting started", body: ["Register your firm, verify your email and sign in. Add your attorneys and staff under Users, then set your firm details and VAT number under Settings.", "Turn modules on or off under Modules so your team only sees what you use."] },
  { id: "import", title: "Moving from another system (CSV import)", body: ["Go to Import data. Download the template for clients, contacts, matters, trust opening balances or time entries, fill it in Excel or Google Sheets and upload it.", "Every upload is checked first in a dry run. Nothing is saved until the whole file is clean, and only a firm administrator can commit an import. Each import is written to the audit log."] },
  { id: "fica", title: "FICA and client due diligence", body: ["Open FICA to record each client's risk rating, identity and address verification, source of funds and screening results, with supporting documents.", "Owlaw flags clients whose review is due or whose records are incomplete, so you can show your Risk Management and Compliance Programme is followed."] },
  { id: "trust", title: "Trust accounting and the audit pack", body: ["Record trust deposits, payments and transfers per matter. Owlaw prevents a matter ledger going into debit.", "Under Trust audit pack, choose a period to download a PDF and CSV containing the trust reconciliation, matter balances, Section 86(3) and 86(4) investments and exceptions. Hand this to your auditor for the annual Legal Practice Council audit."] },
  { id: "billing", title: "Invoices, payment links and statements", body: ["Create invoices from recorded time and disbursements. To be paid online, enter your own PayFast merchant details under Settings, Payments. Client money goes straight to your firm's PayFast account; Owlaw never holds it.", "Use Payment link on an invoice to copy a link, or share it by WhatsApp or email. Client statements can be downloaded as a PDF from the client's page."] },
  { id: "reports", title: "Practice dashboard and accounting export", body: ["The Practice dashboard shows unbilled work, debtors ageing, monthly billing and collections, and time per attorney.", "Use Export invoices to download a CSV for Xero or Sage Business Cloud Accounting, then import it as sales invoices in your accounting package."] },
  { id: "bundles", title: "Court bundles for CaseLines", body: ["Under Court bundles, choose a matter, create sections (A, B, C) and add the matter's PDFs in order. Owlaw builds one PDF with an index, page stamps such as A-1, a running page number and bookmarks, ready to upload to CaseLines or Court Online."] },
  { id: "backups", title: "Backups and system status", body: ["Firm data is backed up every night into an encrypted file kept for 14 days. A tested restore procedure is documented.", "The public status page shows whether Owlaw is up, how quickly the database responds and when the last backup ran."] },
  { id: "privacy", title: "Privacy and security", body: ["Read how we protect your data and meet POPIA on the Security and POPIA page."] },
];

export default function HelpPage() {
  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <p className="text-sm"><Link href="/" className="underline">Owlaw</Link></p>
      <h1 className="mt-2 text-3xl font-semibold">Help centre</h1>
      <p className="mt-2 text-slate-600">Short guides to every part of Owlaw. Still stuck? Email support@owlaw.co.za.</p>
      <nav className="mt-6 grid gap-1 text-sm sm:grid-cols-2">
        {TOPICS.map((t) => <a key={t.id} href={`#${t.id}`} className="underline">{t.title}</a>)}
      </nav>
      {TOPICS.map((t) => (
        <section key={t.id} id={t.id} className="mt-8 scroll-mt-6">
          <h2 className="text-xl font-semibold">{t.title}</h2>
          {t.body.map((b, i) => <p key={i} className="mt-2 leading-relaxed text-slate-700">{b}</p>)}
        </section>
      ))}
      <p className="mt-10 text-sm"><Link href="/status" className="underline">System status</Link> · <Link href="/security" className="underline">Security and POPIA</Link></p>
    </main>
  );
}
