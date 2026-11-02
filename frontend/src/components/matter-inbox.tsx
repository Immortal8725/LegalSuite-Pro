"use client";

import { useCallback, useEffect, useState } from "react";
import { apiBlob, apiGet, apiPost, apiPut, saveBlob } from "@/lib/api";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Textarea } from "@/components/ui/input";
import { ClientSummaryBody } from "@/components/client-summary";
import { ThreadView } from "@/components/thread-view";
import { inboxChanged, type ClientSummary, type StaffThread } from "@/lib/inbox";

/** Staff view: what the client sees about the stage, plus the client inbox for this matter. */
export function MatterClientPanels({ caseId }: { caseId: string }) {
  const [summary, setSummary] = useState<ClientSummary | null>(null);
  const [thread, setThread] = useState<StaffThread | null>(null);
  const [note, setNote] = useState("");
  const [editing, setEditing] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [portalPw, setPortalPw] = useState("");
  const [portalMsg, setPortalMsg] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [s, t] = await Promise.all([
      apiGet<ClientSummary>(`/api/v1/cases/${caseId}/client-summary`),
      apiGet<StaffThread>(`/api/v1/cases/${caseId}/inbox`),
    ]);
    setSummary(s); setNote(s.firmNote ?? ""); setThread(t);
    inboxChanged();
  }, [caseId]);

  useEffect(() => {
    load().catch((e) => setErr(e.message));
    const h = () => load().catch(() => undefined);
    window.addEventListener("owlaw:stage-changed", h);
    return () => window.removeEventListener("owlaw:stage-changed", h);
  }, [load]);

  async function saveNote() {
    try {
      setSummary(await apiPut<ClientSummary>(`/api/v1/cases/${caseId}/client-note`, { note }));
      setEditing(false);
    } catch (e) { setErr((e as Error).message); }
  }

  async function send(body: string, file: File | null) {
    const fd = new FormData();
    fd.append("body", body);
    if (file) fd.append("file", file);
    await apiPost(`/api/v1/cases/${caseId}/inbox`, fd);
    await load();
  }

  async function enablePortal() {
    if (!thread?.clientId) return;
    setPortalMsg(null);
    try {
      const r = await apiPost<{ firmSlug: string; email: string; loginUrl: string }>(
        `/api/v1/clients/${thread.clientId}/portal-access`, { password: portalPw });
      setPortalPw("");
      setPortalMsg(`Portal on. Give the client: ${r.loginUrl}, firm code ${r.firmSlug}, email ${r.email} and the password you set (share it by phone, not email).`);
      await load();
    } catch (e) { setPortalMsg((e as Error).message); }
  }

  if (!summary || !thread) return err ? <p className="mb-4 text-xs text-red-700">{err}</p> : null;

  return (
    <div className="mb-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
      <Card>
        <CardHeader><CardTitle>What the client sees about this stage</CardTitle></CardHeader>
        <CardBody className="space-y-3">
          <ClientSummaryBody s={summary} />
          {editing ? (
            <div className="space-y-2">
              <Textarea rows={3} maxLength={2000} value={note} placeholder="Optional note for the client on this matter" onChange={(e) => setNote(e.target.value)} />
              <div className="flex gap-2">
                <Button size="sm" onClick={saveNote}>Save note</Button>
                <Button size="sm" variant="outline" onClick={() => { setEditing(false); setNote(summary.firmNote ?? ""); }}>Cancel</Button>
              </div>
            </div>
          ) : (
            <Button size="sm" variant="outline" onClick={() => setEditing(true)}>{summary.firmNote ? "Edit client note" : "Add a client note"}</Button>
          )}
        </CardBody>
      </Card>
      <Card id="inbox">
        <CardHeader><CardTitle>Client inbox{thread.clientName ? `: ${thread.clientName}` : ""}</CardTitle></CardHeader>
        <CardBody className="space-y-3">
          {err && <p className="text-xs text-red-700">{err}</p>}
          {!thread.clientId ? (
            <p className="text-sm text-slate-600">Link a client to this matter to message them.</p>
          ) : (
            <>
              {!thread.portalEnabled && (
                <div className="space-y-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
                  <p>This client cannot sign in to the portal yet, so they will not see messages. Set a portal password to turn it on.</p>
                  <div className="flex gap-2">
                    <Input type="password" autoComplete="new-password" placeholder="At least 10 characters" value={portalPw} onChange={(e) => setPortalPw(e.target.value)} />
                    <Button size="sm" disabled={portalPw.length < 10} onClick={enablePortal}>Turn on portal</Button>
                  </div>
                </div>
              )}
              {portalMsg && <p className="text-xs text-slate-700">{portalMsg}</p>}
              {!thread.emailConfigured && <p className="text-xs text-slate-500">Email is not set up for this firm, so the client will only see new messages when they sign in.</p>}
              <ThreadView
                me="staff"
                messages={thread.messages}
                onSend={send}
                onOpenFile={(m) => m.attachment?.documentId &&
                  apiBlob(`/api/v1/documents/${m.attachment.documentId}/download`).then((b) => saveBlob(b, m.attachment!.name)).catch((e) => setErr(e.message))}
                placeholder="Message the client. They see this in their portal."
              />
            </>
          )}
        </CardBody>
      </Card>
    </div>
  );
}
