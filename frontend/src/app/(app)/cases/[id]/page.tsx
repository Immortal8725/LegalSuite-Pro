"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { apiGet, apiPatch, apiPost, apiPut, getToken } from "@/lib/api";
import { daysLeftLabel, daysUntil, todayIso } from "@/lib/docket";
import { formatDate, formatDateTime, moneyExact } from "@/lib/utils";
import { OutboundPanel } from "@/components/outbound-panel";
import { Button, PageHeader, StatusBadge } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Select, Textarea } from "@/components/ui/input";
import type { DocketClock, Matter } from "@/lib/types";
import { MatterAssistant } from "@/components/matter-assistant";
import Link from "next/link";

const STATUSES = ["limited", "intake", "open", "pending", "discovery", "mediation", "trial", "settled", "closed"];

function clockDays(clock: DocketClock) {
  if (typeof clock.daysLeft === "number") return clock.daysLeft;
  return daysUntil(clock.date);
}

function isRaf(c: Matter) {
  const blob = `${c.docketTrack || ""} ${c.practiceArea || ""} ${c.caseType || ""}`.toLowerCase();
  return blob.includes("raf") || (c.docketClocks || []).some((clock) => clock.kind === "raf_lodge");
}

function needsNotice(c: Matter) {
  return Boolean(c.governmentalDefendant) || (c.docketClocks || []).some((clock) => clock.kind === "notice");
}

export default function CaseDetailPage() {
  const params = useParams<{ id: string }>();
  const [c, setC] = useState<Matter | null>(null);
  const [note, setNote] = useState("");
  const [status, setStatus] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState<string | null>(null);

  const load = useCallback(() =>
    apiGet<Matter>(`/api/v1/cases/${params.id}`).then((row) => {
      setC(row);
      setStatus(row.status);
    }).catch((e) => {
      setC(null);
      setError(e instanceof Error ? e.message : "Could not load this matter");
    }), [params.id]);

  useEffect(() => {
    load();
  }, [load]);

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

  if (!c) return <p className="text-sm text-slate-500">Loading matter…</p>;

  const trialBlocked = Boolean(c.docketHold);
  const za = c.jurisdiction === "ZA";
  const showLodge = isRaf(c);
  const showServe = needsNotice(c);

  async function saveStatus() {
    if (!c) return;
    if (trialBlocked && status === "trial") {
      setError(c.docketHoldReason || "Trial is disabled while a docket hold is set.");
      return;
    }
    setSaving("status");
    setError(null);
    try {
      await apiPatch(`/api/v1/cases/${c.id}/status`, { status });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not update status");
    } finally {
      setSaving(null);
    }
  }

  async function saveNote() {
    if (!c) return;
    setSaving("note");
    setError(null);
    try {
      await apiPost(`/api/v1/cases/${c.id}/notes`, { title: "Note", body: note });
      setNote("");
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save note");
    } finally {
      setSaving(null);
    }
  }

  async function mark(body: Record<string, unknown>, fail: string) {
    if (!c) return;
    setSaving("outcome");
    setError(null);
    try {
      await apiPut(`/api/v1/cases/${c.id}`, body);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : fail);
    } finally {
      setSaving(null);
    }
  }

  async function downloadFile(id: string, name: string) {
    setError(null);
    try {
      const res = await fetch(`/api/v1/documents/${id}/download`, {
        headers: { Authorization: `Bearer ${getToken()}` },
      });
      if (!res.ok) {
        let message = "Could not download this file";
        try {
          const body = (await res.json()) as { message?: string };
          if (body.message) message = body.message;
        } catch {
          /* the body was not JSON */
        }
        setError(message);
        return;
      }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = name;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not download this file");
    }
  }

  async function closeMatter() {
    if (!c) return;
    setSaving("close");
    setError(null);
    try {
      await apiPatch(`/api/v1/cases/${c.id}/status`, { status: "closed" });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not close matter");
    } finally {
      setSaving(null);
    }
  }

  return (
    <div>
      <PageHeader
        title={c.title}
        subtitle={`${c.caseNumber} · ${c.practiceArea || "Matter"}`}
        actions={
          <div className="flex flex-col items-stretch gap-2 sm:items-end">
            <div className="flex flex-wrap gap-2">
              {showLodge && (
                <Button
                  variant="outline"
                  disabled={saving === "raf"}
                  onClick={async () => {
                    setSaving("raf");
                    setError(null);
                    try {
                      await apiPost(`/api/v1/cases/${c.id}/raf1`);
                      await load();
                    } catch (e) {
                      setError(e instanceof Error ? e.message : "Could not compile RAF 1 pack");
                    } finally {
                      setSaving(null);
                    }
                  }}
                >
                  Compile RAF 1 pack
                </Button>
              )}
              <Select
                value={status}
                onChange={(e) => setStatus(e.target.value)}
                aria-describedby={trialBlocked ? "trial-hold-reason" : undefined}
              >
                {STATUSES.map((s) => {
                  const blocked = s === "trial" && trialBlocked;
                  return (
                    <option key={s} value={s} disabled={blocked}>
                      {blocked ? "trial (disabled)" : s}
                    </option>
                  );
                })}
              </Select>
              <Button disabled={saving === "status"} onClick={saveStatus}>
                {saving === "status" ? "Saving…" : "Update status"}
              </Button>
            </div>
            {trialBlocked && (
              <p id="trial-hold-reason" className="max-w-md text-sm text-red-950">
                Trial is disabled. {c.docketHoldReason || "A statutory notice, RAF lodge, or CCMA referral is overdue."}
              </p>
            )}
          </div>
        }
      />
      <ErrorBanner error={error} />
      <div className="mb-4 grid gap-3 sm:grid-cols-2">
        <div className="rounded-xl border bg-white p-4">
          <p className="text-[11px] font-bold uppercase tracking-wide text-slate-400">Client</p>
          <p className="mt-1 text-lg font-semibold text-navy">{c.clientName || "Client not named"}</p>
        </div>
        <div className="rounded-xl border bg-white p-4">
          <p className="text-[11px] font-bold uppercase tracking-wide text-slate-400">Other side</p>
          <p className="mt-1 text-lg font-semibold text-navy">{c.opposingParty || "No opposing party on the file"}</p>
          {c.opposingCounsel && <p className="mt-1 text-sm text-slate-500">Counsel: {c.opposingCounsel}</p>}
        </div>
      </div>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <StatusBadge status={c.status} />
        {c.engagementStatus && <StatusBadge status={c.engagementStatus} />}
      </div>
      {!c.appearanceAuthorized && (
        <div className="mb-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950">
          <p className="font-semibold">Limited file. No appearance.</p>
          <p className="mt-1">
            The mandate is unsigned. Status cannot move to open or trial. Trust will not post the pledged retainer
            {c.pendingRetainerAmount ? ` (${moneyExact(c.pendingRetainerAmount)})` : ""} until this instrument is signed.
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
          <p className="font-semibold">Docket hold. Do not appear.</p>
          <p className="mt-1">
            {c.docketHoldReason || "A statutory notice, RAF lodge, or CCMA referral is overdue."} Trial is disabled until
            you lodge, serve, or apply for condonation.
          </p>
        </div>
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
              {c.courtName || "None"}
            </p>
            <p>
              <span className="text-slate-400">Judge</span>
              <br />
              {c.judgeName || "None"}
            </p>
            <p>
              <span className="text-slate-400">Client</span>
              <br />
              {c.clientName || "None"}
            </p>
            <p>
              <span className="text-slate-400">Opposing party</span>
              <br />
              {c.opposingParty || "None"}
            </p>
            <p>
              <span className="text-slate-400">Opposing counsel</span>
              <br />
              {c.opposingCounsel || "None"}
            </p>
            <p>
              <span className="text-slate-400">Limitations</span>
              <br />
              {c.solCitation || (za ? "South African docket" : c.jurisdiction === "TX" ? "Texas docket" : "Docket")}
              {c.statuteOfLimitations ? ` · ${formatDate(c.statuteOfLimitations)}` : ""}
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
              <CardTitle>{za ? "Prescription clocks" : c.jurisdiction === "TX" ? "Texas clocks" : "Docket clocks"}</CardTitle>
            </CardHeader>
            <CardBody className="space-y-3 text-sm">
              {(c.docketClocks || []).length === 0 && (
                <p className="text-slate-500">No computed clocks. Add an incident date.</p>
              )}
              {(c.docketClocks || []).map((clock, i) => {
                const left = daysLeftLabel(clockDays(clock));
                return (
                  <div key={i} className="rounded-lg border p-3">
                    <div className="flex items-baseline justify-between gap-2">
                      <p className="text-[11px] font-bold uppercase tracking-wide text-slate-500">{clock.title || clock.kind}</p>
                      {left && <p className="font-mono text-xs font-bold text-navy">{left}</p>}
                    </div>
                    <p className="font-semibold text-navy">{formatDate(clock.date)}</p>
                    <p className="text-xs font-semibold text-slate-600">{clock.citation || "No citation on this clock."}</p>
                    {clock.reason && <p className="mt-1 text-xs">{clock.reason}</p>}
                  </div>
                );
              })}
              {c.solReason && (c.docketClocks || []).length === 0 && <p className="text-xs text-slate-500">{c.solReason}</p>}
            </CardBody>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Mandate and waiver</CardTitle>
            </CardHeader>
            <CardBody className="space-y-3 text-sm">
              {c.engagementSignatureId ? (
                <p>
                  Mandate <StatusBadge status={c.engagementStatus || "unsigned"} />{" "}
                  <Link className="font-semibold text-navy underline" href={`/sign/${c.engagementSignatureId}`} target="_blank">
                    Open sign link
                  </Link>
                </p>
              ) : (
                <p className="text-slate-500">No mandate is out for signature on this matter.</p>
              )}
              {c.conflictWaiverSignatureId ? (
                <p>
                  Conflict waiver{" "}
                  <Link className="font-semibold text-navy underline" href={`/sign/${c.conflictWaiverSignatureId}`} target="_blank">
                    Open sign link
                  </Link>
                  {c.conflictWaiverHash && (
                    <span className="mt-1 block font-mono text-[11px] text-slate-500">hash {c.conflictWaiverHash.slice(0, 16)}…</span>
                  )}
                </p>
              ) : (
                c.conflictWaiverHash && (
                  <p className="font-mono text-[11px] text-slate-500">Conflict waiver hash {c.conflictWaiverHash}</p>
                )
              )}
            </CardBody>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Outcomes</CardTitle>
            </CardHeader>
            <CardBody className="space-y-2">
              {showLodge && (
                c.rafClaimLodged ? (
                  <p className="text-sm text-slate-600">RAF claim lodged{c.rafLodgedDate ? ` on ${formatDate(c.rafLodgedDate)}` : ""}.</p>
                ) : (
                  <Button
                    className="w-full"
                    variant="outline"
                    disabled={saving === "outcome"}
                    onClick={() => mark({ rafClaimLodged: true, rafLodgedDate: todayIso() }, "Could not mark the claim lodged")}
                  >
                    Lodge claim
                  </Button>
                )
              )}
              {showServe && (
                c.noticeServed ? (
                  <p className="text-sm text-slate-600">Statutory notice served.</p>
                ) : (
                  <Button
                    className="w-full"
                    variant="outline"
                    disabled={saving === "outcome"}
                    onClick={() => mark({ noticeServed: true }, "Could not mark the notice served")}
                  >
                    Serve notice
                  </Button>
                )
              )}
              {c.status !== "closed" && c.status !== "settled" && (
                <Button className="w-full" variant="outline" disabled={saving === "close"} onClick={closeMatter}>
                  {saving === "close" ? "Closing…" : "Close matter"}
                </Button>
              )}
              {trialBlocked && (
                <p className="text-xs text-slate-600">
                  {c.docketHoldReason} Trial stays disabled while this hold stands. You can still close the file.
                </p>
              )}
            </CardBody>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Add note</CardTitle>
            </CardHeader>
            <CardBody>
              <Textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder="Hearing prep, call recap, strategy…" />
              <Button className="mt-3 w-full" disabled={saving === "note"} onClick={saveNote}>
                {saving === "note" ? "Saving…" : "Save note"}
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
                  <button
                    type="button"
                    className="text-xs font-semibold text-navy underline"
                    onClick={() => downloadFile(d.id, d.name)}
                  >
                    Download
                  </button>
                </div>
              ))}
            </CardBody>
          </Card>
        </div>
      </div>
      <div className="mt-6">
        <MatterAssistant caseId={c.id} matterLabel={`${c.caseNumber} · ${c.title}`} />
        <div className="mt-6">
          <OutboundPanel
            spread
            lockMatter
            matterId={c.id}
            matterLabel={`${c.caseNumber} ${c.title}`}
            clientId={c.clientId}
            defaultPhone={c.clientPhone || ""}
            defaultEmail={c.clientEmail || ""}
          />
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
