"use client";

import { useCallback, useEffect, useState } from "react";
import { apiBlob, apiGet, apiPost, saveBlob } from "@/lib/api";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ErrorBanner } from "@/components/ui/dialog";
import { Label } from "@/components/ui/input";

type PrivacyRequest = {
  id: string;
  subjectType: "client" | "user";
  subjectName: string;
  status: "pending" | "completed" | "rejected";
  reason?: string | null;
  decisionNote?: string | null;
  outcome?: string | null;
  requestedByRole?: string | null;
  createdAt: string;
  retention: string;
};

const when = (s: string) => new Date(s).toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short" });

/** POPIA s 23 and s 24 for the signed-in person: download my data, ask for deletion, see the outcome. */
export function MyPrivacyCard({ mode }: { mode: "portal" | "staff" }) {
  const base = mode === "portal" ? "/api/v1/portal/privacy" : "/api/v1/privacy/me";
  const [requests, setRequests] = useState<PrivacyRequest[]>([]);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const load = useCallback(() => {
    apiGet<PrivacyRequest[]>(`${base}/requests`).then(setRequests).catch((e) => setError(e.message));
  }, [base]);
  useEffect(load, [load]);
  const pending = requests.some((r) => r.status === "pending");

  async function download(format: "json" | "zip") {
    setError(null);
    try {
      const blob = await apiBlob(`${base}/export${format === "zip" ? "?format=zip" : ""}`);
      saveBlob(blob, `my-personal-information.${format}`);
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function ask() {
    setError(null);
    setMessage(null);
    try {
      await apiPost(`${base}/erasure`, { reason });
      setReason("");
      setMessage(mode === "portal" ? "Your request was sent to your law firm." : "Your request was sent to the firm admin.");
      load();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Your personal information</CardTitle>
      </CardHeader>
      <CardBody className="space-y-4 text-sm">
        <ErrorBanner error={error} />
        <p className="text-slate-600">
          Under POPIA you can see the personal information held about you and ask for it to be deleted. Records the law
          requires the firm to keep, such as invoices and trust records, stay but are anonymised.
        </p>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" size="sm" onClick={() => download("json")}>
            Download my data (JSON)
          </Button>
          {mode === "portal" && (
            <Button variant="outline" size="sm" onClick={() => download("zip")}>
              Download with my files (ZIP)
            </Button>
          )}
        </div>
        <div className="space-y-2">
          <Label htmlFor={`erase-${mode}`}>Reason for deletion (optional)</Label>
          <textarea
            id={`erase-${mode}`}
            className="min-h-[70px] w-full rounded-lg border border-slate-200 p-2 text-sm"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            disabled={pending}
          />
          <Button size="sm" variant="outline" onClick={ask} disabled={pending}>
            {pending ? "Deletion request waiting" : "Ask for my data to be deleted"}
          </Button>
          {message && <p className="text-emerald-700">{message}</p>}
        </div>
        {requests.length > 0 && (
          <ul className="space-y-2">
            {requests.map((r) => (
              <li key={r.id} className="rounded-lg border bg-slate-50 p-3">
                <p className="font-semibold text-navy">
                  Deletion request, {r.status} <span className="font-normal text-slate-600">({when(r.createdAt)})</span>
                </p>
                {r.decisionNote && <p className="text-slate-700">Firm note: {r.decisionNote}</p>}
                {r.outcome && <p className="text-slate-700">{r.outcome}</p>}
              </li>
            ))}
          </ul>
        )}
      </CardBody>
    </Card>
  );
}

/** Firm admin queue: approve (anonymise) or refuse with a reason. */
export function PrivacyRequestsAdmin() {
  const [requests, setRequests] = useState<PrivacyRequest[]>([]);
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(() => {
    apiGet<PrivacyRequest[]>("/api/v1/privacy/requests").then(setRequests).catch((e) => setError(e.message));
  }, []);
  useEffect(load, [load]);

  async function decide(id: string, approve: boolean) {
    setError(null);
    if (approve && !window.confirm("Anonymise this person? This cannot be undone.")) return;
    try {
      await apiPost(`/api/v1/privacy/requests/${id}/${approve ? "approve" : "reject"}`, { note: notes[id] || "" });
      load();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Privacy requests (POPIA)</CardTitle>
      </CardHeader>
      <CardBody className="space-y-3 text-sm">
        <ErrorBanner error={error} />
        {requests.length === 0 ? (
          <p className="text-slate-600">No deletion requests.</p>
        ) : (
          requests.map((r) => (
            <div key={r.id} className="rounded-lg border p-3">
              <p className="font-semibold text-navy">
                {r.subjectName} ({r.subjectType === "client" ? "client" : "firm user"}), {r.status}
              </p>
              <p className="text-slate-600">Asked {when(r.createdAt)}{r.reason ? `: ${r.reason}` : ""}</p>
              {r.outcome && <p className="mt-1 text-slate-700">{r.outcome}</p>}
              {r.decisionNote && <p className="mt-1 text-slate-700">Note: {r.decisionNote}</p>}
              {r.status === "pending" && (
                <div className="mt-2 space-y-2">
                  <Label htmlFor={`note-${r.id}`}>Note (required to refuse)</Label>
                  <input
                    id={`note-${r.id}`}
                    className="h-9 w-full rounded-lg border border-slate-200 px-2"
                    value={notes[r.id] || ""}
                    onChange={(e) => setNotes({ ...notes, [r.id]: e.target.value })}
                  />
                  <div className="flex gap-2">
                    <Button size="sm" onClick={() => decide(r.id, true)}>
                      Approve and anonymise
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => decide(r.id, false)}>
                      Refuse
                    </Button>
                  </div>
                </div>
              )}
            </div>
          ))
        )}
        <p className="text-xs text-slate-600">{requests[0]?.retention}</p>
      </CardBody>
    </Card>
  );
}
