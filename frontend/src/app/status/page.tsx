"use client";

import { useEffect, useState } from "react";

type Status = {
  status: string;
  databaseUp: boolean;
  databaseLatencyMs?: number;
  version?: string;
  checkedAt?: string;
  backup?: { configured?: boolean; lastSuccessAt?: string; lastStatus?: string };
};
type Run = { id: number; conclusion: string | null; status: string; created_at: string; html_url: string };

const REPO = "Immortal8725/LegalSuite-Pro";

function when(iso?: string) {
  if (!iso) return "never";
  return new Date(iso).toLocaleString("en-ZA", { timeZone: "Africa/Johannesburg", dateStyle: "medium", timeStyle: "short" }) + " SAST";
}

function Dot({ ok }: { ok: boolean | null }) {
  const c = ok === null ? "bg-slate-300" : ok ? "bg-emerald-500" : "bg-red-500";
  return <span className={`inline-block h-3 w-3 rounded-full ${c}`} aria-hidden />;
}

export default function StatusPage() {
  const [s, setS] = useState<Status | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [runs, setRuns] = useState<Run[]>([]);
  const [ms, setMs] = useState<number | null>(null);

  useEffect(() => {
    const t0 = performance.now();
    fetch("/api/v1/status")
      .then((r) => r.json())
      .then((j) => {
        setMs(Math.round(performance.now() - t0));
        setS(j.data);
      })
      .catch(() => setErr("The service did not answer. On the free plan it may be waking up, so refresh in a minute."));
    fetch(`https://api.github.com/repos/${REPO}/actions/workflows/keep-alive.yml/runs?per_page=30`)
      .then((r) => r.json())
      .then((j) => setRuns(j.workflow_runs || []))
      .catch(() => setRuns([]));
  }, []);

  const done = runs.filter((r) => r.status === "completed");
  const good = done.filter((r) => r.conclusion === "success").length;

  return (
    <main className="mx-auto max-w-2xl px-4 py-10 text-slate-800">
      <h1 className="text-2xl font-semibold">Owlaw system status</h1>
      <p className="mt-1 text-sm text-slate-500">Live checks of the Owlaw service, updated each time you open this page.</p>
      {err && <p className="mt-4 rounded bg-amber-50 p-3 text-sm text-amber-800">{err}</p>}
      <ul className="mt-6 divide-y rounded border bg-white">
        <li className="flex items-center justify-between p-4">
          <span className="flex items-center gap-2"><Dot ok={s ? s.status === "up" : err ? false : null} /> Application</span>
          <span className="text-sm text-slate-500">{s ? `Up${ms !== null ? `, answered in ${ms} ms` : ""}` : err ? "Not answering" : "Checking"}</span>
        </li>
        <li className="flex items-center justify-between p-4">
          <span className="flex items-center gap-2"><Dot ok={s ? s.databaseUp : null} /> Database</span>
          <span className="text-sm text-slate-500">{s ? (s.databaseUp ? `Up, ${s.databaseLatencyMs ?? "?"} ms` : "Down") : "Checking"}</span>
        </li>
        <li className="flex items-center justify-between p-4">
          <span className="flex items-center gap-2"><Dot ok={s ? s.backup?.lastStatus === "ok" : null} /> Nightly encrypted backup</span>
          <span className="text-sm text-slate-500">{s ? (s.backup?.configured ? `Last good: ${when(s.backup?.lastSuccessAt)}` : "Not switched on") : "Checking"}</span>
        </li>
        <li className="flex items-center justify-between p-4">
          <span className="flex items-center gap-2"><Dot ok={done.length ? good === done.length : null} /> Uptime checks (last {done.length})</span>
          <span className="text-sm text-slate-500">{done.length ? `${good} of ${done.length} passed` : "No checks yet"}</span>
        </li>
      </ul>
      {done.length > 0 && (
        <div className="mt-4 flex flex-wrap gap-1" aria-label="Recent uptime checks, newest first">
          {done.map((r) => (
            <a key={r.id} href={r.html_url} title={`${when(r.created_at)}: ${r.conclusion}`}
               className={`h-6 w-2 rounded-sm ${r.conclusion === "success" ? "bg-emerald-500" : "bg-red-500"}`} />
          ))}
        </div>
      )}
      <p className="mt-6 text-xs text-slate-500">
        Uptime checks run every 10 minutes, 06:00 to 18:00 SAST, Monday to Saturday. Outside those hours the service may
        take up to a minute to wake. Version {s?.version ?? "unknown"}, checked {when(s?.checkedAt)}.
      </p>
    </main>
  );
}
