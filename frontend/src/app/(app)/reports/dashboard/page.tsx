"use client";

import { useEffect, useState } from "react";
import { apiBlob, apiGet, saveBlob } from "@/lib/api";
import { moneyExact } from "@/lib/utils";
import { Button, PageHeader } from "@/components/page";
import { ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select } from "@/components/ui/input";

type D = {
  wip: number;
  wipHours: number;
  debtors: number;
  ageing: Record<string, number>;
  topDebtors: { client: string; owing: number }[];
  months: { month: string; billed: number; collected: number }[];
  billed12: number;
  collected12: number;
  collectionRate: number | null;
  perAttorney90: { attorney: string; minutes: number; billableValue: number }[];
  openMatters: number;
  openByArea: Record<string, number>;
};

function Tile({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div className="rounded border bg-white p-4">
      <p className="text-xs uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold">{value}</p>
      {note && <p className="text-xs text-slate-500">{note}</p>}
    </div>
  );
}

export default function PracticeDashboard() {
  const [d, setD] = useState<D | null>(null);
  const [error, setError] = useState<string | null>(null);
  const y = new Date().getFullYear();
  const [exp, setExp] = useState({ format: "xero", from: `${y}-01-01`, to: new Date().toISOString().slice(0, 10), accountCode: "" });

  useEffect(() => {
    apiGet<D>("/api/v1/reports/practice").then(setD).catch((e) => setError(e instanceof Error ? e.message : "Could not load"));
  }, []);

  const max = d ? Math.max(1, ...d.months.map((m) => Math.max(Number(m.billed), Number(m.collected)))) : 1;
  const ageMax = d ? Math.max(1, ...Object.values(d.ageing).map(Number)) : 1;

  return (
    <div>
      <PageHeader title="Practice dashboard" subtitle="Work in progress, debtors, billing and collections at a glance." />
      <ErrorBanner error={error} />
      {d && (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Tile label="Unbilled work (WIP)" value={moneyExact(d.wip)} note={`${d.wipHours} hours not yet invoiced`} />
            <Tile label="Debtors" value={moneyExact(d.debtors)} note="Owed on sent invoices" />
            <Tile label="Billed, last 12 months" value={moneyExact(d.billed12)} />
            <Tile label="Collected, last 12 months" value={moneyExact(d.collected12)} note={d.collectionRate === null ? "" : `${d.collectionRate}% of billed`} />
          </div>
          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <section className="rounded border bg-white p-4">
              <h2 className="mb-3 font-semibold">Billed and collected by month</h2>
              <div className="flex h-40 items-end gap-1" role="img" aria-label="Monthly billed and collected">
                {d.months.map((m) => (
                  <div key={m.month} className="flex flex-1 items-end gap-0.5" title={`${m.month}: billed ${moneyExact(m.billed)}, collected ${moneyExact(m.collected)}`}>
                    <div className="w-1/2 bg-navy" style={{ height: `${(Number(m.billed) / max) * 100}%` }} />
                    <div className="w-1/2 bg-emerald-500" style={{ height: `${(Number(m.collected) / max) * 100}%` }} />
                  </div>
                ))}
              </div>
              <div className="mt-1 flex justify-between text-[10px] text-slate-500"><span>{d.months[0]?.month}</span><span>{d.months[d.months.length - 1]?.month}</span></div>
              <p className="mt-2 text-xs"><span className="mr-1 inline-block h-2 w-2 bg-navy" />Billed <span className="ml-3 mr-1 inline-block h-2 w-2 bg-emerald-500" />Collected</p>
            </section>
            <section className="rounded border bg-white p-4">
              <h2 className="mb-3 font-semibold">Debtors ageing</h2>
              {Object.entries(d.ageing).map(([k, v]) => (
                <div key={k} className="mb-2 text-sm">
                  <div className="flex justify-between"><span>{k === "current" ? "0 to 30 days" : `${k} days`}</span><span>{moneyExact(v)}</span></div>
                  <div className="h-2 rounded bg-slate-100"><div className={`h-2 rounded ${k === "90+" ? "bg-red-500" : "bg-amber-500"}`} style={{ width: `${(Number(v) / ageMax) * 100}%` }} /></div>
                </div>
              ))}
              <h3 className="mb-1 mt-4 text-sm font-semibold">Largest balances</h3>
              <ul className="text-sm">{d.topDebtors.map((t) => <li key={t.client} className="flex justify-between"><span>{t.client}</span><span>{moneyExact(t.owing)}</span></li>)}</ul>
            </section>
            <section className="rounded border bg-white p-4">
              <h2 className="mb-3 font-semibold">Time recorded, last 90 days</h2>
              <table className="w-full text-sm"><tbody>
                {d.perAttorney90.map((a) => (
                  <tr key={a.attorney}><td>{a.attorney}</td><td className="text-right">{(a.minutes / 60).toFixed(1)} h</td><td className="text-right">{moneyExact(a.billableValue)}</td></tr>
                ))}
              </tbody></table>
            </section>
            <section className="rounded border bg-white p-4">
              <h2 className="mb-3 font-semibold">Open matters: {d.openMatters}</h2>
              <ul className="text-sm">{Object.entries(d.openByArea).map(([k, v]) => <li key={k} className="flex justify-between"><span>{k}</span><span>{v}</span></li>)}</ul>
            </section>
          </div>
        </>
      )}
      <section className="mt-6 rounded border bg-white p-4">
        <h2 className="mb-1 font-semibold">Export invoices to your accounting system</h2>
        <p className="mb-3 text-sm text-slate-600">Download sent invoices as a CSV ready for Xero or Sage Business Cloud Accounting. Import it as sales or customer invoices; check the sales account code matches your chart of accounts.</p>
        <div className="flex flex-wrap items-end gap-3">
          <div><Label>System</Label><Select value={exp.format} onChange={(e) => setExp({ ...exp, format: e.target.value })}><option value="xero">Xero</option><option value="sage">Sage Business Cloud</option></Select></div>
          <div><Label>From</Label><Input type="date" value={exp.from} onChange={(e) => setExp({ ...exp, from: e.target.value })} /></div>
          <div><Label>To</Label><Input type="date" value={exp.to} onChange={(e) => setExp({ ...exp, to: e.target.value })} /></div>
          <div><Label>Sales account code</Label><Input value={exp.accountCode} placeholder={exp.format === "xero" ? "200" : "1000"} onChange={(e) => setExp({ ...exp, accountCode: e.target.value })} /></div>
          <Button onClick={async () => {
            const q = new URLSearchParams({ format: exp.format, from: exp.from, to: exp.to, ...(exp.accountCode ? { accountCode: exp.accountCode } : {}) });
            saveBlob(await apiBlob(`/api/v1/exports/accounting.csv?${q}`), `${exp.format}-invoices-${exp.from}-${exp.to}.csv`);
          }}>Download CSV</Button>
        </div>
      </section>
    </div>
  );
}
