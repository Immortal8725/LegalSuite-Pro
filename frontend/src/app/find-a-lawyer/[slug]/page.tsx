"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { FirmPage, publicFetch } from "@/lib/directory";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { TRIAGE_STORAGE_KEY } from "@/components/home-triage";

export default function FirmListingPage() {
  const { slug } = useParams<{ slug: string }>();
  const [firm, setFirm] = useState<FirmPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState({ name: "", email: "", phone: "", practiceArea: "", opposingParty: "", message: "", website: "" });
  const [consent, setConsent] = useState(false);
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  useEffect(() => {
    publicFetch<FirmPage>(`/api/v1/public/directory/firms/${encodeURIComponent(slug)}`)
      .then(setFirm)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : "Could not load this firm."));
  }, [slug]);

  // Pre-fill from the home page "Tell us what happened" box (kept only in this browser tab).
  useEffect(() => {
    if (!firm) return;
    try {
      const saved = JSON.parse(sessionStorage.getItem(TRIAGE_STORAGE_KEY) || "null") as { text?: string; area?: string } | null;
      if (!saved?.text) return;
      const area = firm.practiceAreaOptions.some((o) => o.slug === saved.area) ? saved.area || "" : "";
      setForm((f) => ({ ...f, message: f.message || saved.text!.slice(0, 2000), practiceArea: f.practiceArea || area }));
    } catch {
      /* ignore */
    }
  }, [firm]);

  async function send(e: React.FormEvent) {
    e.preventDefault();
    setSendError(null);
    setSending(true);
    try {
      const r = await publicFetch<{ message: string }>(`/api/v1/public/directory/firms/${encodeURIComponent(slug)}/enquiries`, {
        method: "POST",
        body: JSON.stringify({ ...form, consent }),
      });
      setDone(r.message);
    } catch (err: unknown) {
      setSendError(err instanceof Error ? err.message : "Could not send your enquiry.");
    } finally {
      setSending(false);
    }
  }

  if (error) {
    return (
      <div>
        <p className="rounded-xl border bg-white p-5 text-slate-700">{error}</p>
        <Link href="/find-a-lawyer" className="mt-4 inline-block font-semibold text-navy underline">
          Back to Find a Lawyer
        </Link>
      </div>
    );
  }
  if (!firm) return <p className="text-sm text-slate-600">Loading…</p>;

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_420px]">
      <div>
        <Link href="/find-a-lawyer" className="text-sm font-semibold text-slate-600 hover:text-navy">
          ← Back to search
        </Link>
        <h1 className="mt-3 text-3xl font-extrabold text-navy">{firm.firmName}</h1>
        <p className="mt-1 text-slate-600">
          {firm.cities.join(", ")}
          {firm.provinces.length ? ` · ${firm.provinces.join(", ")}` : ""}
        </p>
        {firm.bio ? <p className="mt-4 whitespace-pre-line text-slate-700">{firm.bio}</p> : null}

        <section className="mt-6">
          <h2 className="text-lg font-bold text-navy">Practice areas</h2>
          <p className="mt-1 text-slate-700">{firm.practiceAreas.join(" · ")}</p>
        </section>
        <section className="mt-5">
          <h2 className="text-lg font-bold text-navy">Languages</h2>
          <p className="mt-1 text-slate-700">{firm.languages.join(", ")}</p>
        </section>
        {firm.consultOptions.length ? (
          <section className="mt-5">
            <h2 className="text-lg font-bold text-navy">Consultations</h2>
            <ul className="mt-1 list-disc pl-5 text-slate-700">
              {firm.consultOptions.map((c) => (
                <li key={c}>{c}</li>
              ))}
            </ul>
            {firm.fixedConsultFee ? <p className="mt-1 text-sm text-slate-600">First consultation fee: R{Number(firm.fixedConsultFee).toFixed(2)}</p> : null}
            {firm.responseTime ? <p className="mt-1 text-sm text-slate-600">{firm.responseTime}</p> : null}
          </section>
        ) : null}

        <section className="mt-6 rounded-2xl border bg-white p-5" data-testid="practitioners">
          <h2 className="text-lg font-bold text-navy">LPC-verified practitioners</h2>
          <ul className="mt-2 space-y-2">
            {firm.practitioners.map((p) => (
              <li key={p.lpcNumber} className="text-slate-700">
                <span className="font-semibold">{p.fullName}</span> · LPC number <span className="font-mono">{p.lpcNumber}</span>
                {p.verifiedOn ? <span className="text-sm text-slate-500"> (checked by Owlaw on {p.verifiedOn})</span> : null}
              </li>
            ))}
          </ul>
          <p className="mt-3 text-xs text-slate-500">
            You can confirm any number yourself on the{" "}
            <a href={firm.lpcRegisterUrl} target="_blank" rel="noreferrer" className="underline">
              Legal Practice Council
            </a>{" "}
            site.
          </p>
        </section>
        {firm.publicSite ? (
          <p className="mt-4 text-sm">
            <Link href={firm.publicSite} className="font-semibold text-navy underline">
              Visit the firm&apos;s website
            </Link>
          </p>
        ) : null}
        {firm.phone ? <p className="mt-2 text-sm text-slate-700">Phone: {firm.phone}</p> : null}
      </div>

      <aside className="h-fit rounded-2xl border bg-white p-5 shadow-sm">
        <h2 className="text-lg font-bold text-navy">Send an enquiry</h2>
        {done ? (
          <p className="mt-3 rounded-lg bg-green-50 p-3 text-green-800" data-testid="enquiry-done">
            {done}
          </p>
        ) : (
          <form onSubmit={send} className="mt-3 space-y-3" aria-label="Enquiry form">
            <p className="text-sm text-slate-600">The firm gets this directly. Do not send documents or ID numbers here.</p>
            <div className="flex flex-col gap-1">
              <Label htmlFor="name">Your name</Label>
              <Input id="name" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="phone">Phone</Label>
              <Input id="phone" type="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="practiceArea">What is it about?</Label>
              <Select id="practiceArea" value={form.practiceArea} onChange={(e) => setForm({ ...form, practiceArea: e.target.value })}>
                <option value="">Not sure</option>
                {firm.practiceAreaOptions.map((o) => (
                  <option key={o.slug} value={o.slug}>
                    {o.label}
                  </option>
                ))}
              </Select>
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="opposingParty">Other party, if any</Label>
              <Input
                id="opposingParty"
                placeholder="Person or company on the other side"
                value={form.opposingParty}
                onChange={(e) => setForm({ ...form, opposingParty: e.target.value })}
              />
              <span className="text-xs text-slate-500">The firm uses this to check it can act for you.</span>
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="message">Your message</Label>
              <Textarea id="message" required rows={5} maxLength={2000} value={form.message} onChange={(e) => setForm({ ...form, message: e.target.value })} />
            </div>
            <div className="hidden" aria-hidden="true">
              <label htmlFor="website">Website</label>
              <input id="website" tabIndex={-1} autoComplete="off" value={form.website} onChange={(e) => setForm({ ...form, website: e.target.value })} />
            </div>
            <label className="flex items-start gap-2 text-sm text-slate-700">
              <input type="checkbox" className="mt-1" checked={consent} onChange={(e) => setConsent(e.target.checked)} data-testid="consent" />
              <span>{firm.consentText}</span>
            </label>
            {sendError ? <p className="rounded-lg bg-red-50 p-2 text-sm text-red-700">{sendError}</p> : null}
            <Button type="submit" disabled={sending || !consent} className="w-full">
              {sending ? "Sending…" : "Send enquiry"}
            </Button>
            <p className="text-xs text-slate-500">Free for you. Owlaw does not charge the firm per enquiry and does not take a share of fees.</p>
          </form>
        )}
      </aside>
    </div>
  );
}
