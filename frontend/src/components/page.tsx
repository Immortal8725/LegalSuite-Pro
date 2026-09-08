import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";

export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold text-navy">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
      </div>
      {actions}
    </div>
  );
}

export function StatusBadge({ status }: { status: string }) {
  const tone: Record<string, string> = {
    open: "bg-emerald-50 text-emerald-700",
    discovery: "bg-sky-50 text-sky-700",
    pending: "bg-amber-50 text-amber-800",
    mediation: "bg-violet-50 text-violet-700",
    trial: "bg-red-50 text-red-700",
    closed: "bg-slate-100 text-slate-600",
    settled: "bg-emerald-50 text-emerald-700",
    draft: "bg-slate-100 text-slate-600",
    sent: "bg-sky-50 text-sky-700",
    paid: "bg-emerald-50 text-emerald-700",
    overdue: "bg-red-50 text-red-700",
    todo: "bg-slate-100 text-slate-600",
    in_progress: "bg-sky-50 text-sky-700",
    completed: "bg-emerald-50 text-emerald-700",
    ringing: "bg-amber-50 text-amber-800",
    answered: "bg-sky-50 text-sky-700",
    missed: "bg-red-50 text-red-700",
    active: "bg-emerald-50 text-emerald-700",
    lead: "bg-amber-50 text-amber-800",
    clear: "bg-emerald-50 text-emerald-700",
    cleared: "bg-emerald-50 text-emerald-700",
    potential_conflict: "bg-red-50 text-red-700",
    adverse: "bg-red-50 text-red-700",
    related: "bg-amber-50 text-amber-800",
    counsel: "bg-violet-50 text-violet-700",
    notice: "bg-red-50 text-red-700",
    contacted: "bg-sky-50 text-sky-700",
    consultation: "bg-violet-50 text-violet-700",
    retained: "bg-emerald-50 text-emerald-700",
    limited: "bg-amber-50 text-amber-800",
    unsigned: "bg-amber-50 text-amber-800",
    declined: "bg-slate-100 text-slate-600",
    issued: "bg-sky-50 text-sky-700",
    signed: "bg-emerald-50 text-emerald-700",
    void: "bg-slate-100 text-slate-600",
    connected: "bg-emerald-50 text-emerald-700",
  };
  return (
    <span className={`inline-flex rounded-full px-2.5 py-0.5 text-[11px] font-bold uppercase ${tone[status] || "bg-slate-100 text-slate-600"}`}>
      {status.replaceAll("_", " ")}
    </span>
  );
}

export function TableWrap({ children }: { children: ReactNode }) {
  return (
    <div className="overflow-x-auto rounded-xl bg-white shadow-card">
      <table className="w-full text-sm">{children}</table>
    </div>
  );
}

export function Th({ children }: { children: ReactNode }) {
  return (
    <th className="bg-slate-50 px-4 py-3 text-left text-[11px] font-bold uppercase tracking-wide text-slate-400">
      {children}
    </th>
  );
}

export function Td({ children, className }: { children: ReactNode; className?: string }) {
  return <td className={`border-t border-slate-100 px-4 py-3 ${className || ""}`}>{children}</td>;
}

export function Loading() {
  return <p className="py-16 text-center text-sm text-slate-500">Loading…</p>;
}

export { Button };
