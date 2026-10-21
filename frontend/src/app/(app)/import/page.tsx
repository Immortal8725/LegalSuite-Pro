"use client";

import { useEffect, useState } from "react";
import { apiBlob, apiGet, apiPost, saveBlob } from "@/lib/api";
import { Button, PageHeader, TableWrap, Td, Th } from "@/components/page";
import { ErrorBanner } from "@/components/ui/dialog";
import { Label, Select } from "@/components/ui/input";

type Kind = { kind: string; columns: string[] };
type Row = { line: number; values: Record<string, string>; errors: string[] };
type Result = { kind: string; rows: number; valid: number; invalid: number; imported: number; dryRun: boolean; preview: Row[]; message?: string };
type Account = { id: string; accountName: string };

const LABELS: Record<string, string> = {
  clients: "Clients",
  contacts: "Contacts",
  matters: "Matters",
  trust_opening: "Opening trust balances",
  time: "Time entries",
};

export default function ImportPage() {
  const [kinds, setKinds] = useState<Kind[]>([]);
  const [kind, setKind] = useState("clients");
  const [csv, setCsv] = useState("");
  const [fileName, setFileName] = useState("");
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [account, setAccount] = useState("");
  const [result, setResult] = useState<Result | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    apiGet<Kind[]>("/api/v1/import/kinds").then(setKinds).catch((e) => setError(e instanceof Error ? e.message : "Could not load"));
    apiGet<Account[]>("/api/v1/trust/accounts")
      .then((a) => {
        setAccounts(a);
        if (a[0]) setAccount(a[0].id);
      })
      .catch(() => setAccounts([]));
  }, []);

  async function go(dryRun: boolean) {
    setBusy(true);
    setError(null);
    try {
      const r = await apiPost<Result>(`/api/v1/import/${kind}`, { csv, dryRun, trustAccountId: kind === "trust_opening" ? account : undefined });
      setResult(r);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Import failed");
    } finally {
      setBusy(false);
    }
  }

  const cols = kinds.find((k) => k.kind === kind)?.columns ?? [];

  return (
    <div>
      <PageHeader title="Import data" subtitle="Bring clients, matters, contacts, opening trust balances and time across from your old system." />
      <ErrorBanner error={error} />
      <ol className="mb-6 list-decimal space-y-1 pl-5 text-sm text-slate-600">
        <li>Choose what to import and download the template. Import clients first, then matters, then trust and time.</li>
        <li>Fill it in with Excel or Google Sheets and save as CSV.</li>
        <li>Upload it and press <strong>Check file</strong>. Nothing is saved yet.</li>
        <li>When every row is green, press <strong>Import</strong>. If any row has an error, nothing is imported.</li>
      </ol>
      <div className="grid max-w-2xl gap-4 rounded border bg-white p-4">
        <div>
          <Label>What are you importing?</Label>
          <Select value={kind} onChange={(e) => { setKind(e.target.value); setResult(null); }}>
            {kinds.map((k) => (
              <option key={k.kind} value={k.kind}>{LABELS[k.kind] ?? k.kind}</option>
            ))}
          </Select>
          <p className="mt-1 text-xs text-slate-500">Columns: {cols.join(", ")}</p>
        </div>
        {kind === "trust_opening" && (
          <div>
            <Label>Trust account</Label>
            <Select value={account} onChange={(e) => setAccount(e.target.value)}>
              {accounts.map((a) => (
                <option key={a.id} value={a.id}>{a.accountName}</option>
              ))}
            </Select>
            <p className="mt-1 text-xs text-slate-500">Use the client balances from your last certified reconciliation, so the three-way check balances.</p>
          </div>
        )}
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="outline" onClick={async () => saveBlob(await apiBlob(`/api/v1/import/templates/${kind}`), `owlaw-import-${kind}.csv`)}>
            Download template
          </Button>
          <label className="cursor-pointer rounded border px-3 py-2 text-sm">
            {fileName || "Choose CSV file"}
            <input type="file" accept=".csv,text/csv" className="hidden" onChange={async (e) => {
              const f = e.target.files?.[0];
              if (!f) return;
              setFileName(f.name);
              setCsv(await f.text());
              setResult(null);
            }} />
          </label>
        </div>
        <div className="flex gap-3">
          <Button disabled={!csv || busy} variant="outline" onClick={() => go(true)}>Check file</Button>
          <Button disabled={!csv || busy || !result || !result.dryRun || result.invalid > 0} onClick={() => go(false)}>
            Import {result && result.invalid === 0 ? `${result.valid} rows` : ""}
          </Button>
        </div>
      </div>
      {result && (
        <div className="mt-6">
          <p className="mb-2 text-sm">
            {result.dryRun
              ? `${result.rows} rows checked: ${result.valid} ready, ${result.invalid} with errors.`
              : result.imported > 0
                ? `Done. ${result.imported} rows imported and recorded in the audit log.`
                : result.message}
          </p>
          <TableWrap>
            <thead>
              <tr><Th>Line</Th>{cols.slice(0, 5).map((c) => <Th key={c}>{c}</Th>)}<Th>Check</Th></tr>
            </thead>
            <tbody>
              {result.preview.map((r) => (
                <tr key={r.line} className={r.errors.length ? "bg-red-50" : ""}>
                  <Td>{r.line}</Td>
                  {cols.slice(0, 5).map((c) => <Td key={c}>{r.values[c]}</Td>)}
                  <Td className={r.errors.length ? "text-red-700" : "text-emerald-700"}>{r.errors.length ? r.errors.join(" ") : "OK"}</Td>
                </tr>
              ))}
            </tbody>
          </TableWrap>
        </div>
      )}
    </div>
  );
}
