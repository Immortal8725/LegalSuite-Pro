"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { apiGet } from "@/lib/api";
import { PageHeader, StatusBadge } from "@/components/page";
import { ErrorBanner } from "@/components/ui/dialog";
import { Select } from "@/components/ui/input";
import { formatDate } from "@/lib/utils";
import { daysLeftLabel, daysUntil } from "@/lib/docket";
import type { LitCatalog } from "@/lib/litigation";

type Card = { id: string; caseNumber: string; title: string; status: string; courtLevel: string | null; clientName?: string; nextDeadline: string | null };
type Column = { stage: string; label: string; side?: boolean; matters: Card[] };
type Pipeline = { tracks: { track: string; label: string; count: number; columns: Column[] }[]; disclaimer: string };

export default function PipelinePage() {
  const [cat, setCat] = useState<LitCatalog | null>(null);
  const [track, setTrack] = useState("");
  const [data, setData] = useState<Pipeline | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [hideEmpty, setHideEmpty] = useState(true);

  useEffect(() => { apiGet<LitCatalog>("/api/v1/litigation/tracks").then(setCat).catch((e) => setError(e.message)); }, []);
  useEffect(() => {
    apiGet<Pipeline>(`/api/v1/litigation/pipeline${track ? `?track=${encodeURIComponent(track)}` : ""}`)
      .then(setData).catch((e) => setError(e.message));
  }, [track]);

  return (
    <div className="space-y-6">
      <PageHeader title="Litigation pipeline" subtitle="Matters by litigation stage. Set a track on a matter to place it here." />
      <ErrorBanner error={error} />
      <div className="flex flex-wrap items-center gap-3 text-sm">
        <Select value={track} onChange={(e) => setTrack(e.target.value)} className="w-64">
          <option value="">All tracks in use</option>
          {(cat?.tracks ?? []).map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
        </Select>
        <label className="flex items-center gap-2 text-slate-600">
          <input type="checkbox" checked={hideEmpty} onChange={(e) => setHideEmpty(e.target.checked)} /> Hide empty stages
        </label>
      </div>
      {data && data.tracks.length === 0 && (
        <p className="text-sm text-slate-600">No matters have a litigation track yet. Open a matter and choose its track.</p>
      )}
      {data?.tracks.map((t) => (
        <section key={t.track} className="space-y-2">
          <h2 className="text-base font-semibold text-navy">{t.label} <span className="text-sm font-normal text-slate-500">({t.count})</span></h2>
          <div className="flex gap-3 overflow-x-auto pb-2">
            {t.columns.filter((c) => !hideEmpty || c.matters.length > 0).map((col) => (
              <div key={col.stage} className="w-60 shrink-0 rounded-lg border border-slate-200 bg-slate-50 p-2">
                <div className="mb-2 flex items-center justify-between text-xs font-semibold uppercase text-slate-600">
                  <span>{col.label}{col.side ? " ·" : ""}</span><span>{col.matters.length}</span>
                </div>
                <div className="space-y-2">
                  {col.matters.map((m) => (
                    <Link key={m.id} href={`/cases/${m.id}`} className="block rounded-md border border-slate-200 bg-white p-2 text-sm hover:border-navy">
                      <div className="font-medium">{m.caseNumber}</div>
                      <div className="truncate text-slate-700">{m.title}</div>
                      {m.clientName && <div className="truncate text-xs text-slate-500">{m.clientName}</div>}
                      <div className="mt-1 flex items-center justify-between gap-1">
                        <StatusBadge status={m.status} />
                        {m.nextDeadline && (
                          <span className="text-xs text-slate-600" title="Next stage deadline">
                            {formatDate(m.nextDeadline)} · {daysLeftLabel(daysUntil(m.nextDeadline))}
                          </span>
                        )}
                      </div>
                    </Link>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </section>
      ))}
      {data && <p className="text-xs text-slate-500">{data.disclaimer}</p>}
    </div>
  );
}
