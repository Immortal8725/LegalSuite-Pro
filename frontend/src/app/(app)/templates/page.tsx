"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost, apiPut } from "@/lib/api";
import { Button, PageHeader } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, EmptyState, ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import type { DocTemplate, Matter, Party } from "@/lib/types";

export default function TemplatesPage() {
  const [rows, setRows] = useState<DocTemplate[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ name: "", category: "correspondence", body: "" });
  const [active, setActive] = useState<DocTemplate | null>(null);
  const [cases, setCases] = useState<Matter[]>([]);
  const [clients, setClients] = useState<Party[]>([]);
  const [caseId, setCaseId] = useState("");
  const [merged, setMerged] = useState("");

  const load = () => apiGet<DocTemplate[]>("/api/v1/templates").then(setRows).catch((e) => setError(e.message));
  useEffect(() => {
    load();
    apiGet<Matter[]>("/api/v1/cases").then(setCases);
    apiGet<Party[]>("/api/v1/clients").then(setClients);
  }, []);

  return (
    <div>
      <PageHeader
        title="Document templates"
        subtitle="Merge {{client.name}}, {{case.title}}, {{firm.name}}, and {{today}} into a letter."
        actions={<Button onClick={() => setOpen(true)}>New template</Button>}
      />
      <ErrorBanner error={error} />
      {rows.length === 0 ? (
        <EmptyState title="No templates" body="Add an engagement letter or status update the firm sends every week." />
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          <div className="space-y-3">
            {rows.map((t) => (
              <button
                key={t.id}
                className={`w-full rounded-xl bg-white p-4 text-left shadow-card ${active?.id === t.id ? "ring-2 ring-gold" : ""}`}
                onClick={() => {
                  setActive(t);
                  setMerged("");
                }}
              >
                <p className="font-semibold text-navy">{t.name}</p>
                <p className="text-xs uppercase text-slate-400">{t.category}</p>
              </button>
            ))}
          </div>
          {active && (
            <Card>
              <CardHeader>
                <CardTitle>{active.name}</CardTitle>
              </CardHeader>
              <CardBody className="space-y-3">
                <Textarea
                  rows={12}
                  value={active.body}
                  onChange={(e) => setActive({ ...active, body: e.target.value })}
                />
                <div className="grid gap-3 sm:grid-cols-2">
                  <Select value={caseId} onChange={(e) => setCaseId(e.target.value)}>
                    <option value="">Matter (optional)</option>
                    {cases.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.caseNumber}
                      </option>
                    ))}
                  </Select>
                  <Select
                    value=""
                    onChange={() => undefined}
                  >
                    <option value="">Client comes from the matter</option>
                    {clients.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.displayName}
                      </option>
                    ))}
                  </Select>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="outline"
                    onClick={async () => {
                      await apiPut(`/api/v1/templates/${active.id}`, active);
                      load();
                    }}
                  >
                    Save body
                  </Button>
                  <Button
                    onClick={async () => {
                      const res = await apiPost<DocTemplate>(`/api/v1/templates/${active.id}/merge`, {
                        caseId: caseId || undefined,
                      });
                      setMerged(res.merged || "");
                    }}
                  >
                    Merge
                  </Button>
                </div>
                {merged && <pre className="whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm">{merged}</pre>}
              </CardBody>
            </Card>
          )}
        </div>
      )}
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="New template"
        footer={
          <Button
            onClick={async () => {
              await apiPost("/api/v1/templates", form);
              setOpen(false);
              setForm({ name: "", category: "correspondence", body: "" });
              load();
            }}
          >
            Create
          </Button>
        }
      >
        <Label>Name</Label>
        <Input className="mb-3" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        <Label>Category</Label>
        <Select className="mb-3" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
          <option value="correspondence">Correspondence</option>
          <option value="retainer">Retainer</option>
          <option value="demand">Demand</option>
          <option value="pleading">Pleading</option>
        </Select>
        <Label>Body</Label>
        <Textarea rows={8} value={form.body} onChange={(e) => setForm({ ...form, body: e.target.value })} />
      </Dialog>
    </div>
  );
}
