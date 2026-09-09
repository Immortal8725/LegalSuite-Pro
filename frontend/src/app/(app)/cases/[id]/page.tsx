"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { apiGet, apiPatch, apiPost } from "@/lib/api";
import { formatDateTime } from "@/lib/utils";
import { Button, PageHeader, StatusBadge } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Select, Textarea } from "@/components/ui/input";
import type { Matter } from "@/lib/types";
import Link from "next/link";

export default function CaseDetailPage() {
  const params = useParams<{ id: string }>();
  const [c, setC] = useState<Matter | null>(null);
  const [note, setNote] = useState("");
  const [status, setStatus] = useState("");
  const [error, setError] = useState<string | null>(null);

  const load = () =>
    apiGet<Matter>(`/api/v1/cases/${params.id}`).then((row) => {
      setC(row);
      setStatus(row.status);
      setError(null);
    }).catch((e) => {
      setC(null);
      setError(e instanceof Error ? e.message : "Could not load this matter");
    });

  useEffect(() => {
    load();
  }, [params.id]);

  if (error && !c) {
    return (
      <div>
        <ErrorBanner error={error} />
        <Link href="/cases" className="mt-4 inline-block text-sm font-semibold text-navy">
          Back to matters
        </Link>
      </div>
    );
  }

      <PageHeader
        title={c.title}
        subtitle={`${c.caseNumber} · ${c.clientName || "Client"} · ${c.practiceArea || ""}`}
        actions={
          <div className="flex flex-wrap gap-2">
            {(c.docketTrack === "raf" || (c.practiceArea || "").toLowerCase().includes("raf") || (c.caseType || "").toLowerCase().includes("raf")) && (
              <Button
                variant="outline"
                onClick={async () => {
                  try {
                    setError(null);
                    await apiPost(`/api/v1/cases/${c.id}/raf1`);
                    load();
                  } catch (e) {
                    setError(e instanceof Error ? e.message : "Could not compile RAF 1 pack");
                  }
                }}
              >
                Compile RAF 1 pack
              </Button>
            )}
            <Select value={status} onChange={(e) => setStatus(e.target.value)}>
              {["limited", "intake", "open", "pending", "discovery", "mediation", "trial", "settled", "closed"].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </Select>
            <Button
              onClick={async () => {
                await apiPatch(`/api/v1/cases/${c.id}/status`, { status });
                load();
              }}
            >
              Update status
            </Button>
          </div>
        }
      />
      <ErrorBanner error={error} />
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <StatusBadge status={c.status} />
        {c.engagementStatus && <StatusBadge status={c.engagementStatus} />}
      </div>
      {!c.appearanceAuthorized && (
        <div className="mb-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950">
          <p className="font-semibold">Limited file — no appearance.</p>
          <p className="mt-1">
            The mandate is unsigned. Status cannot move to open or trial. Trust will not post the pledged retainer
            {c.pendingRetainerAmount ? ` (${c.pendingRetainerAmount})` : ""} until this instrument is signed.
          </p>
          {c.engagementSignatureId && (
            <Link className="mt-2 inline-block font-semibold underline" href={`/sign/${c.engagementSignatureId}`} target="_blank">
              Open mandate for signature
            </Link>
          )}
        </div>
      )}
      {c.docketHold && (
        <div className="mb-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-950">
          <p className="font-semibold">Docket hold — do not appear.</p>
          <p className="mt-1">{c.docketHoldReason || "A statutory notice, RAF lodge, or CCMA referral is overdue."} Trial status is blocked until you lodge, serve, or apply for condonation.</p>
        </div>
      )}
      {c.conflictWaiverHash && (
        <p className="mb-4 font-mono text-[11px] text-slate-500">Conflict waiver hash {c.conflictWaiverHash}</p>
      )}
      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Overview</CardTitle>
          </CardHeader>
          <CardBody className="grid gap-3 text-sm sm:grid-cols-2">
            <p>
              <span className="text-slate-400">Court</span>
              <br />
              {c.courtName || "—"}
            </p>
            <p>
              <span className="text-slate-400">Judge</span>
              <br />
              {c.judgeName || "—"}
            </p>
            <p>
              <span className="text-slate-400">Opposing party</span>
              <br />
              {c.opposingParty || "—"}
            </p>
            <p>
              <span className="text-slate-400">Opposing counsel</span>
              <br />
              {c.opposingCounsel || "—"}
            </p>
            <p>
              <span className="text-slate-400">Limitations</span>
              <br />
              {c.solCitation || "Texas docket"}
              {c.statuteOfLimitations ? ` · ${c.statuteOfLimitations}` : ""}
            </p>
            <p className="sm:col-span-2">
              <span className="text-slate-400">Assignment</span>
              <br />
              {c.description || "No narrative on file."}
            </p>
          </CardBody>
        </Card>
        <div className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle>Docket clocks</CardTitle>
          </CardHeader>
          <CardBody className="space-y-3 text-sm">
            {(c.docketClocks || []).length === 0 && (
              <p className="text-slate-500">No computed clocks. Add an incident date and practice area, then save.</p>
            )}
            {(c.docketClocks || []).map((clock, i) => (
              <div key={i} className="rounded-lg border p-3">
                <p className="text-[11px] font-bold uppercase tracking-wide text-slate-500">{clock.title || clock.kind}</p>
                <p className="font-semibold text-navy">{clock.date}</p>
                <p className="text-xs text-slate-500">{clock.citation}</p>
                <p className="mt-1 text-xs">{clock.reason}</p>
              </div>
            ))}
            {c.solReason && (c.docketClocks || []).length === 0 && <p className="text-xs text-slate-500">{c.solReason}</p>}
          </CardBody>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Add note</CardTitle>
          </CardHeader>
          <CardBody>
            <Textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder="Hearing prep, call recap, strategy…" />
            <Button
              className="mt-3 w-full"
              onClick={async () => {
                await apiPost(`/api/v1/cases/${c.id}/notes`, { title: "Note", body: note });
                setNote("");
                load();
              }}
            >
              Save note
            </Button>
          </CardBody>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Files</CardTitle>
          </CardHeader>
          <CardBody className="space-y-2 text-sm">
            {(c.documents || []).length === 0 && <p className="text-slate-500">No files on this matter yet.</p>}
            {(c.documents || []).map((d) => (
              <div key={d.id} className="flex items-center justify-between gap-2 rounded-lg border px-3 py-2">
                <div>
                  <p className="font-semibold">{d.name}</p>
                  <p className="text-xs text-slate-500">{d.category}</p>
                </div>
                <a className="text-xs font-semibold text-navy underline" href={`/api/v1/documents/${d.id}/download`}>
                  Download
                </a>
              </div>
            ))}
          </CardBody>
        </Card>
        </div>
      </div>
      <Card className="mt-6">
        <CardHeader>
          <CardTitle>Notes</CardTitle>
        </CardHeader>
        <CardBody className="space-y-4">
          {(c.notes || []).length === 0 && <p className="text-sm text-slate-500">No notes yet.</p>}
          {(c.notes || []).map((n) => (
            <div key={n.id}>
              <p className="text-xs text-slate-400">{formatDateTime(n.createdAt)}</p>
              <p className="font-semibold">{n.title}</p>
              <p className="text-sm">{n.body}</p>
            </div>
          ))}
        </CardBody>
      </Card>
    </div>
  );
}
