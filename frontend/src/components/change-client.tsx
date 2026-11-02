"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { moneyExact } from "@/lib/utils";
import { useAuth } from "@/components/auth-provider";
import { Button } from "@/components/ui/button";
import { Dialog, ErrorBanner } from "@/components/ui/dialog";
import { Label, Select, Textarea } from "@/components/ui/input";
import { ADMIN_ROLES } from "@/lib/litigation";
import type { Party } from "@/lib/types";

type Preview = {
  counts: Record<string, number>;
  trust: { trustAccountId: string; accountName: string; amount: number }[];
  conflict: { relevantMatches: number; matches: { role?: string; name?: string; caseNumber?: string }[] };
  fica: { status: string; complete: boolean; missing?: string[] };
  mandate: { current: string; after: string };
  possiblePopiaIncident: boolean;
  popiaDetail: string;
};

type Result = {
  counts: Record<string, unknown>;
  nextSteps: { step: string; text: string }[];
  possiblePopiaIncident: boolean;
};

const LABELS: Record<string, string> = {
  messages: "Inbox messages",
  documents: "Documents",
  milestones: "Fee milestones",
  unpaidInvoicesToMove: "Unpaid invoices that move",
  paidInvoicesNeedingCreditNote: "Paid invoices: credit note on the old client",
  signatureRequestsToCancel: "Unsigned e-sign requests to cancel",
  messagesReadByWrongClient: "Messages the wrong client opened",
  messagesSentByWrongClient: "Messages the wrong client sent",
  signaturesSignedByWrongClient: "Documents the wrong client signed",
};

export function ChangeClient({
  caseId,
  currentClientId,
  leadAttorneyId,
  onChanged,
}: {
  caseId: string;
  currentClientId?: string;
  leadAttorneyId?: string;
  onChanged: () => void;
}) {
  const { user } = useAuth();
  const allowed = ADMIN_ROLES.includes(String(user?.role ?? "")) || (!!user?.id && user.id === leadAttorneyId);
  const [open, setOpen] = useState(false);
  const [clients, setClients] = useState<Party[]>([]);
  const [to, setTo] = useState("");
  const [preview, setPreview] = useState<Preview | null>(null);
  const [reason, setReason] = useState("");
  const [showHistory, setShowHistory] = useState(false);
  const [ack, setAck] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<Result | null>(null);

  useEffect(() => {
    if (!open) return;
    apiGet<Party[]>("/api/v1/clients").then((rows) => setClients(rows.filter((r) => r.id !== currentClientId && r.status !== "merged"))).catch(() => setClients([]));
  }, [open, currentClientId]);

  useEffect(() => {
    setPreview(null);
    setAck(false);
    if (!to) return;
    setError(null);
    apiGet<Preview>(`/api/v1/cases/${caseId}/change-client/preview?toClientId=${encodeURIComponent(to)}`)
      .then(setPreview)
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load the preview"));
  }, [to, caseId]);

  if (!allowed) return null;

  const needsAck = (preview?.conflict.relevantMatches ?? 0) > 0;
  const canSubmit = !!preview && reason.trim().length >= 10 && (!needsAck || ack) && !busy;

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      const r = await apiPost<Result>(`/api/v1/cases/${caseId}/change-client`, {
        toClientId: to,
        reason,
        showHistory,
        conflictAcknowledged: ack,
      });
      setResult(r);
      onChanged();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not change the client");
    } finally {
      setBusy(false);
    }
  }

  function close() {
    setOpen(false);
    setTo("");
    setReason("");
    setShowHistory(false);
    setResult(null);
    setError(null);
  }

  return (
    <>
      <Button size="sm" variant="outline" className="mt-2" onClick={() => setOpen(true)}>
        Change client
      </Button>
      <Dialog
        open={open}
        onClose={close}
        title="Change client on this matter"
        wide
        footer={
          result ? (
            <Button onClick={close}>Done</Button>
          ) : (
            <Button disabled={!canSubmit} onClick={submit}>
              {busy ? "Changing…" : "Change client"}
            </Button>
          )
        }
      >
        <ErrorBanner error={error} />
        {result ? (
          <div className="space-y-3 text-sm">
            <p className="font-semibold text-navy">Client changed. The audit log has the details.</p>
            <ul className="list-disc space-y-1 pl-5">
              {result.nextSteps.map((s) => (
                <li key={s.step}>{s.text}</li>
              ))}
            </ul>
          </div>
        ) : (
          <div className="space-y-4 text-sm">
            <p className="text-slate-700">
              Use this when the matter was opened under the wrong client. Only a firm admin or the lead attorney can do it, and it is logged.
            </p>
            <div>
              <Label>Right client</Label>
              <Select aria-label="Right client" value={to} onChange={(e) => setTo(e.target.value)}>
                <option value="">Choose the client</option>
                {clients.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.displayName}
                    {c.idNumber ? ` (${c.idNumber})` : c.email ? ` (${c.email})` : ""}
                  </option>
                ))}
              </Select>
            </div>
            {preview && (
              <>
                <div className="rounded-lg border p-3">
                  <p className="mb-2 font-semibold">What moves</p>
                  <ul className="grid grid-cols-1 gap-1 sm:grid-cols-2">
                    {Object.entries(preview.counts).map(([k, v]) => (
                      <li key={k}>
                        {LABELS[k] || k}: <span className="font-semibold">{v}</span>
                      </li>
                    ))}
                  </ul>
                  {preview.trust.length > 0 && (
                    <p className="mt-2">
                      Trust held for the old client on this matter:{" "}
                      {preview.trust.map((t) => `${moneyExact(t.amount)} in ${t.accountName}`).join(", ")}. This needs a trust transfer that a
                      second firm admin approves. No trust entry is edited.
                    </p>
                  )}
                  <p className="mt-2">The mandate resets to unsigned. You will need to send the new client a mandate.</p>
                </div>
                <div className={`rounded-lg border p-3 ${preview.fica.complete ? "" : "border-amber-200 bg-amber-50 text-amber-950"}`}>
                  FICA for the new client: <span className="font-semibold">{preview.fica.status.replace(/_/g, " ")}</span>
                  {!preview.fica.complete && ". Complete FICA before you act or take money."}
                </div>
                <div className={`rounded-lg border p-3 ${needsAck ? "border-red-200 bg-red-50 text-red-950" : ""}`}>
                  {needsAck ? (
                    <>
                      <p className="font-semibold">Possible conflicts for the new client</p>
                      <ul className="list-disc pl-5">
                        {preview.conflict.matches.slice(0, 8).map((m, i) => (
                          <li key={i}>
                            {m.name} ({m.role}
                            {m.caseNumber ? `, ${m.caseNumber}` : ""})
                          </li>
                        ))}
                      </ul>
                      <label className="mt-2 flex items-center gap-2">
                        <input type="checkbox" checked={ack} onChange={(e) => setAck(e.target.checked)} />I reviewed these and there is no conflict
                      </label>
                    </>
                  ) : (
                    <p>Conflict check: no conflicts found for the new client.</p>
                  )}
                </div>
                {preview.possiblePopiaIncident && (
                  <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-red-950">
                    <p className="font-semibold">Possible POPIA incident</p>
                    <p>{preview.popiaDetail} It will be flagged for your information officer.</p>
                  </div>
                )}
                <label className="flex items-center gap-2">
                  <input type="checkbox" checked={showHistory} onChange={(e) => setShowHistory(e.target.checked)} />
                  Show earlier messages to the new client (off by default)
                </label>
                <div>
                  <Label>Reason (goes in the audit log)</Label>
                  <Textarea aria-label="Reason" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="For example: intake picked the wrong Nomsa Khumalo" />
                </div>
              </>
            )}
          </div>
        )}
      </Dialog>
    </>
  );
}
