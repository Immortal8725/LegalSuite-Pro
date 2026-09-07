import Link from "next/link";
import { Scale, Check } from "lucide-react";

const FEATURES = [
  ["Cases & matters", "Statuses, court info, opposing counsel, notes, and a full timeline."],
  ["Client CRM + intake", "Leads from your public site land in the same workspace as retained clients."],
  ["Documents", "Upload, categorize, and keep privileged files with the matter."],
  ["Calendar & tasks", "Hearings, filing deadlines, and a kanban for the week."],
  ["Time, invoices, trust", "6-minute increments, draft invoices, IOLTA ledgers that cannot overdraw."],
  ["In-app voice (WebRTC)", "Attorney-to-attorney and attorney-to-client portal calls are free. PSTN bills at month-end."],
  ["AI on the docket", "Summaries, intake screening, and drafts stay inside the firm. No API key."],
  ["Templates & e-sign", "Merge letters from matter fields, then send a public signature link."],
];

const PLANS = [
  { name: "Free", price: "$0", note: "Solo start", items: ["Landing page", "2 users", "25 matters", "30 in-app minutes"] },
  { name: "Essentials", price: "$49", note: "per user / month", items: ["Unlimited cases", "Time + billing", "Client portal add-on", "200 PSTN minutes"] },
  { name: "Professional", price: "$99", note: "per user / month", popular: true, items: ["Trust / IOLTA", "Call recording (opt-in)", "E-sign ready", "1,000 PSTN minutes"] },
  { name: "Enterprise", price: "$149", note: "per user / month", items: ["Unlimited seats", "SSO-ready roles", "API access", "Unlimited voice"] },
];

export default function MarketingPage() {
  return (
    <div className="min-h-screen bg-white">
      <nav className="mx-auto flex max-w-6xl items-center justify-between px-6 py-5">
        <div className="flex items-center gap-2 font-extrabold text-navy">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gold text-navy-dark">
            <Scale className="h-4 w-4" />
          </span>
          Legal<span className="text-gold">Suite</span> Pro
        </div>
        <div className="flex items-center gap-3">
          <Link href="/login" className="text-sm font-semibold text-slate-600 hover:text-navy">
            Sign in
          </Link>
          <Link href="/portal/login" className="hidden text-sm font-semibold text-slate-600 sm:inline">
            Client portal
          </Link>
          <Link href="/register" className="rounded-lg bg-gold px-4 py-2 text-sm font-bold text-navy-dark hover:bg-gold-light">
            Start free trial
          </Link>
        </div>
      </nav>

      <section className="mx-auto max-w-4xl px-6 pb-16 pt-10 text-center">
        <p className="text-sm font-bold uppercase tracking-[0.2em] text-gold">For every size of firm</p>
        <h1 className="mt-3 text-4xl font-extrabold leading-tight text-navy sm:text-5xl">
          Register once. Get a live website, a practice desk, and only the modules you click on.
        </h1>
        <p className="mx-auto mt-5 max-w-2xl text-lg text-slate-500">
          Solo shops and 50-attorney teams share the same platform. Every firm receives a public landing page and intake form on day one. Turn on trust accounting, voice minutes, or a client portal when you need them — billed at month end for what you used.
        </p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Link href="/register" className="rounded-xl bg-gold px-6 py-3 font-bold text-navy-dark shadow-lift">
            Register your firm
          </Link>
          <Link href="/login" className="rounded-xl border px-6 py-3 font-bold text-navy">
            Demo: smith-associates
          </Link>
        </div>
      </section>

      <section className="bg-slate-50 py-16">
        <div className="mx-auto grid max-w-6xl gap-6 px-6 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map(([title, body]) => (
            <div key={title} className="rounded-2xl bg-white p-6 shadow-card">
              <h3 className="font-bold text-navy">{title}</h3>
              <p className="mt-2 text-sm text-slate-500">{body}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 py-16">
        <h2 className="text-center text-3xl font-extrabold text-navy">Plans that grow with the docket</h2>
        <p className="mx-auto mt-2 max-w-xl text-center text-slate-500">
          Start on Free. Voice over the public switched network and add-on modules invoice at month end — no prepaid buckets required.
        </p>
        <div className="mt-10 grid gap-5 md:grid-cols-2 lg:grid-cols-4">
          {PLANS.map((p) => (
            <div
              key={p.name}
              className={`rounded-2xl border p-6 ${p.popular ? "border-gold shadow-lift" : "border-slate-200"}`}
            >
              {p.popular && <p className="mb-2 text-xs font-bold uppercase text-gold">Most chosen</p>}
              <h3 className="text-lg font-bold">{p.name}</h3>
              <p className="mt-2 text-3xl font-extrabold text-navy">{p.price}</p>
              <p className="text-xs text-slate-400">{p.note}</p>
              <ul className="mt-4 space-y-2 text-sm">
                {p.items.map((i) => (
                  <li key={i} className="flex gap-2">
                    <Check className="mt-0.5 h-4 w-4 text-emerald-600" />
                    {i}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </section>

      <footer className="border-t px-6 py-8 text-center text-sm text-slate-400">
        LegalSuite Pro · Attorney-client privilege stays on your tenant row. Call recording is opt-in.
      </footer>
    </div>
  );
}
