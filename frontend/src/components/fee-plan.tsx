"use client";

import { useCallback, useEffect, useState } from "react";
import { apiGet, apiPost, apiPut } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export type Milestone = {
  id?: string;
  label: string;
  kind: "deposit" | "stage";
  stage?: string | null;
  stageLabel?: string;
  amount: number | string;
  status?: string;
  invoiceId?: string | null;
  payUrl?: string | null;
  caseTitle?: string;
};
type Plan = { milestones: Milestone[]; stages: { id: string; label: string }[]; track?: string | null };

const STATUS: Record<string, string> = {
  pending: "Not yet due",
  invoiced: "Invoice sent",
  held: "Paid, waiting to post to trust",
  paid: "Paid",
};
export const milestoneStatus = (s?: string) => STATUS[s || "pending"] || s || "";

/** Staff: the matter's fee plan. A deposit is fees in advance (into trust, LPA s 86); stage fees bill when the stage is reached. */
export function FeePlan({ caseId }: { caseId: string }) {
  const [plan, setPlan] = useState<Plan | null>(null);
  const [rows, setRows] = useState<Milestone[]>([]);
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    apiGet<Plan>(`/api/v1/cases/${caseId}/milestones`).then((p) => {
      setPlan(p);
      setRows(p.milestones.filter((m) => m.status === "pending"));
    }).catch((e) => setMsg(e.message));
  }, [caseId]);
  useEffect(load, [load]);

  async function run(fn: () => Promise<Plan>, ok: string) {
    setBusy(true); setMsg(null);
    try { const p = await fn(); setPlan(p); setRows(p.milestones.filter((m) => m.status === "pending")); setMsg(ok); }
    catch (e) { setMsg(e instanceof Error ? e.message : "Something went wrong"); }
    finally { setBusy(false); }
  }

  if (!plan) return <p className="text-sm text-slate-600">{msg || "Loading the fee plan…"}</p>;
  const billed = plan.milestones.filter((m) => m.status !== "pending");
  return (
    <div className="space-y-3 text-sm">
      <p className="text-xs text-slate-600">
        A deposit is a fee paid in advance. It goes into your trust account (Legal Practice Act s 86) and stays the client&apos;s money until you invoice earned fees.
        A stage fee is invoiced automatically, with a PayFast link sent to the client&apos;s inbox, when the matter reaches that stage.
      </p>
      {billed.map((m) => (
        <div key={m.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border px-3 py-2">
          <span>{m.label}{m.stageLabel ? ` (${m.stageLabel})` : m.kind === "deposit" ? " (deposit, into trust)" : ""}</span>
          <span className="flex items-center gap-3">
            R {Number(m.amount).toFixed(2)} · {milestoneStatus(m.status)}
            {m.status === "held" && (
              <button type="button" className="text-xs font-semibold text-navy underline" disabled={busy}
                onClick={() => run(() => apiPost<Plan>(`/api/v1/milestones/${m.id}/post-trust`), "Posted to trust.")}>Post to trust</button>
            )}
          </span>
        </div>
      ))}
      {rows.map((m, i) => (
        <div key={m.id || `n${i}`} className="grid gap-2 rounded-lg border p-2 sm:grid-cols-[110px_1fr_1fr_110px_auto]">
          <select aria-label="Kind" className="rounded border px-2" value={m.kind}
            onChange={(e) => setRows(rows.map((r, j) => (j === i ? { ...r, kind: e.target.value as Milestone["kind"] } : r)))}>
            <option value="deposit">Deposit</option>
            <option value="stage">Stage fee</option>
          </select>
          <Input aria-label="Label" value={m.label} onChange={(e) => setRows(rows.map((r, j) => (j === i ? { ...r, label: e.target.value } : r)))} />
          {m.kind === "stage" ? (
            <select aria-label="Stage" className="rounded border px-2" value={m.stage || ""}
              onChange={(e) => setRows(rows.map((r, j) => (j === i ? { ...r, stage: e.target.value } : r)))}>
              <option value="">Choose a stage</option>
              {plan.stages.map((s) => <option key={s.id} value={s.id}>{s.label}</option>)}
            </select>
          ) : (
            <span className="self-center text-xs text-slate-600">Billed when you tap Request</span>
          )}
          <Input aria-label="Amount (R, before VAT on stage fees)" placeholder="Amount (R)" inputMode="decimal" value={String(m.amount)} onChange={(e) => setRows(rows.map((r, j) => (j === i ? { ...r, amount: e.target.value } : r)))} />
          <span className="flex items-center gap-2">
            {m.id && m.kind === "deposit" && (
              <button type="button" className="text-xs font-semibold text-navy underline" disabled={busy}
                onClick={() => run(() => apiPost<Plan>(`/api/v1/milestones/${m.id}/bill`), "Deposit requested. The client has a message with a payment link.")}>Request</button>
            )}
            <button type="button" className="text-xs text-slate-500 underline" onClick={() => setRows(rows.filter((_, j) => j !== i))}>Remove</button>
          </span>
        </div>
      ))}
      {!plan.track && <p className="text-xs text-amber-700">Choose a litigation track for this matter to tie fees to stages.</p>}
      <div className="flex flex-wrap gap-2">
        <Button type="button" size="sm" variant="outline" onClick={() => setRows([...rows, { kind: "deposit", label: "Deposit on fees", amount: "" }])}>Add deposit</Button>
        <Button type="button" size="sm" variant="outline" disabled={!plan.track} onClick={() => setRows([...rows, { kind: "stage", label: "Stage fee", stage: "", amount: "" }])}>Add stage fee</Button>
        <Button type="button" size="sm" disabled={busy}
          onClick={() => run(() => apiPut<Plan>(`/api/v1/cases/${caseId}/milestones`, { milestones: rows }), "Fee plan saved.")}>Save fee plan</Button>
      </div>
      {msg && <p className="text-xs">{msg}</p>}
    </div>
  );
}
