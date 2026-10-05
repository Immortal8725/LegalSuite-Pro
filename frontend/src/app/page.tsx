import Link from "next/link";
import { Check, Scale } from "lucide-react";
import { LegalFooter } from "@/components/legal-footer";

const FEATURES = [
  ["Hire in one motion", "Website consult, conflict check, mandate, and e-sign stay on the same tenant."],
  ["The clock is the home screen", "RAF s 23, Act 40 notice, CCMA referrals, and prescription sit on the docket. The attorney remains responsible for the date."],
  ["The phone is on the file", "In-app calls are included. Public-network minutes are pay-what-you-use. Recording is opt-in, with a spoken notice."],
  ["One attorney, one seat", "Light is R1,199 per month. Section 86 trust is in that seat. There is no minute bundle and no unlimited voice."],
  ["Drafts stay on the firm", "Summaries stay on this tenant unless the firm turns on a model vendor. The attorney remains responsible. This is not legal advice."],
  ["Trust an inspector can read", "Per-client ledgers. Three-way recon (bank, cashbook, and clients). One client cannot spend another's money."],
];

const LIGHT = {
  name: "Light",
  price: "R1,199",
  note: "per month, 1 attorney",
  items: [
    "Practice desk, hire gate, and docket clocks",
    "Section 86 trust included",
    "In-app calls included",
    "Public-network minutes: pay-what-you-use",
    "Local number optional, about R79, or bundled",
  ],
};

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
            Start on Light
          </Link>
        </div>
      </nav>

      <section className="mx-auto max-w-4xl px-6 pb-16 pt-10 text-center">
        <p className="text-sm font-bold uppercase tracking-[0.2em] text-gold">For a solo South African attorney</p>
        <h1 className="mt-3 text-4xl font-extrabold leading-tight text-navy sm:text-5xl">
          One seat at R1,199 a month. The docket watches RAF and Act 40. The phone is what you use.
        </h1>
        <p className="mx-auto mt-5 max-w-2xl text-lg text-slate-500">
          Landing, intake, conflict check, signed mandate, and section 86 trust stay in one firm. Trust is in the seat, not a later add-on. In-app calls are included and write time on hangup. Public-network minutes have no bundle. A local number is optional at about R79 a month, or bundled when you include it.
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

      <section className="mx-auto max-w-xl px-6 py-16">
        <h2 className="text-center text-3xl font-extrabold text-navy">Light</h2>
        <p className="mx-auto mt-2 max-w-xl text-center text-slate-500">
          Prices are in rand. Card billing starts when Stripe is connected. Until then the seat is recorded on the month-end preview and no card is charged.
        </p>
        <div className="mt-10 rounded-2xl border border-gold p-6 shadow-lift">
          <p className="mb-2 text-xs font-bold uppercase text-gold">Solo launch</p>
          <h3 className="text-lg font-bold">{LIGHT.name}</h3>
          <p className="mt-2 text-3xl font-extrabold text-navy">{LIGHT.price}</p>
          <p className="text-xs text-slate-400">{LIGHT.note}</p>
          <ul className="mt-4 space-y-2 text-sm">
            {LIGHT.items.map((item) => (
              <li key={item} className="flex gap-2">
                <Check className="mt-0.5 h-4 w-4 text-emerald-600" />
                {item}
              </li>
            ))}
          </ul>
        </div>
      </section>

      <LegalFooter />
    </div>
  );
}
