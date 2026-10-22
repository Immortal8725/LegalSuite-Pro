import Link from "next/link";
import { Check, Scale } from "lucide-react";
import { LegalFooter } from "@/components/legal-footer";
import { ADD_ONS, PLANS, PRICE_NOTE } from "@/lib/pricing";
import { DEMO_MODE } from "@/lib/demo";
import { HomeTriage } from "@/components/home-triage";

const FEATURES = [
  ["Hire in one motion", "Website consult, conflict check, mandate, and e-sign stay on the same tenant."],
  ["The clock is the home screen", "RAF s 23, Act 40 notice, CCMA referrals, and prescription sit on the docket. The attorney remains responsible for the date."],
  ["The phone is on the file", "In-app calls are included. Public-network minutes are pay-what-you-use. Recording is opt-in, with a spoken notice."],
  ["Priced per attorney", "Solo R899, Firm R1,199 and Practice R1,599 per attorney a month, ex VAT. Section 86 trust is in every plan. Calls are R1.49 a minute with no bundle."],
  ["Drafts stay on the firm", "Summaries stay on this tenant unless the firm turns on a model vendor. The attorney remains responsible. This is not legal advice."],
  ["Trust an inspector can read", "Per-client ledgers. Three-way recon (bank, cashbook, and clients). One client cannot spend another's money."],
];

export default function MarketingPage() {
  return (
    <div className="min-h-screen bg-white">
      <nav className="mx-auto flex max-w-6xl items-center justify-between px-6 py-5">
        <div className="flex items-center gap-2 font-extrabold text-navy">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gold text-navy-dark">
            <Scale className="h-4 w-4" />
          </span>
          <span>Ow<span className="text-gold">Law</span></span>
        </div>
        <div className="flex items-center gap-3">
          <Link href="/find-a-lawyer" className="hidden text-sm font-semibold text-slate-600 hover:text-navy sm:inline">
            Find a Lawyer
          </Link>
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
        <p className="text-base font-semibold text-navy">Eyes on every matter.</p>
        <p className="text-sm font-bold uppercase tracking-[0.2em] text-gold">For a solo South African attorney</p>
        <h1 className="mt-3 text-4xl font-extrabold leading-tight text-navy sm:text-5xl">
          From R899 a month. The docket watches RAF and Act 40. The phone is what you use.
        </h1>
        <p className="mx-auto mt-5 max-w-2xl text-lg text-slate-600">
          Landing, intake, conflict check, signed mandate, and section 86 trust stay in one firm. Trust is in the seat, not a later add-on. In-app calls are included and write time on hangup. Public-network minutes have no bundle. A local number is optional at about R79 a month, or bundled when you include it.
        </p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Link href="/register" className="rounded-xl bg-gold px-6 py-3 font-bold text-navy-dark shadow-lift">
            Register your firm
          </Link>
          <Link href="/login" className="rounded-xl border px-6 py-3 font-bold text-navy">
            {DEMO_MODE ? "Demo: ndlovu-partners" : "Sign in"}
          </Link>
        </div>
        <HomeTriage />
      </section>

      <section className="bg-slate-50 py-16">
        <div className="mx-auto grid grid-cols-1 max-w-6xl gap-6 px-6 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map(([title, body]) => (
            <div key={title} className="rounded-2xl bg-white p-6 shadow-card">
              <h3 className="font-bold text-navy">{title}</h3>
              <p className="mt-2 text-sm text-slate-600">{body}</p>
            </div>
          ))}
        </div>
      </section>

      <section id="pricing" className="mx-auto max-w-6xl px-6 py-16">
        <h2 className="text-center text-3xl font-extrabold text-navy">Pricing</h2>
        <p className="mx-auto mt-2 max-w-2xl text-center text-slate-600">
          {PRICE_NOTE} Every plan includes section 86 trust. Start with a 14-day trial.
        </p>
        <div className="mt-10 grid grid-cols-1 gap-6 md:grid-cols-3">
          {PLANS.map((plan) => (
            <div
              key={plan.slug}
              className={`flex flex-col rounded-2xl border p-6 ${plan.tag ? "border-gold shadow-lift" : "border-slate-200 shadow-card"}`}
            >
              <p className="mb-2 h-4 text-xs font-bold uppercase text-gold">{plan.tag ?? ""}</p>
              <h3 className="text-lg font-bold text-navy">{plan.name}</h3>
              <p className="mt-2 text-3xl font-extrabold text-navy">{plan.price}</p>
              <p className="text-xs text-slate-600">{plan.unit}, ex VAT</p>
              <ul className="mt-4 flex-1 space-y-2 text-sm">
                {plan.items.map((item) => (
                  <li key={item} className="flex gap-2">
                    <Check className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" aria-hidden="true" />
                    {item}
                  </li>
                ))}
              </ul>
              <Link
                href={`/register?plan=${plan.slug}`}
                className={`mt-6 rounded-xl px-4 py-3 text-center font-bold ${plan.tag ? "bg-gold text-navy-dark" : "border text-navy"}`}
              >
                Start on {plan.name}
              </Link>
            </div>
          ))}
        </div>
        <div className="mx-auto mt-10 max-w-3xl rounded-2xl bg-slate-50 p-6">
          <h3 className="font-bold text-navy">Add-ons and usage</h3>
          <dl className="mt-3 grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
            {ADD_ONS.map(([name, price]) => (
              <div key={name}>
                <dt className="font-semibold text-navy">{name}</dt>
                <dd className="text-slate-600">{price}</dd>
              </div>
            ))}
          </dl>
        </div>
      </section>

      <LegalFooter />
    </div>
  );
}
