import { PageHeader } from "@/components/page";

export function fmtDate(v?: string | null, withTime = false) {
  if (!v) return "-";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleString("en-ZA", {
    timeZone: "Africa/Johannesburg",
    day: "numeric",
    month: "short",
    year: "numeric",
    ...(withTime ? { hour: "2-digit", minute: "2-digit" } : {}),
  });
}

export function rand(v?: number | string | null) {
  const n = Number(v || 0);
  return "R" + n.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function OperatorOnly({ title }: { title: string }) {
  return (
    <div>
      <PageHeader title={title} subtitle="This screen is for the Owlaw operator." />
      <p className="text-sm text-slate-600">Sign in as the platform operator to use it.</p>
    </div>
  );
}
