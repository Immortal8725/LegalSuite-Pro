"use client";

import { useEffect, useState } from "react";
import { apiBlob, apiGet, apiPost, saveBlob } from "@/lib/api";
import { moneyExact } from "@/lib/utils";
import { Button, PageHeader, TableWrap, Td, Th } from "@/components/page";
import { Dialog, ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select } from "@/components/ui/input";

type Exc = { severity: string; check: string; detail: string };
type Acct = {
  account: string;
  opening: number;
  receipts: number;
  payments: number;
  closing: number;
  threeWay: { bank: number; cashbook: number; clientLedgers: number; balanced: boolean };
  clientLedgers: { client: string; balance: number; lastMovement?: string }[];
  reconciliations: { periodEnd: string; difference: number; status: string; certified: boolean }[];
};
type Inv = { id: string; section: string; client: string; institution?: string; principal: number; interestEarned: number; interestPaidOut: number; openedOn: string; closedOn?: string };
type Pack = { firm: string; accounts: Acct[]; exceptions: Exc[]; investments: Inv[]; interest86_3: number; interest86_4: number };
type Client = { id: string; displayName?: string; name?: string; firstName?: string; lastName?: string; companyName?: string };

function defaultPeriod() {
  // Trust audit years usually end on the last day of February.
  const now = new Date();
  const y = now.getMonth() >= 2 ? now.getFullYear() : now.getFullYear() - 1;
  return { from: `${y}-03-01`, to: now.toISOString().slice(0, 10) };
}

const SEV: Record<string, string> = { high: "bg-red-100 text-red-800", medium: "bg-amber-100 text-amber-800", low: "bg-slate-100 text-slate-700" };

export default function TrustAuditPage() {
  const [period, setPeriod] = useState(defaultPeriod());
  const [pack, setPack] = useState<Pack | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [clients, setClients] = useState<Client[]>([]);
  const [open, setOpen] = useState(false);
  const [inv, setInv] = useState({ section: "86(4)", clientId: "", institution: "", accountNumber: "", principal: "", interestEarned: "0", interestPaidOut: "0", openedOn: "" });
  const [dlgErr, setDlgErr] = useState<string | null>(null);
  const q = `from=${period.from}&to=${period.to}`;

  const load = () => apiGet<Pack>(`/api/v1/trust/audit-pack?${q}`).then((p) => { setPack(p); setError(null); }).catch((e) => setError(e instanceof Error ? e.message : "Could not build the pack"));
  useEffect(() => {
    load();
    apiGet<Client[]>("/api/v1/clients").then(setClients).catch(() => setClients([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const name = (c: Client) => c.displayName || c.name || c.companyName || `${c.firstName ?? ""} ${c.lastName ?? ""}`.trim();

  return (
    <div>
      <PageHeader title="Trust audit pack" subtitle="Section 86 reports for your auditor: cashbook, client ledgers, reconciliations, three-way check, investments and exceptions." />
      <ErrorBanner error={error} />
      <div className="mb-6 flex flex-wrap items-end gap-3 rounded border bg-white p-4">
        <div><Label>From</Label><Input type="date" value={period.from} onChange={(e) => setPeriod({ ...period, from: e.target.value })} /></div>
        <div><Label>To</Label><Input type="date" value={period.to} onChange={(e) => setPeriod({ ...period, to: e.target.value })} /></div>
        <Button onClick={load}>Build pack</Button>
        <Button variant="outline" onClick={async () => saveBlob(await apiBlob(`/api/v1/trust/audit-pack.pdf?${q}`), `trust-audit-pack-${period.from}-${period.to}.pdf`)}>Download PDF</Button>
        <Button variant="outline" onClick={async () => saveBlob(await apiBlob(`/api/v1/trust/audit-pack.csv?${q}`), `trust-audit-pack-${period.from}-${period.to}.csv`)}>Download CSV</Button>
      </div>
      {pack && (
        <>
          <h2 className="mb-2 font-semibold">Exceptions ({pack.exceptions.length})</h2>
          {pack.exceptions.length === 0 ? <p className="mb-6 text-sm text-emerald-700">No exceptions found.</p> : (
            <ul className="mb-6 space-y-1 text-sm">
              {pack.exceptions.map((e, i) => (
                <li key={i} className="flex gap-2"><span className={`rounded px-2 text-xs leading-5 ${SEV[e.severity]}`}>{e.severity}</span><strong>{e.check}:</strong> {e.detail}</li>
              ))}
            </ul>
          )}
          {pack.accounts.map((a) => (
            <div key={a.account} className="mb-6 rounded border bg-white p-4">
              <h3 className="font-semibold">{a.account}</h3>
              <p className="mt-1 text-sm">Opening {moneyExact(a.opening)}, receipts {moneyExact(a.receipts)}, payments {moneyExact(a.payments)}, closing {moneyExact(a.closing)}.</p>
              <p className={`mt-1 text-sm ${a.threeWay.balanced ? "text-emerald-700" : "text-red-700"}`}>
                Three-way at {period.to}: bank {moneyExact(a.threeWay.bank)}, cashbook {moneyExact(a.threeWay.cashbook)}, client ledgers {moneyExact(a.threeWay.clientLedgers)}. {a.threeWay.balanced ? "Balanced." : "Does not balance."}
              </p>
              <p className="mt-1 text-xs text-slate-500">{a.clientLedgers.length} client ledgers, {a.reconciliations.length} reconciliations in the period.</p>
            </div>
          ))}
          <div className="mb-2 flex items-center justify-between">
            <h2 className="font-semibold">Section 86(3) and 86(4) investments</h2>
            <Button size="sm" onClick={() => { setDlgErr(null); setOpen(true); }}>Add investment</Button>
          </div>
          <TableWrap>
            <thead><tr><Th>Section</Th><Th>Client</Th><Th>Institution</Th><Th>Principal</Th><Th>Interest</Th><Th>Paid out</Th><Th>Opened</Th></tr></thead>
            <tbody>
              {pack.investments.map((i) => (
                <tr key={i.id}><Td>{i.section}</Td><Td>{i.client}</Td><Td>{i.institution ?? ""}</Td><Td>{moneyExact(i.principal)}</Td><Td>{moneyExact(i.interestEarned)}</Td><Td>{moneyExact(i.interestPaidOut)}</Td><Td>{i.openedOn}</Td></tr>
              ))}
            </tbody>
          </TableWrap>
          <p className="mt-2 text-xs text-slate-500">Interest on 86(3): {moneyExact(pack.interest86_3)} (to the Fidelity Fund). Interest on 86(4): {moneyExact(pack.interest86_4)} (to the clients).</p>
        </>
      )}
      <Dialog open={open} onClose={() => setOpen(false)} title="Trust investment" footer={
        <Button onClick={async () => {
          try {
            await apiPost("/api/v1/trust/investments", { ...inv, clientId: inv.clientId || undefined, openedOn: inv.openedOn || undefined });
            setOpen(false);
            load();
          } catch (e) { setDlgErr(e instanceof Error ? e.message : "Could not save"); }
        }}>Save</Button>
      }>
        <div className="grid gap-3 text-sm">
          <ErrorBanner error={dlgErr} />
          <div><Label>Section</Label><Select value={inv.section} onChange={(e) => setInv({ ...inv, section: e.target.value })}>
            <option value="86(4)">86(4): on a client&apos;s instruction, interest to the client</option>
            <option value="86(3)">86(3): surplus trust money, interest to the Fidelity Fund</option>
          </Select></div>
          <div><Label>Client</Label><Select value={inv.clientId} onChange={(e) => setInv({ ...inv, clientId: e.target.value })}>
            <option value="">None (86(3) only)</option>
            {clients.map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
          </Select></div>
          <div className="grid grid-cols-2 gap-3">
            <div><Label>Bank</Label><Input value={inv.institution} onChange={(e) => setInv({ ...inv, institution: e.target.value })} /></div>
            <div><Label>Account number</Label><Input value={inv.accountNumber} onChange={(e) => setInv({ ...inv, accountNumber: e.target.value })} /></div>
            <div><Label>Principal (R)</Label><Input value={inv.principal} onChange={(e) => setInv({ ...inv, principal: e.target.value })} /></div>
            <div><Label>Opened on</Label><Input type="date" value={inv.openedOn} onChange={(e) => setInv({ ...inv, openedOn: e.target.value })} /></div>
            <div><Label>Interest earned (R)</Label><Input value={inv.interestEarned} onChange={(e) => setInv({ ...inv, interestEarned: e.target.value })} /></div>
            <div><Label>Interest paid out (R)</Label><Input value={inv.interestPaidOut} onChange={(e) => setInv({ ...inv, interestPaidOut: e.target.value })} /></div>
          </div>
        </div>
      </Dialog>
    </div>
  );
}
