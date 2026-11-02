"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { Button, PageHeader, StatusBadge, TableWrap, Td, Th } from "@/components/page";
import { Dialog, EmptyState, ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import type { Matter, Party } from "@/lib/types";
import type { LitCatalog } from "@/lib/litigation";

const PRACTICE_AREAS = [
  "Divorce and family", "RAF claims", "Personal injury", "Conveyancing and property", "Criminal",
  "Labour and employment", "Wills and estates", "Commercial and contracts", "Debt collection",
  "Immigration", "Notarial", "Litigation", "Medical negligence", "Tax",
];
/** Practice area to the litigation track we suggest. Only a suggestion; the user can change it. */
const SUGGESTED_TRACK: Record<string, string> = {
  "Divorce and family": "divorce",
  "RAF claims": "raf_claim",
  "Personal injury": "high_court_action",
  "Medical negligence": "high_court_action",
  Criminal: "criminal",
  "Labour and employment": "labour",
  "Wills and estates": "deceased_estate",
  "Debt collection": "magistrates_action",
  Litigation: "high_court_action",
};
const today = () => new Date(Date.now() + 2 * 3600_000).toISOString().slice(0, 10);

export default function CasesPage() {
  const [rows, setRows] = useState<Matter[]>([]);
  const [clients, setClients] = useState<Party[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const openDialog = () => {
    setDialogError(null);
    setOpen(true);
  };
  const [cat, setCat] = useState<LitCatalog | null>(null);
  const [lit, setLit] = useState({ track: "", courtLevel: "", stage: "", stageDate: today() });
  const pickTrack = (id: string) => {
    const t = cat?.tracks.find((x) => x.id === id);
    setLit({ track: id, courtLevel: t?.courtLevels[0] ?? "", stage: t?.stages[0]?.id ?? "", stageDate: today() });
  };
  const [form, setForm] = useState({
    title: "",
    clientId: "",
    practiceArea: "Litigation",
    description: "",
    opposingParty: "",
    accrualDate: "",
    governmentalDefendant: false,
  });

  const load = () =>
    Promise.all([apiGet<Matter[]>("/api/v1/cases"), apiGet<Party[]>("/api/v1/clients")])
      .then(([c, cl]) => {
        setRows(c);
        setClients(cl);
      })
      .catch((e) => setError(e.message));

  useEffect(() => {
    load();
    apiGet<LitCatalog>("/api/v1/litigation/tracks").then(setCat).catch(() => setCat(null));
  }, []);
  const litTrack = cat?.tracks.find((x) => x.id === lit.track);

  return (
    <div>
      <PageHeader
        title="Cases & matters"
        subtitle="Every open file, with court and opposing party."
        actions={<Button onClick={openDialog}>New matter</Button>}
      />
      <ErrorBanner error={error} />
      {rows.length === 0 && !error ? (
        <EmptyState title="No matters yet" body="Open a file from intake or add a retained client first." action={<Button onClick={openDialog}>New matter</Button>} />
      ) : (
        <TableWrap>
          <thead>
            <tr>
              <Th>#</Th>
              <Th>Title</Th>
              <Th>Client</Th>
              <Th>Area</Th>
              <Th>Matter type</Th>
              <Th>Stage</Th>
              <Th>Priority</Th>
              <Th>Status</Th>
            </tr>
          </thead>
          <tbody>
            {rows.map((c) => (
              <tr key={c.id} className="hover:bg-slate-50">
                <Td className="font-mono">
                  <Link className="text-navy font-semibold" href={`/cases/${c.id}`}>
                    {c.caseNumber}
                  </Link>
                </Td>
                <Td>{c.title}</Td>
                <Td>{c.clientName}</Td>
                <Td>{c.practiceArea}</Td>
                <Td>{c.litigationTrackLabel ?? "Not litigation"}</Td>
                <Td>{c.litigationStageLabel ?? (c.litigationTrack ? "Not started" : "")}</Td>
                <Td>{c.priority}</Td>
                <Td>
                  <StatusBadge status={c.status} />
                </Td>
              </tr>
            ))}
          </tbody>
        </TableWrap>
      )}
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Open a matter"
        footer={
          <Button
            disabled={saving}
            onClick={async () => {
              setSaving(true);
              setDialogError(null);
              try {
                const body: Record<string, unknown> = { ...form };
                if (lit.track) {
                  body.litigationTrack = lit.track;
                  if (lit.courtLevel) body.courtLevel = lit.courtLevel;
                  if (lit.stage) {
                    body.initialStage = lit.stage;
                    body.initialStageDate = lit.stageDate;
                  }
                }
                await apiPost("/api/v1/cases", body);
                setOpen(false);
                load();
              } catch (err) {
                setDialogError(err instanceof Error ? err.message : "Could not create the matter");
              } finally {
                setSaving(false);
              }
            }}
          >
            {saving ? "Saving…" : "Create"}
          </Button>
        }
      >
        <ErrorBanner error={dialogError} />
        <div className="space-y-3">
          <div>
            <Label>Title</Label>
            <Input aria-label="Title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
          </div>
          <div>
            <Label>Client</Label>
            <Select aria-label="Client" value={form.clientId} onChange={(e) => setForm({ ...form, clientId: e.target.value })}>
              <option value="">Select…</option>
              {clients.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.displayName}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <Label>Practice area</Label>
            <Select
              aria-label="Practice area"
              value={form.practiceArea}
              onChange={(e) => {
                setForm({ ...form, practiceArea: e.target.value });
                const sug = SUGGESTED_TRACK[e.target.value];
                if (sug && !lit.track) pickTrack(sug);
              }}
            >
              {!PRACTICE_AREAS.includes(form.practiceArea) && form.practiceArea && <option value={form.practiceArea}>{form.practiceArea}</option>}
              {PRACTICE_AREAS.map((a) => (
                <option key={a} value={a}>{a}</option>
              ))}
            </Select>
          </div>
          {cat && (
            <div className="rounded-md border border-slate-200 p-3 space-y-3">
              <div>
                <Label>Matter type (litigation track)</Label>
                <Select aria-label="Matter type" value={lit.track} onChange={(e) => pickTrack(e.target.value)}>
                  <option value="">Not litigation</option>
                  {cat.tracks.map((t) => (
                    <option key={t.id} value={t.id}>{t.label}</option>
                  ))}
                </Select>
              </div>
              {litTrack && (
                <>
                  <div>
                    <Label>Court</Label>
                    <Select aria-label="Court" value={lit.courtLevel} onChange={(e) => setLit({ ...lit, courtLevel: e.target.value })}>
                      {litTrack.courtLevels.map((l) => (
                        <option key={l} value={l}>{cat.courtLevels[l] ?? l}</option>
                      ))}
                    </Select>
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <Label>Initial stage</Label>
                      <Select aria-label="Initial stage" value={lit.stage} onChange={(e) => setLit({ ...lit, stage: e.target.value })}>
                        <option value="">Not started yet</option>
                        {litTrack.stages.filter((s) => !s.side).map((s) => (
                          <option key={s.id} value={s.id}>{s.label}</option>
                        ))}
                      </Select>
                    </div>
                    <div>
                      <Label>Stage date</Label>
                      <Input aria-label="Stage date" type="date" max={today()} disabled={!lit.stage} value={lit.stageDate} onChange={(e) => setLit({ ...lit, stageDate: e.target.value })} />
                    </div>
                  </div>
                  <p className="text-xs text-slate-500">Owlaw adds the deadlines for this stage straight away, counted in court days. Check them before relying on them.</p>
                </>
              )}
            </div>
          )}
          <div>
            <Label>Opposing party</Label>
            <Input aria-label="Opposing party" value={form.opposingParty} onChange={(e) => setForm({ ...form, opposingParty: e.target.value })} />
          </div>
          <div>
            <Label>Incident / accrual date</Label>
            <Input aria-label="Incident / accrual date" type="date" value={form.accrualDate} onChange={(e) => setForm({ ...form, accrualDate: e.target.value })} />
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={form.governmentalDefendant}
              onChange={(e) => setForm({ ...form, governmentalDefendant: e.target.checked })}
            />
            Governmental defendant (TTCA notice)
          </label>
          <div>
            <Label>Facts / assignment</Label>
            <Textarea aria-label="Facts / assignment" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
        </div>
      </Dialog>
    </div>
  );
}
