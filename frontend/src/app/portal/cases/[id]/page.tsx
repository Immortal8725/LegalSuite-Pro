"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { apiBlob, apiGet, apiPost, saveBlob } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ErrorBanner } from "@/components/ui/dialog";
import { ClientSummaryBody } from "@/components/client-summary";
import { ThreadView } from "@/components/thread-view";
import type { ClientSummary, InboxMessage } from "@/lib/inbox";

type PortalMatter = {
  case: { id: string; caseNumber: string; title: string; status: string };
  summary: ClientSummary;
  messages: InboxMessage[];
};

export default function PortalMatterPage() {
  const params = useParams<{ id: string }>();
  const { tenant, logout } = useAuth();
  const [m, setM] = useState<PortalMatter | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() =>
    apiGet<PortalMatter>(`/api/v1/portal/cases/${params.id}`).then(setM), [params.id]);

  useEffect(() => { load().catch((e) => setError(e.message)); }, [load]);

  async function send(body: string, file: File | null) {
    const fd = new FormData();
    fd.append("body", body);
    if (file) fd.append("file", file);
    await apiPost(`/api/v1/portal/cases/${params.id}/messages`, fd);
    await load();
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="flex items-center justify-between border-b bg-white px-6 py-4">
        <div>
          <p className="text-xs uppercase tracking-wide text-gold-dark">Client portal</p>
          <h1 className="text-lg font-bold text-navy">{tenant?.firmName}</h1>
        </div>
        <Button variant="outline" size="sm" onClick={logout}>Sign out</Button>
      </header>
      <main className="mx-auto max-w-3xl space-y-6 p-6">
        <Link href="/portal" className="text-sm text-navy hover:underline">Back to my matters</Link>
        <ErrorBanner error={error} />
        {m && (
          <>
            <h2 className="text-xl font-semibold text-slate-900">{m.case.caseNumber} {m.case.title}</h2>
            <Card>
              <CardHeader><CardTitle>Where your matter stands</CardTitle></CardHeader>
              <CardBody><ClientSummaryBody s={m.summary} /></CardBody>
            </Card>
            <Card>
              <CardHeader><CardTitle>Messages with your attorney</CardTitle></CardHeader>
              <CardBody>
                <ThreadView
                  me="client"
                  messages={m.messages}
                  onSend={send}
                  onOpenFile={(msg) => apiBlob(`/api/v1/portal/cases/${params.id}/messages/${msg.id}/attachment`)
                    .then((b) => saveBlob(b, msg.attachment?.name ?? "file")).catch((e) => setError(e.message))}
                  placeholder="Write to your attorney. You can attach a document (up to 50 MB)."
                />
              </CardBody>
            </Card>
          </>
        )}
      </main>
    </div>
  );
}
