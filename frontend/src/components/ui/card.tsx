import { cn } from "@/lib/utils";

export function Card({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("rounded-xl bg-white shadow-card", className)} {...props} />;
}

export function CardHeader({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("flex items-center justify-between border-b border-slate-100 px-5 py-4", className)} {...props} />;
}

export function CardTitle({ className, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return <h3 className={cn("text-sm font-bold text-slate-900", className)} {...props} />;
}

export function CardBody({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("p-5", className)} {...props} />;
}

export function Badge({
  children,
  tone = "navy",
}: {
  children: React.ReactNode;
  tone?: "navy" | "green" | "gold" | "red" | "slate" | "purple" | "blue";
}) {
  const map = {
    navy: "bg-slate-100 text-navy",
    green: "bg-emerald-50 text-emerald-700",
    gold: "bg-amber-50 text-amber-800",
    red: "bg-red-50 text-red-700",
    slate: "bg-slate-100 text-slate-600",
    purple: "bg-violet-50 text-violet-700",
    blue: "bg-sky-50 text-sky-700",
  };
  return (
    <span className={`inline-flex rounded-full px-2.5 py-0.5 text-[11px] font-bold uppercase tracking-wide ${map[tone]}`}>
      {children}
    </span>
  );
}
