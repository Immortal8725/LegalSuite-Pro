"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { apiGet, apiPost } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import { ErrorBanner } from "@/components/ui/dialog";
import type { Landing } from "@/lib/types";
import { Scale } from "lucide-react";
import { cn } from "@/lib/utils";

export default function FirmLandingPage() {
  const params = useParams<{ slug: string }>();
  const slug = params.slug;
  const [page, setPage] = useState<Landing | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState<string | null>(null);
  const [form, setForm] = useState({
    name: "",
    email: "",
    phone: "",
    caseType: "General",
    description: "",
    opposingParty: "",
    accrualDate: "",
    governmentalDefendant: false,
  });

  useEffect(() => {
    apiGet<Landing>(`/api/v1/landing/${slug}`)
      .then(setPage)
      .catch((e) => setError(e.message));
  }, [slug]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const res = await apiPost<{ message: string }>(`/api/v1/intake/${slug}`, form);
      setSent(res.message);
      setForm({
        name: "",
        email: "",
        phone: "",
        caseType: "General",
        description: "",
        opposingParty: "",
        accrualDate: "",
        governmentalDefendant: false,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send.");
    }
  }

  if (!page && !error) return <p className="p-10 text-sm text-slate-500">Loading firm site…</p>;
  if (error && !page) {
    return (
      <div className="p-10">
        <ErrorBanner error={error} />
        <Link href="/" className="mt-4 inline-block text-sm font-semibold text-navy">
          Back to LegalSuite Pro
        </Link>
      </div>
    );
  }
  if (!page) return null;

  const t = page.tenant;
  const template = page.template || "classic";
  const tone =
    template === "modern"
      ? "from-slate-900 via-slate-800 to-slate-700"
      : template === "boutique"
        ? "from-emerald-950 via-emerald-900 to-stone-800"
        : template === "corporate"
          ? "from-blue-950 via-blue-900 to-slate-900"
          : "from-navy-dark via-navy to-navy-light";

  return (
    <div className="min-h-screen bg-white">
      <header className={cn("px-6 py-16 text-white bg-gradient-to-br", tone)}>
        <div className="mx-auto flex max-w-5xl items-center justify-between">
          <div className="flex items-center gap-2 font-extrabold">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gold text-navy-dark">
              <Scale className="h-4 w-4" />
            </span>
            {t.firmName}
          </div>
          <Link href="/portal/login" className="text-sm font-semibold text-gold">
            Client portal
          </Link>
        </div>
        <div className="mx-auto mt-12 max-w-5xl">
          <h1 className="text-4xl font-extrabold sm:text-5xl">{page.heroTitle}</h1>
          <p className="mt-4 max-w-2xl text-lg text-white/80">{page.heroSubtitle || t.tagline}</p>
          <a href="#intake" className="mt-8 inline-block rounded-xl bg-gold px-6 py-3 font-bold text-navy-dark">
            Request a consultation
          </a>
        </div>
      </header>

      <section className="mx-auto max-w-5xl px-6 py-16">
        <h2 className="text-2xl font-bold text-navy">Practice areas</h2>
        <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {(page.practiceAreas || []).map((a) => (
            <div key={a} className="rounded-xl border p-5 shadow-card">
              <p className="font-semibold">{a}</p>
              <p className="mt-1 text-sm text-slate-500">Counsel, filings, and negotiation in {a.toLowerCase()}.</p>
            </div>
          ))}
        </div>
      </section>

      <section className="bg-slate-50 py-16">
        <div className="mx-auto max-w-5xl px-6">
          <h2 className="text-2xl font-bold text-navy">Attorneys</h2>
          <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {page.attorneys.map((a) => (
              <div key={a.id} className="rounded-xl bg-white p-5 shadow-card">
                <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-navy text-sm font-bold text-white">
                  {a.initials}
                </div>
                <p className="font-semibold">{a.fullName}</p>
                <p className="text-sm text-slate-500">{a.title || a.role}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {page.aboutText && (
        <section className="mx-auto max-w-3xl px-6 py-16 text-slate-600">
          <h2 className="mb-4 text-2xl font-bold text-navy">About the firm</h2>
          <p>{page.aboutText}</p>
        </section>
      )}

      <section id="intake" className="mx-auto max-w-xl px-6 py-16">
        <h2 className="text-2xl font-bold text-navy">Free consultation</h2>
        <p className="mt-2 text-sm text-slate-500">
          {t.addressLine1} {t.city} {t.state} {t.zip} · {t.phone} · {t.email}
        </p>
        <ErrorBanner error={error} />
        {sent ? (
          <p className="mt-6 rounded-xl bg-emerald-50 p-4 text-emerald-800">{sent}</p>
        ) : (
          <form onSubmit={submit} className="mt-6 space-y-3">
            <div>
              <Label>Name</Label>
              <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
            </div>
            <div>
              <Label>Email</Label>
              <Input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
            </div>
            <div>
              <Label>Phone</Label>
              <Input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
            </div>
            <div>
              <Label>Matter type</Label>
              <Select value={form.caseType} onChange={(e) => setForm({ ...form, caseType: e.target.value })}>
                {(page.practiceAreas || ["General"]).map((a) => (
                  <option key={a}>{a}</option>
                ))}
              </Select>
            </div>
            <div>
              <Label>Who is on the other side?</Label>
              <Input value={form.opposingParty} onChange={(e) => setForm({ ...form, opposingParty: e.target.value })} placeholder="Person, company, or agency" />
            </div>
            <div>
              <Label>When did it happen?</Label>
              <Input type="date" value={form.accrualDate} onChange={(e) => setForm({ ...form, accrualDate: e.target.value })} />
            </div>
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={form.governmentalDefendant}
                onChange={(e) => setForm({ ...form, governmentalDefendant: e.target.checked })}
              />
              {page.tenant?.country === "ZA"
                ? "The other side is an organ of state (municipality, SAPS, department, metro)"
                : "The other side is a city, county, school, or transit agency"}
            </label>
            <div>
              <Label>What happened?</Label>
              <Textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} required />
            </div>
            <Button type="submit" className="w-full">
              Submit intake
            </Button>
          </form>
        )}
      </section>
      <footer className="border-t py-6 text-center text-xs text-slate-400">
        © {new Date().getFullYear()} {t.firmName}. Site generated by LegalSuite Pro.
      </footer>
    </div>
  );
}
