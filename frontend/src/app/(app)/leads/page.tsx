"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { apiGet, apiPatch, apiPost } from "@/lib/api";
import { Button, PageHeader, StatusBadge } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { PrivilegeStrip } from "@/components/privilege-strip";
import type { ConflictHit, DocketPreview, Lead } from "@/lib/types";
import { formatDate } from "@/lib/utils";

const STAGES = ["new", "contacted", "consultation", "retained", "declined"];

type RetainResult = {
  blocked?: boolean;
  reason?: string;
  conflict?: ConflictHit;
  signUrl?: string;
  message?: string;
  leadId?: string;
  matter?: { id: string; caseNumber?: string; title?: string; solCitation?: string; statuteOfLimitations?: string };
  docket?: DocketPreview;
};

export default function LeadsPage() {
  const [rows, setRows] = useState<Lead[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [result, setResult] = useState<RetainResult | null>(null);

  const load = () => apiGet<Lead[]>("/api/v1/intake/leads").then(setRows).catch((e) => setError(e.message));
  useEffect(() => {
    load();
  }, []);

  async function retain(id: string, force = false) {
    setBusy(id);
    setError(null);
    try {
      const res = await apiPost<RetainResult>(`/api/v1/retain/${id}`, { retainerAmount: 2500, force });
      setResult(res);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Retain failed");
    } finally {
      setBusy(null);
    }
  }

  return (
    <div>
      <PageHeader
        title="Hire pipeline"
        subtitle="Website consult → party conflict → Texas clocks → engagement → IOLTA. One motion."
      />
      <PrivilegeStrip />
      <ErrorBanner error={error} />
      {result?.blocked && (
        <div className="my-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm">
          <p className="font-semibold text-red-800">{result.reason}</p>
          <p className="mt-1 text-red-700">{result.conflict?.matchCount} hit(s) on “{result.conflict?.searchName}”.</p>
          <ul className="mt-2 list-disc pl-5 text-red-800">
            {(result.conflict?.matches || []).map((m, i) => (
              <li key={i}>
                <span className="font-semibold uppercase">{m.role || m.type}</span>: {m.name}
                {m.detail ? ` — ${m.detail}` : ""}
              </li>
            ))}
          </ul>
          <Button className="mt-3" variant="danger" onClick={() => result.leadId && retain(result.leadId, true)}>
            Written waiver on file — retain anyway
          </Button>
        </div>
      )}
      {result && !result.blocked && (
        <div className="my-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
          <p className="font-semibold">{result.message}</p>
          {result.matter && (
            <p className="mt-1">
              Matter{" "}
              <Link className="font-semibold underline" href={`/cases/${result.matter.id}`}>
                {result.matter.caseNumber}
              </Link>
            </p>
          )}
          {result.docket?.solDate && (
            <p className="mt-1">
              Texas clock: {result.docket.controllingCitation || "SOL"} · {formatDate(result.docket.solDate)}
              {result.docket.controllingKind === "notice" ? " — governmental notice controls" : ""}
            </p>
          )}
          {result.signUrl && (
            <Link className="mt-2 inline-block font-semibold underline" href={result.signUrl} target="_blank">
              Open engagement for signature
            </Link>
          )}
        </div>
      )}
      <div className="mt-4 grid gap-3 overflow-x-auto md:grid-cols-5">
        {STAGES.map((stage) => (
          <Card key={stage} className="min-w-[180px]">
            <CardHeader>
              <CardTitle className="capitalize">{stage}</CardTitle>
            </CardHeader>
            <CardBody className="space-y-2">
              {rows
                .filter((l) => l.status === stage)
                .map((l) => (
                  <div key={l.id} className="rounded-lg border p-3 text-sm">
                    <div className="font-semibold">{l.name}</div>
                    <div className="text-xs text-slate-500">{l.email}</div>
                    <p className="mt-1 text-xs">{l.caseType}</p>
                    {l.opposingParty && <p className="mt-1 text-xs font-medium text-red-800">v. {l.opposingParty}</p>}
                    <p className="mt-1 text-xs text-slate-500">{l.description}</p>
                    {l.docket?.solDate && (
                      <p className="mt-1 text-[11px] text-navy">
                        {l.docket.controllingKind === "notice" ? "TTCA notice" : "SOL"} {formatDate(l.docket.controllingDate || l.docket.solDate)}
                        {l.docket.controllingCitation ? ` · ${l.docket.controllingCitation}` : ""}
                      </p>
                    )}
                    <StatusBadge status={l.status} />
                    <select
                      className="mt-2 h-8 w-full rounded border text-xs"
                      value={l.status}
                      onChange={async (e) => {
                        await apiPatch(`/api/v1/intake/leads/${l.id}`, { status: e.target.value });
                        await load();
                      }}
                    >
                      {STAGES.map((s) => (
                        <option key={s}>{s}</option>
                      ))}
                    </select>
                    {stage !== "retained" && stage !== "declined" && (
                      <Button
                        className="mt-2 w-full"
                        size="sm"
                        disabled={busy === l.id}
                        onClick={() => retain(l.id)}
                      >
                        {busy === l.id ? "Running…" : "Retain"}
                      </Button>
                    )}
                  </div>
                ))}
            </CardBody>
          </Card>
        ))}
      </div>
    </div>
  );
}
