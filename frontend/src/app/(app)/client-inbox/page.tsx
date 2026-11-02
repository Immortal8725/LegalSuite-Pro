"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { apiGet } from "@/lib/api";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { when } from "@/lib/inbox";

type Row = { caseId: string; caseNumber: string; title: string; unread: number; lastAt: string };

export default function ClientInboxPage() {
  const [rows, setRows] = useState<Row[] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  useEffect(() => {
    apiGet<{ total: number; matters: Row[] }>("/api/v1/inbox/unread").then((r) => setRows(r.matters)).catch((e) => setErr(e.message));
  }, []);
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold text-slate-900">Client inbox</h1>
      <p className="text-sm text-slate-600">Unread messages from clients, by matter. Open a matter to read and reply.</p>
      <Card>
        <CardHeader><CardTitle>Unread</CardTitle></CardHeader>
        <CardBody>
          {err && <p className="text-sm text-red-700">{err}</p>}
          {rows && rows.length === 0 && <p className="text-sm text-slate-500">Nothing unread. Clients reply from their portal.</p>}
          <ul className="divide-y divide-slate-100">
            {rows?.map((r) => (
              <li key={r.caseId} className="flex items-center justify-between py-2 text-sm">
                <Link href={`/cases/${r.caseId}#inbox`} className="text-navy-dark hover:underline">
                  {r.caseNumber} {r.title}
                </Link>
                <span className="text-xs text-slate-500">{r.unread} unread · {when(r.lastAt)}</span>
              </li>
            ))}
          </ul>
        </CardBody>
      </Card>
    </div>
  );
}
