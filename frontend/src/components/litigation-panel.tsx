"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { apiGet, apiPost, apiPut } from "@/lib/api";
import { formatDate } from "@/lib/utils";
import { daysLeftLabel, daysUntil, todayIso } from "@/lib/docket";
import { useAuth } from "@/components/auth-provider";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Select, Textarea } from "@/components/ui/input";
import { ADMIN_ROLES, UNIT_LABEL, type LitCatalog, type MatterLitigation } from "@/lib/litigation";

export function LitigationPanel({ caseId }: { caseId: string }) {
  const { user } = useAuth();
  const [cat, setCat] = useState<LitCatalog | null>(null);
  const [m, setM] = useState<MatterLitigation | null>(null);
  const [track, setTrack] = useState("");
  const [level, setLevel] = useState("");
  const [stage, setStage] = useState("");
  const [date, setDate] = useState(todayIso());
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [notifyClient, setNotifyClient] = useState(true);
  const [clientMessage, setClientMessage] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(() =>
    apiGet<MatterLitigation>(`/api/v1/cases/${caseId}/litigation`).then((row) => {
      setM(row);
      setTrack(row.track ?? "");
      setLevel(row.courtLevel ?? "");
      setStage("");
    }), [caseId]);

  useEffect(() => {
    apiGet<LitCatalog>("/api/v1/litigation/tracks").then(setCat).catch((e) => setErr(e.message));
    load().catch((e) => setErr(e.message));
  }, [load]);

  useEffect(() => {
    if (!stage) { setClientMessage(""); return; }
    apiGet<{ body: string }>(`/api/v1/cases/${caseId}/stage-message?stage=${encodeURIComponent(stage)}`)
      .then((r) => setClientMessage(r.body))
      .catch(() => setClientMessage(""));
  }, [caseId, stage]);

  if (!cat || !m) return null;
  const t = cat.tracks.find((x) => x.id === track);
  const locked = m.timeline.length > 0;
  const isAdmin = ADMIN_ROLES.includes(String(user?.role ?? ""));

  async function saveTrack() {
    setBusy(true); setErr(null); setMsg(null);
    try {
      await apiPut(`/api/v1/cases/${caseId}/litigation`, { track: track || null, courtLevel: level || null });
      await load();
      setMsg("Track saved.");
    } catch (e) { setErr((e as Error).message); } finally { setBusy(false); }
  }

  async function addStage() {
    if (!stage) return;
    setBusy(true); setErr(null); setMsg(null);
    try {
      const r = await apiPost<MatterLitigation>(`/api/v1/cases/${caseId}/stages`, {
        stage, date, note, notifyClient, clientMessage: notifyClient ? clientMessage : undefined,
      });
      await load();
      setNote("");
      window.dispatchEvent(new Event("owlaw:stage-changed"));
      const n = r.createdDeadlines?.length ?? 0;
      const sent = (r as { clientMessage?: unknown }).clientMessage ? " Client update sent to their inbox." : "";
      setMsg((n ? `Stage recorded. ${n} deadline task${n === 1 ? "" : "s"} added.` : "Stage recorded.") + sent);
    } catch (e) { setErr((e as Error).message); } finally { setBusy(false); }
  }

  const rulesFor = (s: string) => (t?.rules ?? []).filter((r) => r.trigger === s);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Litigation stage</CardTitle>
      </CardHeader>
      <CardBody className="space-y-4 text-sm">
        {isAdmin && (
          <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
            {cat.disclaimer}
          </p>
        )}
        {err && <p className="text-xs text-red-700">{err}</p>}
        {msg && <p className="text-xs text-emerald-700">{msg}</p>}
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
          <Select value={track} disabled={locked} onChange={(e) => {
            setTrack(e.target.value);
            const nt = cat.tracks.find((x) => x.id === e.target.value);
            setLevel(nt?.courtLevels[0] ?? "");
          }}>
            <option value="">No litigation track</option>
            {cat.tracks.map((x) => <option key={x.id} value={x.id}>{x.label}</option>)}
          </Select>
          <Select value={level} disabled={!t} onChange={(e) => setLevel(e.target.value)}>
            {(t?.courtLevels ?? []).map((l) => <option key={l} value={l}>{cat.courtLevels[l] ?? l}</option>)}
          </Select>
          <Button size="sm" variant="outline" disabled={busy || (track === (m.track ?? "") && level === (m.courtLevel ?? ""))} onClick={saveTrack}>
            Save track
          </Button>
        </div>
        {locked && <p className="text-xs text-slate-500">The track is fixed once stages are recorded.</p>}
        {m.trackNote && <p className="text-xs text-slate-600">{m.trackNote}</p>}

        {m.track && (
          <div className="space-y-2">
            <div className="text-xs font-semibold uppercase text-slate-500">Move to stage</div>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-4">
              <Select value={stage} onChange={(e) => setStage(e.target.value)} className="sm:col-span-2">
                <option value="">Choose the next stage</option>
                {m.nextStages.map((s) => <option key={s.id} value={s.id}>{s.label}{s.side ? " (side step)" : ""}</option>)}
              </Select>
              <Input type="date" value={date} max={todayIso()} onChange={(e) => setDate(e.target.value)} />
              <Button size="sm" disabled={busy || !stage} onClick={addStage}>Record stage</Button>
            </div>
            <Input placeholder="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} maxLength={250} />
            {stage && (
              <div className="space-y-1 rounded-md border border-slate-200 p-2">
                <label className="flex items-center gap-2 text-xs text-slate-700">
                  <input type="checkbox" checked={notifyClient} onChange={(e) => setNotifyClient(e.target.checked)} />
                  Send the client a plain-language update in their inbox
                </label>
                {notifyClient && (
                  <Textarea rows={6} maxLength={4000} value={clientMessage} onChange={(e) => setClientMessage(e.target.value)} />
                )}
              </div>
            )}
            {stage && rulesFor(stage).length > 0 && (
              <ul className="space-y-1 text-xs text-slate-600">
                {rulesFor(stage).map((r) => (
                  <li key={r.id}>
                    Adds: {r.title}, {r.amount} {UNIT_LABEL[r.unit] ?? r.unit} ({r.source}){r.needsReview ? ". Check this period" : ""}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        {m.deadlines.length > 0 && (
          <div>
            <div className="mb-1 text-xs font-semibold uppercase text-slate-500">Deadlines</div>
            <ul className="space-y-1">
              {m.deadlines.map((d) => (
                <li key={d.id} className="flex flex-wrap items-baseline justify-between gap-2 border-b border-slate-100 py-1">
                  <span className={d.status === "completed" ? "text-slate-400 line-through" : ""} title={d.description}>{d.title}</span>
                  <span className="text-xs text-slate-600">
                    {d.dueDate ? `${formatDate(d.dueDate)} · ${daysLeftLabel(daysUntil(d.dueDate))}` : "No date"}
                  </span>
                </li>
              ))}
            </ul>
            <Link href="/tasks" className="text-xs text-navy underline">Open tasks</Link>
          </div>
        )}

        {m.timeline.length > 0 && (
          <div>
            <div className="mb-1 text-xs font-semibold uppercase text-slate-500">Timeline</div>
            <ol className="relative ml-2 border-l border-slate-200">
              {m.timeline.map((e) => (
                <li key={e.id} className="mb-2 ml-4">
                  <span className="absolute -left-1.5 mt-1.5 h-3 w-3 rounded-full border border-white bg-navy" />
                  <div className="font-medium">{e.label}</div>
                  <div className="text-xs text-slate-500">{formatDate(e.date)}{e.note ? ` · ${e.note}` : ""}</div>
                </li>
              ))}
            </ol>
          </div>
        )}
      </CardBody>
    </Card>
  );
}
