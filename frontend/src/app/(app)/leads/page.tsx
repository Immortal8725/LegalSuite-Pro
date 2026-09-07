"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPatch } from "@/lib/api";
import { PageHeader, StatusBadge } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import type { Lead } from "@/lib/types";

const STAGES = ["new", "contacted", "consultation", "retained", "declined"];

export default function LeadsPage() {
  const [rows, setRows] = useState<Lead[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = () => apiGet<Lead[]>("/api/v1/intake/leads").then(setRows).catch((e) => setError(e.message));
  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <PageHeader
        title="Intake pipeline"
        subtitle="Consultations from your public site land here after they submit the form."
      />
      <ErrorBanner error={error} />
      <div className="grid gap-3 overflow-x-auto md:grid-cols-5">
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
                    <p className="mt-1 text-xs text-slate-500">{l.description}</p>
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
                  </div>
                ))}
            </CardBody>
          </Card>
        ))}
      </div>
    </div>
  );
}
