"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost, api } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { Button } from "@/components/ui/button";
import { Badge, Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Input, Label } from "@/components/ui/input";
import type { FieldReference, LibraryTemplate } from "@/lib/types";

/** The firm-branded template library: system templates, the firm's own Word templates and the field list. */
export function BrandedLibrary() {
  const { user } = useAuth();
  const isAdmin = !!user && ["owner", "admin", "partner", "director"].includes(user.role);
  const [rows, setRows] = useState<LibraryTemplate[]>([]);
  const [fields, setFields] = useState<FieldReference[]>([]);
  const [name, setName] = useState("");
  const [category, setCategory] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = () => apiGet<LibraryTemplate[]>("/api/v1/doc-templates").then(setRows).catch((e) => setError(e.message));
  useEffect(() => {
    load();
    apiGet<FieldReference[]>("/api/v1/doc-templates/fields").then(setFields).catch(() => undefined);
  }, []);

  return (
    <Card className="mb-6">
      <CardHeader>
        <CardTitle>Branded document library</CardTitle>
      </CardHeader>
      <CardBody className="space-y-4 text-sm">
        <p className="text-slate-600">
          These print on your letterhead with your logo, firm details, the signed-in attorney and your footer. Generate them from a
          matter (Files, then Generate document). Set the letterhead under Settings.
        </p>
        <ErrorBanner error={error} />
        {notice && (
          <p role="status" className="text-emerald-700">
            {notice}
          </p>
        )}
        <ul className="divide-y rounded-lg border">
          {rows.map((t) => (
            <li key={t.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
              <div className="min-w-0">
                <p className="font-semibold text-navy">{t.name}</p>
                <p className="text-xs text-slate-600">
                  {t.category} · {t.fillInCount} fill-in field{t.fillInCount === 1 ? "" : "s"}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Badge>{t.system ? "Owlaw" : "Firm"}</Badge>
                {!t.system && isAdmin && (
                  <Button
                    size="sm"
                    variant="ghost"
                    aria-label={`Remove ${t.name}`}
                    onClick={async () => {
                      setError(null);
                      try {
                        await api(`/api/v1/doc-templates/${t.id}`, { method: "DELETE" });
                        load();
                      } catch (e) {
                        setError(e instanceof Error ? e.message : "Could not remove the template");
                      }
                    }}
                  >
                    Remove
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
        <form
          className="grid grid-cols-1 gap-3 rounded-lg border p-3 sm:grid-cols-3"
          onSubmit={async (e) => {
            e.preventDefault();
            if (!file) {
              setError("Choose a Word (.docx) file.");
              return;
            }
            setError(null);
            try {
              const fd = new FormData();
              fd.append("file", file);
              if (name) fd.append("name", name);
              if (category) fd.append("category", category);
              const out = await apiPost<{ name: string; unknownFields: string[] }>("/api/v1/doc-templates/upload", fd);
              setNotice(
                `${out.name} added.` +
                  (out.unknownFields.length ? ` Fill-in fields: ${out.unknownFields.join(", ")}.` : "")
              );
              setName("");
              setCategory("");
              setFile(null);
              load();
            } catch (err) {
              setError(err instanceof Error ? err.message : "Could not upload the template");
            }
          }}
        >
          <div>
            <Label htmlFor="tpl-name">Template name</Label>
            <Input id="tpl-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Letter of demand" />
          </div>
          <div>
            <Label htmlFor="tpl-category">Category</Label>
            <Input id="tpl-category" value={category} onChange={(e) => setCategory(e.target.value)} placeholder="Litigation" />
          </div>
          <div>
            <Label htmlFor="tpl-file">Word file (.docx)</Label>
            <input
              id="tpl-file"
              type="file"
              accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
              onChange={(e) => setFile(e.target.files?.[0] || null)}
            />
          </div>
          <div className="sm:col-span-3">
            <Button type="submit">Upload your own template</Button>
          </div>
        </form>
        <details>
          <summary className="cursor-pointer font-semibold text-navy">Fields you can use in your own templates</summary>
          <p className="mt-2 text-slate-600">
            Type a field in double braces, for example {"{{firm_name}}"}. Any other name, such as {"{{debtor_name}}"} or a
            [BRACKET PROMPT], becomes a box to fill in when the document is generated. {"{{name:____}}"} prints the blank line if
            left empty.
          </p>
          <table className="mt-2 w-full text-left text-xs">
            <thead>
              <tr>
                <th className="py-1 pr-2">Field</th>
                <th className="py-1 pr-2">Filled from</th>
                <th className="py-1">Meaning</th>
              </tr>
            </thead>
            <tbody>
              {fields.map((f) => (
                <tr key={f.key} className="border-t">
                  <td className="py-1 pr-2 font-mono">{`{{${f.key}}}`}</td>
                  <td className="py-1 pr-2">{f.group}</td>
                  <td className="py-1">{f.description}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </details>
      </CardBody>
    </Card>
  );
}
