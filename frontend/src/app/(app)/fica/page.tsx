"use client";

import { useEffect, useState } from "react";
import { api, apiBlob, apiGet, apiPost, apiPut, saveBlob } from "@/lib/api";
import { Button, PageHeader, TableWrap, Td, Th } from "@/components/page";
import { Dialog, ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select, Textarea } from "@/components/ui/input";

type Profile = {
  clientId: string;
  clientName: string;
  status: string;
  riskRating?: string;
  riskScore?: number;
  riskFactors: string[];
  idType?: string;
  idNumberMasked?: string;
  idExpiry?: string;
  idDocumentId?: string;
  poaDate?: string;
  poaDocumentId?: string;
  pepStatus?: string;
  sanctionsResult?: string;
  sanctionsCheckedAt?: string;
  sanctionsSources?: string;
  sourceOfFunds?: string;
  verifiedAt?: string;
  nextReviewDate?: string;
  notes?: string;
  missing: string[];
};
type Factors = { factors: Record<string, number>; sanctionsSources: string[] };

const FACTOR_LABELS: Record<string, string> = {
  pep_or_ppo: "Prominent influential person (PEP or PIP)",
  high_risk_country: "Link to a high-risk country (FATF list)",
  cash_heavy: "Large or frequent cash",
  complex_structure: "Trust or layered company structure",
  non_face_to_face: "Never met face to face",
  unusual_instruction: "Instruction with no clear economic purpose",
  property_transfer: "Property or high value transfer",
  third_party_funding: "Paid by a third party",
};
const STATUS: Record<string, { label: string; cls: string }> = {
  not_started: { label: "Not started", cls: "bg-slate-100 text-slate-700" },
  incomplete: { label: "Incomplete", cls: "bg-amber-100 text-amber-800" },
  verified: { label: "Verified", cls: "bg-emerald-100 text-emerald-800" },
  review_due: { label: "Review due", cls: "bg-orange-100 text-orange-800" },
  blocked: { label: "Blocked: sanctions", cls: "bg-red-100 text-red-800" },
};

export default function FicaPage() {
  const [rows, setRows] = useState<Profile[]>([]);
  const [factors, setFactors] = useState<Factors | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<Profile | null>(null);
  const [form, setForm] = useState<Record<string, unknown>>({});
  const [dlgError, setDlgError] = useState<string | null>(null);
  const [filter, setFilter] = useState("all");
  const [note, setNote] = useState<string | null>(null);

  const load = () => apiGet<Profile[]>("/api/v1/fica/report").then(setRows).catch((e) => setError(e instanceof Error ? e.message : "Could not load"));
  useEffect(() => {
    load();
    apiGet<Factors>("/api/v1/fica/factors").then(setFactors).catch(() => setFactors(null));
  }, []);

  function edit(p: Profile) {
    setDlgError(null);
    setOpen(p);
    setForm({
      idType: p.idType ?? "sa_id",
      idNumber: "",
      idExpiry: p.idExpiry ?? "",
      poaDate: p.poaDate ?? "",
      pepStatus: p.pepStatus ?? "",
      sanctionsResult: p.sanctionsResult ?? "",
      sanctionsSources: p.sanctionsSources ?? "",
      sourceOfFunds: p.sourceOfFunds ?? "",
      riskFactors: p.riskFactors ?? [],
      riskRating: p.riskRating ?? "",
      notes: p.notes ?? "",
    });
  }

  async function save(): Promise<Profile | null> {
    if (!open) return null;
    const body: Record<string, unknown> = { ...form };
    if (!body.idNumber) delete body.idNumber;
    if (!body.riskRating) delete body.riskRating;
    if (!body.sanctionsResult) delete body.sanctionsResult;
    try {
      const p = await apiPut<Profile>(`/api/v1/fica/clients/${open.clientId}`, body);
      setOpen(p);
      load();
      return p;
    } catch (e) {
      setDlgError(e instanceof Error ? e.message : "Could not save");
      return null;
    }
  }

  async function upload(kind: "id" | "poa", file: File) {
    if (!open) return;
    const fd = new FormData();
    fd.append("file", file);
    fd.append("kind", kind);
    try {
      const p = await api<Profile>(`/api/v1/fica/clients/${open.clientId}/documents`, { method: "POST", body: fd });
      setOpen(p);
      load();
    } catch (e) {
      setDlgError(e instanceof Error ? e.message : "Upload failed");
    }
  }

  const set = (k: string, v: unknown) => setForm((f) => ({ ...f, [k]: v }));
  const shown = rows.filter((r) => filter === "all" || r.status === filter);
  const count = (s: string) => rows.filter((r) => r.status === s).length;

  return (
    <div>
      <PageHeader
        title="FICA compliance"
        subtitle="Customer due diligence, risk rating and sanctions checks for every client (FIC Act 38 of 2001)."
        actions={
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={async () => saveBlob(await apiBlob("/api/v1/fica/report.csv"), "fica-register.csv")}>Export register</Button>
            <Button variant="outline" onClick={async () => saveBlob(await apiBlob("/api/v1/fica/rmcp.docx"), "RMCP-template.docx")}>RMCP template</Button>
            <Button variant="outline" onClick={async () => {
              const r = await apiPost<{ created: number }>("/api/v1/fica/reminders/run");
              setNote(`${r.created} reminder task${r.created === 1 ? "" : "s"} created.`);
            }}>Create reminders</Button>
          </div>
        }
      />
      <ErrorBanner error={error} />
      {note && <p className="mb-3 text-sm text-emerald-700">{note}</p>}
      <div className="mb-4 flex flex-wrap gap-2 text-sm">
        {["all", "not_started", "incomplete", "review_due", "verified", "blocked"].map((s) => (
          <button key={s} onClick={() => setFilter(s)} className={`rounded-full border px-3 py-1 ${filter === s ? "border-navy bg-navy text-white" : "bg-white"}`}>
            {s === "all" ? `All (${rows.length})` : `${STATUS[s].label} (${count(s)})`}
          </button>
        ))}
      </div>
      <TableWrap>
        <thead>
          <tr><Th>Client</Th><Th>Status</Th><Th>Risk</Th><Th>Next review</Th><Th>Still needed</Th><Th></Th></tr>
        </thead>
        <tbody>
          {shown.map((r) => (
            <tr key={r.clientId}>
              <Td>{r.clientName}</Td>
              <Td><span className={`rounded px-2 py-0.5 text-xs ${STATUS[r.status]?.cls}`}>{STATUS[r.status]?.label ?? r.status}</span></Td>
              <Td>{r.riskRating ?? "Not rated"}</Td>
              <Td>{r.nextReviewDate ?? ""}</Td>
              <Td className="text-xs text-slate-600">{r.missing.join(", ")}</Td>
              <Td><Button size="sm" variant="outline" onClick={() => edit(r)}>Open</Button></Td>
            </tr>
          ))}
        </tbody>
      </TableWrap>

      <Dialog
        open={!!open}
        onClose={() => setOpen(null)}
        title={open ? `FICA: ${open.clientName}` : "FICA"}
        footer={
          <div className="flex gap-2">
            <Button variant="outline" onClick={save}>Save</Button>
            <Button onClick={async () => {
              const p = await save();
              if (!p) return;
              try {
                setOpen(await apiPost<Profile>(`/api/v1/fica/clients/${p.clientId}/verify`));
                load();
              } catch (e) {
                setDlgError(e instanceof Error ? e.message : "Could not verify");
              }
            }}>Save and mark verified</Button>
          </div>
        }
      >
        {open && (
          <div className="grid gap-3 text-sm">
            <ErrorBanner error={dlgError} />
            {open.missing.length > 0 && <p className="rounded bg-amber-50 p-2 text-amber-800">Still needed: {open.missing.join(", ")}</p>}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label>ID type</Label>
                <Select value={String(form.idType)} onChange={(e) => set("idType", e.target.value)}>
                  <option value="sa_id">South African ID</option>
                  <option value="passport">Passport</option>
                  <option value="company_reg">Company registration (CIPC)</option>
                  <option value="trust_loa">Trust letters of authority</option>
                </Select>
              </div>
              <div>
                <Label>ID or registration number {open.idNumberMasked ? `(saved: ${open.idNumberMasked})` : ""}</Label>
                <Input value={String(form.idNumber)} onChange={(e) => set("idNumber", e.target.value)} placeholder="Leave blank to keep" />
              </div>
              <div><Label>ID expiry date</Label><Input type="date" value={String(form.idExpiry)} onChange={(e) => set("idExpiry", e.target.value)} /></div>
              <div><Label>Proof of address date</Label><Input type="date" value={String(form.poaDate)} onChange={(e) => set("poaDate", e.target.value)} /></div>
              <div>
                <Label>Copy of ID {open.idDocumentId ? "(on file)" : ""}</Label>
                <input type="file" accept=".pdf,image/*" onChange={(e) => e.target.files?.[0] && upload("id", e.target.files[0])} />
              </div>
              <div>
                <Label>Proof of address {open.poaDocumentId ? "(on file)" : ""}</Label>
                <input type="file" accept=".pdf,image/*" onChange={(e) => e.target.files?.[0] && upload("poa", e.target.files[0])} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label>Prominent influential person (PEP / PIP)?</Label>
                <Select value={String(form.pepStatus)} onChange={(e) => set("pepStatus", e.target.value)}>
                  <option value="">Not checked</option><option value="no">No</option><option value="yes">Yes</option>
                </Select>
              </div>
              <div>
                <Label>Sanctions check result</Label>
                <Select value={String(form.sanctionsResult)} onChange={(e) => set("sanctionsResult", e.target.value)}>
                  <option value="">Not checked</option><option value="clear">Clear</option>
                  <option value="possible_match">Possible match, checking</option><option value="match">Match: do not act</option>
                </Select>
              </div>
            </div>
            <div>
              <Label>Lists searched (manual check, free)</Label>
              <ul className="mb-1 list-disc pl-5 text-xs text-slate-600">
                {factors?.sanctionsSources.map((s) => <li key={s}>{s}</li>)}
              </ul>
              <Input value={String(form.sanctionsSources)} onChange={(e) => set("sanctionsSources", e.target.value)} placeholder="For example: UN list and FIC TFS list, searched by surname" />
              {open.sanctionsCheckedAt && <p className="text-xs text-slate-500">Last checked {new Date(open.sanctionsCheckedAt).toLocaleDateString("en-ZA")}</p>}
            </div>
            <div>
              <Label>Risk factors</Label>
              <div className="grid gap-1">
                {Object.keys(factors?.factors ?? {}).map((f) => {
                  const list = (form.riskFactors as string[]) ?? [];
                  return (
                    <label key={f} className="flex items-center gap-2">
                      <input type="checkbox" checked={list.includes(f)} onChange={(e) => set("riskFactors", e.target.checked ? [...list, f] : list.filter((x) => x !== f))} />
                      {FACTOR_LABELS[f] ?? f}
                    </label>
                  );
                })}
              </div>
              <p className="mt-1 text-xs text-slate-500">Score {open.riskScore ?? 0}, rated {open.riskRating ?? "not yet"}. You may rate higher than the score, never lower.</p>
              <Select value={String(form.riskRating)} onChange={(e) => set("riskRating", e.target.value)}>
                <option value="">Use the score</option><option value="low">Low</option><option value="medium">Medium</option><option value="high">High</option>
              </Select>
            </div>
            <div><Label>Source of funds (needed for high risk)</Label><Input value={String(form.sourceOfFunds)} onChange={(e) => set("sourceOfFunds", e.target.value)} /></div>
            <div><Label>Notes</Label><Textarea value={String(form.notes)} onChange={(e) => set("notes", e.target.value)} /></div>
            {open.verifiedAt && <p className="text-xs text-emerald-700">Verified {new Date(open.verifiedAt).toLocaleDateString("en-ZA")}. Next review {open.nextReviewDate}.</p>}
          </div>
        )}
      </Dialog>
    </div>
  );
}
