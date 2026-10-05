import Link from "next/link";
import { Check, Scale } from "lucide-react";
import { LegalFooter } from "@/components/legal-footer";

const FEATURES = [
  ["Hire in one motion", "Website consult, conflict check, merged mandate, e-sign, and trust retainer, on the same tenant."],
  ["The clock is the home screen", "RAF s 23, Act 40 notice, CCMA referrals, and prescription sit on the docket with the file."],
  ["The phone is on the file", "In-app WebRTC is free. Hangup writes a time entry. Recording is opt-in with a RICA / two-party warning."],
  ["Metered, not seated", "Core stays on. Add-ons and PSTN minutes invoice at month end for what you actually used."],
  ["Drafts stay on the firm", "Summaries stay on this tenant unless the firm turns on a model vendor. Tenant files are not a training set. The attorney remains responsible."],
  ["Trust that would survive an inspector", "Per-client ledgers. Three-way recon (bank = cashbook = clients). No overdraw of another client's money."],
];

const PLANS = [
  { name: "Free", price: "$0", note: "Solo start", items: ["Landing page", "2 users", "25 matters", "30 in-app minutes"] },
  { name: "Essentials", price: "$49", note: "per user / month", items: ["Unlimited cases", "Time + billing", "Client portal add-on", "200 PSTN minutes"] },
  { name: "Professional", price: "R1,800", note: "per user / month (demo USD $99)", popular: true, items: ["s 86 trust / IOLTA", "Call recording (opt-in)", "E-sign ready", "1,000 PSTN minutes"] },
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
        <p className="text-sm font-bold uppercase tracking-[0.2em] text-gold">South Africa first. Texas still in the other tenant.</p>
        <h1 className="mt-3 text-4xl font-extrabold leading-tight text-navy sm:text-5xl">
          Register once. The website takes the consult. The docket watches RAF and Act 40. The month-end bill is what you used.
        </h1>
        <p className="mx-auto mt-5 max-w-2xl text-lg text-slate-500">
          Landing → intake → LPC conflict → signed mandate → section 86 trust, in one tenant. In-app calls are free and write time on hangup. Client text never leaves the firm. Add-on modules and PSTN minutes invoice when the month closes.
        </p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Link href="/register" className="rounded-xl bg-gold px-6 py-3 font-bold text-navy-dark shadow-lift">
            Register your firm
          </Link>
          <Link href="/login" className="rounded-xl border px-6 py-3 font-bold text-navy">
            Demo: ndlovu-partners
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
          Start on Free. Voice over the public switched network and add-on modules invoice at month end. There is no prepaid bucket.
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

      <LegalFooter />
    </div>
  );
}
