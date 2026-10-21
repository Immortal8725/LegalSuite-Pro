"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { apiPost } from "@/lib/api";
import type { PublicSite } from "@/lib/types";
import { href } from "@/components/public-site/site-chrome";
import { useLang } from "@/lib/i18n";

export function EnquiryForm({ site, base }: { site: PublicSite; base: string }) {
  const { t } = useLang();
  const areas = site.practices?.length ? site.practices.map((item) => item.name) : ["General"];
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState<string | null>(null);
  const [form, setForm] = useState({
    name: "",
    email: "",
    phone: "",
    caseType: areas[0],
    description: "",
    opposingParty: "",
    accrualDate: "",
    governmentalDefendant: false,
  });

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      const res = await apiPost<{ message: string }>(`/api/v1/intake/${site.slug}`, form);
      setSent(res.message);
    } catch (err) {
      setError(err instanceof Error ? err.message : t.couldNotSend);
    }
  }

  if (sent) {
    return <p className="border border-[var(--ps-line)] bg-[var(--ps-card)] p-5 text-sm">{sent}</p>;
  }

  const za = site.country === "ZA";

  return (
    <form onSubmit={submit} className="ps-form space-y-4">
      <p className="text-sm text-[var(--ps-body)]">
        {t.formIntro}{" "}
        <Link href={href(base, "/legal/privacy")} className="underline">{t.privacy}</Link>
      </p>
      {error ? <p className="border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</p> : null}
      <label className="block text-sm">
        {t.name}
        <input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="mt-1 w-full border border-[var(--ps-line)] bg-[var(--ps-card)] px-3 py-2" />
      </label>
      <label className="block text-sm">
        {t.email}
        <input required type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} className="mt-1 w-full border border-[var(--ps-line)] bg-[var(--ps-card)] px-3 py-2" />
      </label>
      <label className="block text-sm">
        {t.phone}
        <input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} className="mt-1 w-full border border-[var(--ps-line)] bg-[var(--ps-card)] px-3 py-2" />
      </label>
      <label className="block text-sm">
        {t.matterType}
        <select value={form.caseType} onChange={(e) => setForm({ ...form, caseType: e.target.value })} className="mt-1 w-full border border-[var(--ps-line)] bg-[var(--ps-card)] px-3 py-2">
          {areas.map((area) => (
            <option key={area}>{area}</option>
          ))}
        </select>
      </label>
      <label className="block text-sm">
        {t.otherSide}
        <input value={form.opposingParty} onChange={(e) => setForm({ ...form, opposingParty: e.target.value })} className="mt-1 w-full border border-[var(--ps-line)] bg-[var(--ps-card)] px-3 py-2" />
      </label>
      <label className="block text-sm">
        {t.when}
        <input type="date" value={form.accrualDate} onChange={(e) => setForm({ ...form, accrualDate: e.target.value })} className="mt-1 w-full border border-[var(--ps-line)] bg-[var(--ps-card)] px-3 py-2" />
      </label>
      <label className="flex items-start gap-2 text-sm">
        <input
          type="checkbox"
          checked={form.governmentalDefendant}
          onChange={(e) => setForm({ ...form, governmentalDefendant: e.target.checked })}
          className="mt-1"
        />
        <span>
          {za
            ? t.organOfState
            : "The other side is a city, county, school, or transit agency"}
        </span>
      </label>
      <label className="block text-sm">
        {t.whatHappened}
        <textarea required rows={5} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} className="mt-1 w-full border border-[var(--ps-line)] bg-[var(--ps-card)] px-3 py-2" />
      </label>
      <button type="submit" className="ps-cta px-5 py-3 text-sm font-semibold">
        {t.send}
      </button>
    </form>
  );
}
