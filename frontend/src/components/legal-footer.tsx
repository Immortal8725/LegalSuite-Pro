import Link from "next/link";
import { LEGAL_DOCS } from "@/lib/legal";
import { cn } from "@/lib/utils";

export function LegalFooter({ className }: { className?: string }) {
  return (
    <footer className={cn("border-t bg-white px-6 py-10", className)}>
      <div className="mx-auto max-w-6xl">
        <p className="text-xs font-bold uppercase tracking-wide text-slate-400">LegalSuite Pro</p>
        <nav className="mt-4 flex flex-col gap-2 text-sm" aria-label="Legal">
          {LEGAL_DOCS.map((d) => (
            <Link
              key={d.slug}
              href={`/legal/${d.slug}`}
              className="w-fit font-semibold text-sky-600 hover:text-navy hover:underline"
            >
              {d.title}
            </Link>
          ))}
        </nav>
        <p className="mt-6 text-xs text-slate-400">
          Starting texts, not a substitute for counsel. Attorney-client privilege stays on your tenant row. Call
          recording is opt-in.
        </p>
      </div>
    </footer>
  );
}
