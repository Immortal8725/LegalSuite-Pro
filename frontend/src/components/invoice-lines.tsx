"use client";

import type { ReactNode } from "react";
import { moneyExact } from "@/lib/utils";

export type InvoiceLine = {
  kind?: string;
  description?: string;
  minutes?: number;
  amount?: number | string;
  category?: string;
  date?: string;
  vendor?: string;
};

export function invoiceLines(raw?: string | null): InvoiceLine[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((row) => row && typeof row === "object") as InvoiceLine[];
  } catch {
    return [];
  }
}

function showDate(value?: string) {
  if (!value) return "";
  return value.length >= 10 ? value.slice(0, 10) : value;
}

function Section({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <section>
      <h3 className="text-xs font-bold uppercase tracking-wide text-slate-500">{title}</h3>
      <table className="mt-2 w-full text-sm">{children}</table>
    </section>
  );
}

export function InvoiceLines({ raw }: { raw?: string | null }) {
  const lines = invoiceLines(raw);
  const fees = lines.filter((line) => line.kind !== "expense");
  const disbursements = lines.filter((line) => line.kind === "expense");
  if (lines.length === 0) {
    return <p className="px-4 py-3 text-sm text-slate-500">No line items.</p>;
  }
  return (
    <div className="space-y-4 bg-slate-50 px-4 py-3">
      {fees.length > 0 && (
        <Section title="Fees">
          <thead>
            <tr className="text-left text-[11px] uppercase tracking-wide text-slate-400">
              <th className="py-1 pr-3 font-semibold">Date</th>
              <th className="py-1 pr-3 font-semibold">Description</th>
              <th className="py-1 pr-3 font-semibold">Time</th>
              <th className="py-1 text-right font-semibold">Amount</th>
            </tr>
          </thead>
          <tbody>
            {fees.map((line, index) => (
              <tr key={`fee-${index}`} className="border-t border-slate-200">
                <td className="py-1.5 pr-3">{showDate(line.date)}</td>
                <td className="py-1.5 pr-3">{line.description}</td>
                <td className="py-1.5 pr-3">{line.minutes != null ? `${line.minutes} min` : ""}</td>
                <td className="py-1.5 text-right">{moneyExact(line.amount)}</td>
              </tr>
            ))}
          </tbody>
        </Section>
      )}
      {disbursements.length > 0 && (
        <Section title="Disbursements">
          <thead>
            <tr className="text-left text-[11px] uppercase tracking-wide text-slate-400">
              <th className="py-1 pr-3 font-semibold">Date</th>
              <th className="py-1 pr-3 font-semibold">Description</th>
              <th className="py-1 pr-3 font-semibold">Vendor</th>
              <th className="py-1 text-right font-semibold">Amount</th>
            </tr>
          </thead>
          <tbody>
            {disbursements.map((line, index) => (
              <tr key={`disbursement-${index}`} className="border-t border-slate-200">
                <td className="py-1.5 pr-3">{showDate(line.date)}</td>
                <td className="py-1.5 pr-3">
                  {line.description}
                  {line.category ? <span className="ml-2 text-xs text-slate-400">{line.category}</span> : null}
                </td>
                <td className="py-1.5 pr-3">{line.vendor || ""}</td>
                <td className="py-1.5 text-right">{moneyExact(line.amount)}</td>
              </tr>
            ))}
          </tbody>
        </Section>
      )}
    </div>
  );
}
