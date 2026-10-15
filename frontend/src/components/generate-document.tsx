"use client";

import { useEffect, useMemo, useState } from "react";
import { apiGet, apiPost, apiPostBlob } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Input, Label, Select } from "@/components/ui/input";
import type { LibraryTemplate, TemplateForm } from "@/lib/types";

type Generated = {
  template: string;
  documents: { id: string; name: string; mimeType: string }[];
  signature?: { signUrl?: string };
  email?: { sent: boolean; reason?: string; to: string };
};

/** Generate a firm-branded document from the template library for one matter. */
export function GenerateDocument({
  caseId,
  open,
  onClose,
  onDone,
  clientEmail,
}: {
  caseId: string;
  open: boolean;
  onClose: () => void;
  onDone: () => void;
  clientEmail?: string;
}) {
  const [library, setLibrary] = useState<LibraryTemplate[]>([]);
  const [templateId, setTemplateId] = useState("");
  const [form, setForm] = useState<TemplateForm | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [pdf, setPdf] = useState(true);
  const [docx, setDocx] = useState(true);
  const [sign, setSign] = useState(false);
  const [signerName, setSignerName] = useState("");
  const [signerEmail, setSignerEmail] = useState("");
  const [emailTo, setEmailTo] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<Generated | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    apiGet<LibraryTemplate[]>("/api/v1/doc-templates").then(setLibrary).catch((e) => setError(e.message));
  }, [open]);

  useEffect(() => {
    setForm(null);
    setResult(null);
    if (!templateId) return;
    apiGet<TemplateForm>(`/api/v1/doc-templates/${templateId}/form?caseId=${caseId}`)
      .then((f) => {
        setForm(f);
        const v: Record<string, string> = {};
        f.fields.filter((x) => !x.system).forEach((x) => (v[x.key] = x.value || ""));
        setValues(v);
        const client = f.fields.find((x) => x.key === "client_name")?.value || "";
        setSignerName(client);
        setSignerEmail(clientEmail || "");
      })
      .catch((e) => setError(e.message));
  }, [templateId, caseId, clientEmail]);

  useEffect(() => () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
  }, [previewUrl]);

  const groups = useMemo(() => {
    const byCat: Record<string, LibraryTemplate[]> = {};
    library.forEach((t) => (byCat[t.category] = [...(byCat[t.category] || []), t]));
    return byCat;
  }, [library]);

  const fillIns = form?.fields.filter((f) => !f.system) || [];
  const firmFields = form?.fields.filter((f) => f.system) || [];
  const body = () => ({
    caseId,
    values,
    formats: [pdf && "pdf", docx && "docx"].filter(Boolean),
    ...(sign && signerEmail ? { sign: { signerName, signerEmail } } : {}),
    ...(emailTo ? { email: { to: emailTo } } : {}),
  });

  const preview = async () => {
    setBusy("preview");
    setError(null);
    try {
      const blob = await apiPostBlob(`/api/v1/doc-templates/${templateId}/preview`, { caseId, values });
      if (previewUrl) URL.revokeObjectURL(previewUrl);
      setPreviewUrl(URL.createObjectURL(blob));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not build the preview");
    } finally {
      setBusy(null);
    }
  };

  const generate = async () => {
    if (!pdf && !docx) {
      setError("Choose PDF, Word or both.");
      return;
    }
    setBusy("generate");
    setError(null);
    try {
      const out = await apiPost<Generated>(`/api/v1/doc-templates/${templateId}/generate`, body());
      setResult(out);
      onDone();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not generate the document");
    } finally {
      setBusy(null);
    }
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Generate document"
      wide
      error={error}
      footer={
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="outline" onClick={onClose}>
            Close
          </Button>
          <Button variant="outline" disabled={!templateId || busy !== null} onClick={preview}>
            {busy === "preview" ? "Building preview..." : "Preview PDF"}
          </Button>
          <Button disabled={!templateId || busy !== null} onClick={generate}>
            {busy === "generate" ? "Generating..." : "Generate and save to matter"}
          </Button>
        </div>
      }
    >
      <div className="space-y-4 text-sm">
        <div>
          <Label htmlFor="gen-template">Template</Label>
          <Select id="gen-template" value={templateId} onChange={(e) => setTemplateId(e.target.value)}>
            <option value="">Choose a template</option>
            {Object.entries(groups).map(([cat, rows]) => (
              <optgroup key={cat} label={cat}>
                {rows.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                    {t.system ? "" : " (firm)"}
                  </option>
                ))}
              </optgroup>
            ))}
          </Select>
        </div>
        {form && (
          <>
            <p className="text-slate-600">
              Your letterhead fills {firmFields.length} firm field{firmFields.length === 1 ? "" : "s"} (logo, firm details, attorney and
              footer). Anything left blank below prints as the original blank line so it can be completed by hand.
            </p>
            {fillIns.length > 0 && (
              <div className="grid max-h-[40vh] grid-cols-1 gap-3 overflow-y-auto pr-1 sm:grid-cols-2">
                {fillIns.map((f) => (
                  <div key={f.key}>
                    <Label htmlFor={`gen-${f.key}`}>{f.label}</Label>
                    <Input
                      id={`gen-${f.key}`}
                      value={values[f.key] || ""}
                      onChange={(e) => setValues({ ...values, [f.key]: e.target.value })}
                    />
                  </div>
                ))}
              </div>
            )}
            <fieldset className="flex flex-wrap gap-4">
              <legend className="mb-1 text-sm font-semibold text-slate-700">Formats</legend>
              <label className="flex items-center gap-2">
                <input type="checkbox" checked={pdf} onChange={(e) => setPdf(e.target.checked)} /> PDF
              </label>
              <label className="flex items-center gap-2">
                <input type="checkbox" checked={docx} onChange={(e) => setDocx(e.target.checked)} /> Word (DOCX)
              </label>
              <label className="flex items-center gap-2">
                <input type="checkbox" checked={sign} onChange={(e) => setSign(e.target.checked)} /> Send for e-signature
              </label>
            </fieldset>
            {sign && (
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div>
                  <Label htmlFor="gen-signer-name">Signer name</Label>
                  <Input id="gen-signer-name" value={signerName} onChange={(e) => setSignerName(e.target.value)} />
                </div>
                <div>
                  <Label htmlFor="gen-signer-email">Signer email</Label>
                  <Input id="gen-signer-email" type="email" value={signerEmail} onChange={(e) => setSignerEmail(e.target.value)} />
                </div>
              </div>
            )}
            <div>
              <Label htmlFor="gen-email">Email the files to (optional)</Label>
              <Input id="gen-email" type="email" placeholder="client@example.co.za" value={emailTo} onChange={(e) => setEmailTo(e.target.value)} />
            </div>
          </>
        )}
        {previewUrl && (
          <iframe title="Document preview" src={previewUrl} className="h-[60vh] w-full rounded-lg border" />
        )}
        {result && (
          <div role="status" className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-emerald-900">
            <p className="font-semibold">Saved to the matter&apos;s files: {result.documents.map((d) => d.name).join(", ")}</p>
            {result.signature?.signUrl && <p>E-signature request created. Signing link: {result.signature.signUrl}</p>}
            {result.email && <p>{result.email.sent ? `Emailed to ${result.email.to}.` : `Not emailed: ${result.email.reason}`}</p>}
          </div>
        )}
      </div>
    </Dialog>
  );
}
