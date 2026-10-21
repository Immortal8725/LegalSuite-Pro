"use client";

import { useCallback, useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page";
import { Badge } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input, Label, Select } from "@/components/ui/input";
import { fmtDate, OperatorOnly } from "@/components/platform";

type Firm = {
  id: string;
  firmName: string;
  slug: string;
  email?: string;
  city?: string;
  planSlug?: string;
  planName: string;
  status: string;
  seats: number;
  createdAt?: string;
  trialEndsAt?: string;
  trialExpired: boolean;
  lastActivityAt?: string;
  suspendedReason?: string;
  suspendedAt?: string;
};
type PlanOption = { slug: string; name: string; priceMonthly: number };
type Action = "suspend" | "reactivate" | "extend-trial" | "plan";

const tone = (s: string) => (s === "active" ? "green" : s === "trial" ? "blue" : s === "suspended" ? "red" : "slate");

export default function PlatformFirmsPage() {
  const { user } = useAuth();
  const [firms, setFirms] = useState<Firm[]>([]);
  const [plans, setPlans] = useState<PlanOption[]>([]);
  const [q, setQ] = useState("");
  const [show, setShow] = useState("all");
  const [open, setOpen] = useState<{ id: string; action: Action } | null>(null);
  const [reason, setReason] = useState("");
  const [days, setDays] = useState("14");
  const [planSlug, setPlanSlug] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    Promise.all([apiGet<Firm[]>("/api/v1/platform/firms"), apiGet<PlanOption[]>("/api/v1/platform/plans")])
      .then(([f, p]) => {
        setFirms(f);
        setPlans(p);
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : "Could not load firms."))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (user?.role === "superadmin") load();
    else setLoading(false);
  }, [user, load]);

  async function submit(firm: Firm) {
    if (!open) return;
    setError(null);
    setNotice(null);
    const body: Record<string, unknown> = { reason };
    if (open.action === "extend-trial") body.days = Number(days);
    if (open.action === "plan") body.planSlug = planSlug;
    try {
      await apiPost(`/api/v1/platform/firms/${firm.id}/${open.action}`, body);
      setNotice(`${firm.firmName}: saved.`);
      setOpen(null);
      setReason("");
      load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Could not save.");
    }
  }

  if (user?.role !== "superadmin") return <OperatorOnly title="Firms" />;

  const s = q.trim().toLowerCase();
  const rows = firms.filter(
    (f) => (show === "all" || f.status === show) && (!s || f.firmName.toLowerCase().includes(s) || f.slug.includes(s) || (f.email || "").toLowerCase().includes(s))
  );

  return (
    <div>
      <PageHeader
        title="Firms"
        subtitle="Every firm on Owlaw. Suspend, reactivate, extend a trial or change a plan. Each change needs a reason and is logged for Owlaw and for the firm. This screen shows no client or matter data."
      />
      <ErrorBanner error={error} />
      {notice ? <p className="mb-3 rounded-lg bg-green-50 p-2 text-sm text-green-800">{notice}</p> : null}
      <div className="mb-4 flex flex-wrap items-end gap-4">
        <div className="flex flex-col gap-1">
          <Label htmlFor="q">Search</Label>
          <Input id="q" placeholder="Name, slug or email" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="show">Status</Label>
          <Select id="show" value={show} onChange={(e) => setShow(e.target.value)}>
            <option value="all">All</option>
            <option value="active">Active</option>
            <option value="trial">Trial</option>
            <option value="suspended">Suspended</option>
          </Select>
        </div>
        <p className="text-sm text-slate-600">{rows.length} of {firms.length} firms</p>
      </div>
      {loading ? <p className="text-sm text-slate-600">Loading…</p> : null}
      <div className="overflow-x-auto rounded-xl border bg-white">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
            <tr>
              <th className="p-3">Firm</th>
              <th className="p-3">Plan</th>
              <th className="p-3">Status</th>
              <th className="p-3">Seats</th>
              <th className="p-3">Created</th>
              <th className="p-3">Trial ends</th>
              <th className="p-3">Last sign-in</th>
              <th className="p-3">Actions</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((f) => (
              <tr key={f.id} className="border-t align-top" data-testid="firm-row">
                <td className="p-3">
                  <div className="font-semibold text-navy">{f.firmName}</div>
                  <div className="text-xs text-slate-500">
                    {f.slug}
                    {f.city ? ` · ${f.city}` : ""}
                  </div>
                  {f.suspendedReason ? <div className="mt-1 text-xs text-red-700">Suspended: {f.suspendedReason}</div> : null}
                </td>
                <td className="p-3">{f.planName}</td>
                <td className="p-3">
                  <Badge tone={tone(f.status)}>{f.status}</Badge>
                  {f.trialExpired ? <div className="mt-1 text-xs text-red-700">Trial over</div> : null}
                </td>
                <td className="p-3">{f.seats}</td>
                <td className="p-3">{fmtDate(f.createdAt)}</td>
                <td className="p-3">{f.status === "trial" ? fmtDate(f.trialEndsAt) : "-"}</td>
                <td className="p-3">{fmtDate(f.lastActivityAt, true)}</td>
                <td className="p-3">
                  <div className="flex flex-wrap gap-1">
                    {f.status === "suspended" ? (
                      <Button variant="outline" onClick={() => setOpen({ id: f.id, action: "reactivate" })}>
                        Reactivate
                      </Button>
                    ) : (
                      <Button variant="outline" onClick={() => setOpen({ id: f.id, action: "suspend" })}>
                        Suspend
                      </Button>
                    )}
                    {f.status === "trial" ? (
                      <Button variant="outline" onClick={() => setOpen({ id: f.id, action: "extend-trial" })}>
                        Extend trial
                      </Button>
                    ) : null}
                    <Button
                      variant="outline"
                      onClick={() => {
                        setPlanSlug(f.planSlug || "");
                        setOpen({ id: f.id, action: "plan" });
                      }}
                    >
                      Change plan
                    </Button>
                  </div>
                  {open?.id === f.id ? (
                    <form
                      className="mt-2 space-y-2 rounded-lg border bg-slate-50 p-3"
                      onSubmit={(e) => {
                        e.preventDefault();
                        submit(f);
                      }}
                    >
                      <p className="font-semibold">
                        {open.action === "suspend"
                          ? "Suspend this firm. Staff and the client portal are blocked until you reactivate it."
                          : open.action === "reactivate"
                            ? "Reactivate this firm."
                            : open.action === "extend-trial"
                              ? "Extend the trial."
                              : "Change the plan."}
                      </p>
                      {open.action === "extend-trial" ? (
                        <Input aria-label="Days" type="number" min="1" max="90" value={days} onChange={(e) => setDays(e.target.value)} />
                      ) : null}
                      {open.action === "plan" ? (
                        <Select aria-label="Plan" value={planSlug} onChange={(e) => setPlanSlug(e.target.value)}>
                          {plans.map((p) => (
                            <option key={p.slug} value={p.slug}>
                              {p.name} (R{p.priceMonthly} a month)
                            </option>
                          ))}
                        </Select>
                      ) : null}
                      <Input aria-label="Reason" placeholder="Reason (required, kept in the audit log)" required minLength={5} value={reason} onChange={(e) => setReason(e.target.value)} />
                      <div className="flex gap-2">
                        <Button type="submit">Confirm</Button>
                        <Button type="button" variant="outline" onClick={() => setOpen(null)}>
                          Cancel
                        </Button>
                      </div>
                    </form>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
