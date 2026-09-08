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

type Instrument = { id?: string; status?: string; signUrl?: string; documentHash?: string; signatureHash?: string };

type RetainResult = {
  blocked?: boolean;
  waiverRequired?: boolean;
  waiverSigned?: boolean;
  reason?: string;
  conflict?: ConflictHit;
  signUrl?: string;
  message?: string;
  leadId?: string;
  limited?: boolean;
  matter?: { id: string; caseNumber?: string; title?: string; solCitation?: string; statuteOfLimitations?: string };
  docket?: DocketPreview;
  waiver?: Instrument;
  trust?: { pledged?: boolean; posted?: boolean; amount?: number; note?: string };
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

  async function issueWaiver(id: string) {
    setBusy(id);
    setError(null);
    try {
      const res = await apiPost<RetainResult>(`/api/v1/retain/${id}/waiver`, {});
      setResult({
        blocked: true,
        waiverRequired: true,
        leadId: id,
        message: res.message,
        waiver: res,
        conflict: res.conflict,
        reason: res.message,
      });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not issue waiver");
    } finally {
      setBusy(null);
    }
  }

  return (
    <div>
      <PageHeader
        title="Hire pipeline"
        subtitle="Conflict waiver is a signed letter. Unsigned engagement is a limited file — no appearance, retainer pledged not posted."
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
          {result.waiver?.signUrl && (
            <p className="mt-2">
              Waiver {result.waiver.status}:{" "}
              <Link className="font-semibold underline" href={result.waiver.signUrl} target="_blank">
                Open instrument
              </Link>
              {result.waiver.signatureHash && (
                <span className="mt-1 block font-mono text-[11px]">hash {result.waiver.signatureHash.slice(0, 16)}…</span>
              )}
            </p>
          )}
          <div className="mt-3 flex flex-wrap gap-2">
            {result.leadId && !result.waiverSigned && result.waiver?.status !== "signed" && (
              <Button variant="danger" onClick={() => result.leadId && issueWaiver(result.leadId)}>
                Issue conflict waiver
              </Button>
            )}
            {result.leadId && (result.waiverSigned || result.waiver?.status === "signed") && (
              <Button onClick={() => result.leadId && retain(result.leadId, true)}>Retain with signed waiver</Button>
            )}
          </div>
        </div>
      )}
      {result && !result.blocked && (
        <div className="my-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
          <p className="font-semibold">{result.message}</p>
          {result.limited && <p className="mt-1 font-medium">File is limited until the engagement is signed.</p>}
          {result.matter && (
            <p className="mt-1">
              Matter{" "}
              <Link className="font-semibold underline" href={`/cases/${result.matter.id}`}>
                {result.matter.caseNumber}
              </Link>
            </p>
          )}
          {result.trust?.note && <p className="mt-1">{result.trust.note}</p>}
          {result.docket?.solDate && (
            <p className="mt-1">
              Texas clock: {result.docket.controllingCitation || "SOL"} · {formatDate(result.docket.solDate)}
            </p>
          )}
          {result.signUrl && (
            <Link className="mt-2 inline-block font-semibold underline" href={result.signUrl} target="_blank">
              Sign the engagement
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
                      </p>
                    )}
                    {l.waiver && (
                      <p className="mt-1 text-[11px]">
                        Waiver <StatusBadge status={l.waiver.status} />{" "}
                        <Link className="underline" href={l.waiver.signUrl || "#"} target="_blank">
                          instrument
                        </Link>
                      </p>
                    )}
                    {l.engagement && (
                      <p className="mt-1 text-[11px]">
                        Engagement <StatusBadge status={l.engagement.status} />{" "}
                        <Link className="underline" href={l.engagement.signUrl || "#"} target="_blank">
                          sign
                        </Link>
                      </p>
                    )}
                    {l.caseId && (
                      <Link className="mt-1 block text-[11px] font-semibold text-navy" href={`/cases/${l.caseId}`}>
                        Open limited file
                      </Link>
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
                      <Button className="mt-2 w-full" size="sm" disabled={busy === l.id} onClick={() => retain(l.id)}>
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
