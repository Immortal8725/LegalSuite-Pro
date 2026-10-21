"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page";
import { Badge, Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input, Label, Select } from "@/components/ui/input";

type Practitioner = { id: string; fullName: string; lpcNumber: string; status: string; fidelityCurrent: boolean };
type Listing = {
  tenantId: string;
  firmName: string;
  slug: string;
  plan: string;
  listedSwitch: boolean;
  listed: boolean;
  reasons: string[];
  operatorHidden: boolean;
  hiddenReason?: string | null;
  practiceAreas: string[];
  cities: string[];
  languages: string[];
  practitioners: Practitioner[];
  last30Days: { profileViews: number; searchAppearances: number; enquiries: number };
};

export default function DirectoryListingsPage() {
  const { user } = useAuth();
  const [rows, setRows] = useState<Listing[]>([]);
  const [show, setShow] = useState("all");
  const [q, setQ] = useState("");
  const [reasons, setReasons] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    apiGet<Listing[]>("/api/v1/platform/directory/listings")
      .then(setRows)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : "Could not load listings."))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (user?.role === "superadmin") load();
    else setLoading(false);
  }, [user, load]);

  async function visibility(row: Listing, hidden: boolean) {
    setError(null);
    try {
      await apiPost(`/api/v1/platform/directory/listings/${row.tenantId}/visibility`, { hidden, reason: reasons[row.tenantId] || "" });
      load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Could not save.");
    }
  }

  if (user?.role !== "superadmin") {
    return (
      <div>
        <PageHeader title="Directory listings" subtitle="This screen is for the Owlaw operator." />
        <p className="text-sm text-slate-600">Sign in as the platform operator to manage Find a Lawyer.</p>
      </div>
    );
  }

  const filtered = rows.filter((r) => {
    if (show === "listed" && !r.listed) return false;
    if (show === "not-listed" && (r.listed || r.operatorHidden)) return false;
    if (show === "hidden" && !r.operatorHidden) return false;
    const s = q.trim().toLowerCase();
    return !s || r.firmName.toLowerCase().includes(s) || r.slug.includes(s);
  });
  const listedCount = rows.filter((r) => r.listed).length;
  const pending = rows.reduce((n, r) => n + r.practitioners.filter((p) => p.status === "pending").length, 0);

  return (
    <div>
      <PageHeader
        title="Directory listings"
        subtitle="Every firm that has opened a Find a Lawyer profile. Hide a listing when there is a complaint or a problem with the LPC check. The firm sees your reason."
      />
      <ErrorBanner error={error} />
      <div className="mb-4 flex flex-wrap items-end gap-4">
        <div className="flex flex-col gap-1">
          <Label htmlFor="show">Show</Label>
          <Select id="show" value={show} onChange={(e) => setShow(e.target.value)}>
            <option value="all">All profiles</option>
            <option value="listed">Listed now</option>
            <option value="not-listed">Not listed yet</option>
            <option value="hidden">Hidden by Owlaw</option>
          </Select>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="q">Firm</Label>
          <Input id="q" placeholder="Name or slug" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <p className="text-sm text-slate-600">
          {listedCount} listed of {rows.length} profiles ·{" "}
          <Link href="/platform/directory" className="font-semibold underline">
            {pending} practitioners waiting for an LPC check
          </Link>{" "}
          ·{" "}
          <a href="/find-a-lawyer" target="_blank" rel="noreferrer" className="underline">
            Open the public directory
          </a>
        </p>
      </div>
      {loading ? <p className="text-sm text-slate-600">Loading…</p> : null}
      {!loading && filtered.length === 0 ? <p className="text-sm text-slate-600">Nothing here.</p> : null}
      <div className="space-y-4">
        {filtered.map((r) => (
          <Card key={r.tenantId} data-testid="listing-row">
            <CardHeader>
              <CardTitle>
                {r.firmName} <span className="font-normal text-slate-500">· {r.slug}</span>
              </CardTitle>
            </CardHeader>
            <CardBody className="space-y-3 text-sm text-slate-700">
              <div className="flex flex-wrap gap-2">
                <Badge tone={r.listed ? "green" : r.operatorHidden ? "red" : "gold"}>{r.listed ? "Listed" : r.operatorHidden ? "Hidden by Owlaw" : "Not listed"}</Badge>
                <Badge tone="slate">Plan {r.plan}</Badge>
                <Badge tone={r.listedSwitch ? "blue" : "slate"}>Firm switch {r.listedSwitch ? "on" : "off"}</Badge>
              </div>
              <p>
                {r.practiceAreas.join(", ") || "No practice areas"} · {r.cities.join(", ") || "No city"} · {r.languages.join(", ")}
              </p>
              <p>
                Practitioners:{" "}
                {r.practitioners.length === 0
                  ? "none"
                  : r.practitioners.map((p) => `${p.fullName} (${p.lpcNumber}, ${p.status}${p.fidelityCurrent ? "" : ", Fidelity Fund expired"})`).join("; ")}
              </p>
              <p className="text-slate-500">
                Last 30 days: {r.last30Days.searchAppearances} search appearances, {r.last30Days.profileViews} profile views, {r.last30Days.enquiries} enquiries
              </p>
              {r.reasons.length ? (
                <ul className="list-disc pl-5 text-slate-600">
                  {r.reasons.map((x) => (
                    <li key={x}>{x}</li>
                  ))}
                </ul>
              ) : null}
              {r.operatorHidden ? (
                <Button variant="outline" onClick={() => visibility(r, false)}>
                  Restore listing
                </Button>
              ) : (
                <div className="flex flex-wrap items-center gap-2">
                  <Input
                    aria-label={`Reason to hide ${r.firmName}`}
                    placeholder="Reason the firm will see"
                    className="max-w-md"
                    value={reasons[r.tenantId] || ""}
                    onChange={(e) => setReasons({ ...reasons, [r.tenantId]: e.target.value })}
                  />
                  <Button variant="outline" onClick={() => visibility(r, true)}>
                    Hide listing
                  </Button>
                  {r.listed ? (
                    <a href={`/find-a-lawyer/${r.slug}`} target="_blank" rel="noreferrer" className="font-semibold underline">
                      View public page
                    </a>
                  ) : null}
                </div>
              )}
            </CardBody>
          </Card>
        ))}
      </div>
    </div>
  );
}
