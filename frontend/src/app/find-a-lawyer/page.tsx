"use client";

import Link from "next/link";
import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Catalog, publicFetch, SearchResult } from "@/lib/directory";
import { Label, Select } from "@/components/ui/input";
import { Button } from "@/components/ui/button";

const FIELDS = ["area", "province", "city", "language"] as const;
type Field = (typeof FIELDS)[number];

function Search() {
  const router = useRouter();
  const params = useSearchParams();
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [form, setForm] = useState<Record<Field, string>>({ area: "", province: "", city: "", language: "" });
  const [data, setData] = useState<SearchResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const qs = params.toString();

  useEffect(() => {
    publicFetch<Catalog>("/api/v1/public/directory/catalog").then(setCatalog).catch(() => undefined);
  }, []);

  useEffect(() => {
    const next = { area: "", province: "", city: "", language: "" };
    for (const f of FIELDS) next[f] = params.get(f) || "";
    setForm(next);
    setLoading(true);
    setError(null);
    publicFetch<SearchResult>(`/api/v1/public/directory/search${qs ? `?${qs}` : ""}`)
      .then(setData)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : "Search failed."))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [qs]);

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const q = new URLSearchParams();
    for (const f of FIELDS) if (form[f]) q.set(f, form[f]);
    router.push(`/find-a-lawyer${q.toString() ? `?${q}` : ""}`);
  }

  const cities = (catalog?.cities || []).filter((c) => !form.province || c.province === form.province);

  return (
    <div>
      <h1 className="text-3xl font-extrabold text-navy">Find a Lawyer</h1>
      <p className="mt-2 max-w-3xl text-slate-600">
        Search South African law firms by the type of lawyer you need, where you are and the language you want to speak. Every firm listed here has at
        least one practitioner whose Legal Practice Council number Owlaw has checked. Owlaw does not rate firms and firms do not pay per enquiry.
      </p>

      <form onSubmit={submit} className="mt-6 grid gap-4 rounded-2xl border bg-white p-5 shadow-sm sm:grid-cols-2 lg:grid-cols-5" aria-label="Search the directory">
        <div className="flex flex-col gap-1">
          <Label htmlFor="area">Type of lawyer</Label>
          <Select id="area" value={form.area} onChange={(e) => setForm({ ...form, area: e.target.value })}>
            <option value="">Any practice area</option>
            {catalog?.practiceAreas.map((o) => (
              <option key={o.slug} value={o.slug}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="province">Province</Label>
          <Select id="province" value={form.province} onChange={(e) => setForm({ ...form, province: e.target.value, city: "" })}>
            <option value="">Any province</option>
            {catalog?.provinces.map((o) => (
              <option key={o.slug} value={o.slug}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="city">City or town</Label>
          <Select id="city" value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })}>
            <option value="">Any city or town</option>
            {cities.map((o) => (
              <option key={o.slug} value={o.slug}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="language">Language</Label>
          <Select id="language" value={form.language} onChange={(e) => setForm({ ...form, language: e.target.value })}>
            <option value="">Any language</option>
            {catalog?.languages.map((o) => (
              <option key={o.slug} value={o.slug}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>
        <div className="flex items-end">
          <Button type="submit" className="w-full">
            Search
          </Button>
        </div>
      </form>

      {error ? <p className="mt-6 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p> : null}
      {loading ? <p className="mt-6 text-sm text-slate-600">Searching…</p> : null}
      {!loading && data ? (
        <div className="mt-6">
          <p className="text-sm text-slate-600" data-testid="result-count">
            {data.total === 1 ? "1 firm" : `${data.total} firms`} found. {data.order}
          </p>
          {data.total === 0 ? (
            <p className="mt-4 rounded-xl border bg-white p-5 text-slate-700">
              No listed firm matches yet. Try a nearby city or remove a filter. If you cannot afford a lawyer, Legal Aid South Africa can help: call 0800 110 110.
            </p>
          ) : null}
          <ul className="mt-4 grid gap-4 md:grid-cols-2">
            {data.results.map((f) => (
              <li key={f.slug} className="rounded-2xl border bg-white p-5 shadow-sm" data-testid="firm-card">
                <Link href={`/find-a-lawyer/${f.slug}`} className="text-lg font-bold text-navy hover:underline">
                  {f.firmName}
                </Link>
                <p className="mt-1 text-sm text-slate-600">{f.cities.join(", ")}</p>
                <p className="mt-2 text-sm text-slate-700">{f.practiceAreas.join(" · ")}</p>
                <p className="mt-1 text-sm text-slate-500">Languages: {f.languages.join(", ")}</p>
                <div className="mt-3 flex flex-wrap gap-2 text-xs">
                  <span className="rounded-full bg-green-50 px-2.5 py-1 font-semibold text-green-800">
                    {f.verifiedPractitioners} LPC-verified {f.verifiedPractitioners === 1 ? "practitioner" : "practitioners"}
                  </span>
                  {f.consultOptions.slice(0, 3).map((c) => (
                    <span key={c} className="rounded-full bg-slate-100 px-2.5 py-1 text-slate-700">
                      {c}
                    </span>
                  ))}
                </div>
                <Link href={`/find-a-lawyer/${f.slug}`} className="mt-4 inline-block text-sm font-bold text-navy underline">
                  View firm and send an enquiry
                </Link>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      <p className="mt-10 text-xs text-slate-500">
        Owlaw is a directory, not a law firm, and does not give legal advice or recommend one firm over another. Check any practitioner on the{" "}
        <a href="https://lpc.org.za/" target="_blank" rel="noreferrer" className="underline">
          Legal Practice Council
        </a>{" "}
        site.
      </p>
    </div>
  );
}

export default function FindALawyerPage() {
  return (
    <Suspense fallback={<p className="text-sm text-slate-600">Loading…</p>}>
      <Search />
    </Suspense>
  );
}
