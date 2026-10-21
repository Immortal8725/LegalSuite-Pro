"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { apiGet, apiPost, apiPut, api } from "@/lib/api";
import { PageHeader } from "@/components/page";
import { Badge, Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import { Catalog } from "@/lib/directory";

type Practitioner = { id: string; fullName: string; lpcNumber: string; fidelityExpiresOn?: string; fidelityCurrent: boolean; status: string; verificationNote?: string | null };
type Profile = {
  practiceAreas: string[];
  cities: string[];
  languages: string[];
  consultOptions: string[];
  fixedConsultFee?: number | null;
  responseTime?: string | null;
  bio: string;
  listedSwitch: boolean;
  listed: boolean;
  reasons: string[];
  slug: string;
  practitioners: Practitioner[];
  catalog: Catalog;
};

function Checks({ options, value, onChange, name }: { options: { slug: string; label: string }[]; value: string[]; onChange: (v: string[]) => void; name: string }) {
  return (
    <div className="grid gap-1 sm:grid-cols-2">
      {options.map((o) => (
        <label key={o.slug} className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            name={name}
            checked={value.includes(o.slug)}
            onChange={(e) => onChange(e.target.checked ? [...value, o.slug] : value.filter((v) => v !== o.slug))}
          />
          {o.label}
        </label>
      ))}
    </div>
  );
}

export default function DirectoryListingSettings() {
  const [p, setP] = useState<Profile | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);
  const [np, setNp] = useState({ fullName: "", lpcNumber: "", fidelityExpiresOn: "" });

  const load = useCallback(() => {
    apiGet<Profile>("/api/v1/directory/profile")
      .then(setP)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : "Could not load your listing."));
  }, []);
  useEffect(load, [load]);

  async function save(listed?: boolean) {
    if (!p) return;
    setError(null);
    setSaved(null);
    try {
      const r = await apiPut<Profile>("/api/v1/directory/profile", {
        practiceAreas: p.practiceAreas,
        cities: p.cities,
        languages: p.languages,
        consultOptions: p.consultOptions,
        fixedConsultFee: p.fixedConsultFee ?? null,
        responseTime: p.responseTime || null,
        bio: p.bio,
        listed: listed ?? p.listedSwitch,
      });
      setP(r);
      setSaved("Saved.");
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Could not save.");
    }
  }

  async function addPractitioner(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      setP(await apiPost<Profile>("/api/v1/directory/practitioners", np));
      setNp({ fullName: "", lpcNumber: "", fidelityExpiresOn: "" });
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not add the practitioner.");
    }
  }

  async function remove(id: string) {
    try {
      await api(`/api/v1/directory/practitioners/${id}`, { method: "DELETE" });
      load();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not remove.");
    }
  }

  if (!p) return error ? <ErrorBanner error={error} /> : <p className="text-sm text-slate-600">Loading…</p>;
  const c = p.catalog;

  return (
    <div>
      <PageHeader
        title="Find a Lawyer listing"
        subtitle="List the firm on owlaw.co.za/find-a-lawyer. Free with your plan: no per-enquiry fees and no ratings. Enquiries arrive in the Hire pipeline with a conflict check already run."
      />
      <ErrorBanner error={error} />
      <Card className="mb-4">
        <CardBody className="space-y-2 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={p.listed ? "green" : "gold"}>{p.listed ? "Listed" : "Not listed"}</Badge>
            {p.listed ? (
              <Link href={`/find-a-lawyer/${p.slug}`} target="_blank" className="font-semibold underline">
                View your public page
              </Link>
            ) : null}
          </div>
          {p.reasons.length ? (
            <ul className="list-disc pl-5 text-slate-600">
              {p.reasons.map((r) => (
                <li key={r}>{r}</li>
              ))}
            </ul>
          ) : null}
          <div className="flex gap-2">
            {p.listedSwitch ? (
              <Button variant="outline" onClick={() => save(false)}>
                Turn the listing off
              </Button>
            ) : (
              <Button onClick={() => save(true)}>Turn the listing on</Button>
            )}
          </div>
        </CardBody>
      </Card>

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>Profile</CardTitle>
        </CardHeader>
        <CardBody className="space-y-5">
          <div>
            <Label>Practice areas (up to 6)</Label>
            <Checks name="area" options={c.practiceAreas} value={p.practiceAreas} onChange={(v) => setP({ ...p, practiceAreas: v })} />
          </div>
          <div>
            <Label>Cities and towns you serve</Label>
            <Checks name="city" options={c.cities} value={p.cities} onChange={(v) => setP({ ...p, cities: v })} />
          </div>
          <div>
            <Label>Languages</Label>
            <Checks name="language" options={c.languages} value={p.languages} onChange={(v) => setP({ ...p, languages: v })} />
          </div>
          <div>
            <Label>Consultations</Label>
            <Checks name="consult" options={c.consultOptions} value={p.consultOptions} onChange={(v) => setP({ ...p, consultOptions: v })} />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="flex flex-col gap-1">
              <Label htmlFor="fee">Fixed first consultation fee (R, optional)</Label>
              <Input id="fee" type="number" min="0" value={p.fixedConsultFee ?? ""} onChange={(e) => setP({ ...p, fixedConsultFee: e.target.value === "" ? null : Number(e.target.value) })} />
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="rt">Reply time</Label>
              <Select id="rt" value={p.responseTime || ""} onChange={(e) => setP({ ...p, responseTime: e.target.value || null })}>
                <option value="">Do not show</option>
                {c.responseTimes.map((o) => (
                  <option key={o.slug} value={o.slug}>
                    {o.label}
                  </option>
                ))}
              </Select>
            </div>
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor="bio">About the firm (600 characters)</Label>
            <Textarea id="bio" rows={4} maxLength={600} value={p.bio} onChange={(e) => setP({ ...p, bio: e.target.value })} />
            <span className="text-xs text-slate-500">LPC rules: do not call the firm specialists, experts, the best or leading.</span>
          </div>
          <div className="flex items-center gap-3">
            <Button onClick={() => save()}>Save profile</Button>
            {saved ? <span className="text-sm text-green-700">{saved}</span> : null}
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Practitioners</CardTitle>
        </CardHeader>
        <CardBody className="space-y-3 text-sm">
          <p className="text-slate-600">Owlaw checks each LPC number on the Legal Practice Council register before the firm can be listed.</p>
          {p.practitioners.map((x) => (
            <div key={x.id} className="flex flex-wrap items-center gap-2 border-b pb-2">
              <span className="font-semibold">{x.fullName}</span>
              <span className="font-mono">{x.lpcNumber}</span>
              <Badge tone={x.status === "verified" ? "green" : x.status === "rejected" ? "red" : "gold"}>{x.status === "pending" ? "waiting for Owlaw" : x.status}</Badge>
              <Badge tone={x.fidelityCurrent ? "green" : "red"}>Fidelity Fund {x.fidelityCurrent ? "current" : "expired"}</Badge>
              {x.verificationNote ? <span className="text-slate-500">{x.verificationNote}</span> : null}
              <Button variant="outline" onClick={() => remove(x.id)}>
                Remove
              </Button>
            </div>
          ))}
          <form onSubmit={addPractitioner} className="grid gap-3 sm:grid-cols-4">
            <Input aria-label="Full name" placeholder="Full name" required value={np.fullName} onChange={(e) => setNp({ ...np, fullName: e.target.value })} />
            <Input aria-label="LPC number" placeholder="LPC number" required value={np.lpcNumber} onChange={(e) => setNp({ ...np, lpcNumber: e.target.value })} />
            <Input aria-label="Fidelity Fund certificate expires" type="date" required value={np.fidelityExpiresOn} onChange={(e) => setNp({ ...np, fidelityExpiresOn: e.target.value })} />
            <Button type="submit">Add practitioner</Button>
          </form>
        </CardBody>
      </Card>
    </div>
  );
}
