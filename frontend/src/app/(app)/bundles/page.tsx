"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPostBlob, saveBlob } from "@/lib/api";
import { Button, PageHeader } from "@/components/page";
import { ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select } from "@/components/ui/input";
import type { Matter } from "@/lib/types";

type Doc = { id: string; name: string; category?: string; pdf: boolean };
type Section = { name: string; documentIds: string[] };

const DEFAULT_SECTIONS = ["Pleadings", "Notices", "Affidavits", "Expert reports", "Correspondence"];

export default function BundlesPage() {
  const [matters, setMatters] = useState<Matter[]>([]);
  const [caseId, setCaseId] = useState("");
  const [docs, setDocs] = useState<Doc[]>([]);
  const [title, setTitle] = useState("Trial bundle");
  const [sections, setSections] = useState<Section[]>([{ name: "Pleadings", documentIds: [] }]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    apiGet<Matter[]>("/api/v1/cases").then(setMatters).catch((e) => setError(e instanceof Error ? e.message : "Could not load matters"));
  }, []);
  useEffect(() => {
    if (!caseId) return;
    apiGet<Doc[]>(`/api/v1/cases/${caseId}/bundle/documents`).then(setDocs).catch(() => setDocs([]));
    setSections([{ name: "Pleadings", documentIds: [] }]);
  }, [caseId]);

  const used = new Set(sections.flatMap((s) => s.documentIds));
  const move = (si: number, i: number, dir: number) => setSections((ss) => ss.map((s, k) => {
    if (k !== si) return s;
    const ids = [...s.documentIds];
    const j = i + dir;
    if (j < 0 || j >= ids.length) return s;
    [ids[i], ids[j]] = [ids[j], ids[i]];
    return { ...s, documentIds: ids };
  }));
  const name = (id: string) => docs.find((d) => d.id === id)?.name ?? id;

  return (
    <div>
      <PageHeader title="Court bundles" subtitle="Build a paginated, indexed PDF bundle ready to upload to CaseLines or Court Online." />
      <ErrorBanner error={error} />
      <div className="grid max-w-3xl gap-4">
        <div className="grid gap-3 rounded border bg-white p-4 sm:grid-cols-2">
          <div><Label>Matter</Label><Select value={caseId} onChange={(e) => setCaseId(e.target.value)}>
            <option value="">Choose a matter</option>
            {matters.map((m) => <option key={m.id} value={m.id}>{m.caseNumber} {m.title}</option>)}
          </Select></div>
          <div><Label>Bundle title</Label><Input value={title} onChange={(e) => setTitle(e.target.value)} /></div>
        </div>
        {caseId && (
          <>
            {sections.map((s, si) => (
              <div key={si} className="rounded border bg-white p-4">
                <div className="mb-2 flex items-center gap-2">
                  <span className="font-mono font-semibold">{String.fromCharCode(65 + si)}</span>
                  <Input value={s.name} onChange={(e) => setSections((ss) => ss.map((x, k) => (k === si ? { ...x, name: e.target.value } : x)))} />
                  <Button size="sm" variant="ghost" onClick={() => setSections((ss) => ss.filter((_, k) => k !== si))}>Remove</Button>
                </div>
                <ol className="mb-2 space-y-1 text-sm">
                  {s.documentIds.map((id, i) => (
                    <li key={id} className="flex items-center gap-2">
                      <span className="flex-1">{i + 1}. {name(id)}</span>
                      <button aria-label="Move up" onClick={() => move(si, i, -1)}>↑</button>
                      <button aria-label="Move down" onClick={() => move(si, i, 1)}>↓</button>
                      <button className="text-red-700" onClick={() => setSections((ss) => ss.map((x, k) => (k === si ? { ...x, documentIds: x.documentIds.filter((y) => y !== id) } : x)))}>Remove</button>
                    </li>
                  ))}
                </ol>
                <Select value="" onChange={(e) => e.target.value && setSections((ss) => ss.map((x, k) => (k === si ? { ...x, documentIds: [...x.documentIds, e.target.value] } : x)))}>
                  <option value="">Add a document</option>
                  {docs.filter((d) => d.pdf && !used.has(d.id)).map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
                </Select>
              </div>
            ))}
            {docs.some((d) => !d.pdf) && <p className="text-xs text-slate-500">Only PDFs can go in a bundle. Convert Word documents and photos to PDF and upload them to the matter first.</p>}
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => setSections((ss) => [...ss, { name: DEFAULT_SECTIONS[ss.length] ?? `Section ${String.fromCharCode(65 + ss.length)}`, documentIds: [] }])}>Add section</Button>
              <Button disabled={busy || used.size === 0} onClick={async () => {
                setBusy(true);
                setError(null);
                try {
                  saveBlob(await apiPostBlob(`/api/v1/cases/${caseId}/bundle`, { title, sections }), `${title.replace(/[^A-Za-z0-9 -]/g, "")}.pdf`);
                } catch (e) { setError(e instanceof Error ? e.message : "Could not build the bundle"); }
                finally { setBusy(false); }
              }}>{busy ? "Building..." : "Build bundle PDF"}</Button>
            </div>
            <p className="text-xs text-slate-500">Each page is stamped with its section page (A-1, A-2) and a running number, with an index on the first page and a bookmark per document.</p>
          </>
        )}
      </div>
    </div>
  );
}
