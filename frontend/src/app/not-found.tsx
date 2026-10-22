import Link from "next/link";
import { Scale } from "lucide-react";

export const metadata = { title: "Page not found" };

export default function NotFound() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-50 px-6">
      <div className="w-full max-w-md rounded-2xl border bg-white p-8 text-center shadow-card">
        <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-gold text-navy-dark">
          <Scale className="h-5 w-5" aria-hidden="true" />
        </span>
        <p className="mt-4 text-sm font-bold uppercase tracking-wide text-gold-dark">Owlaw</p>
        <h1 className="mt-2 text-2xl font-extrabold text-navy">We could not find that page</h1>
        <p className="mt-2 text-sm text-slate-600">
          The link may be old or mistyped, or the page may have moved. Nothing has been changed.
        </p>
        <div className="mt-6 flex justify-center gap-3">
          <Link href="/dashboard" className="rounded-lg bg-navy px-4 py-2 text-sm font-semibold text-white">
            Go to the dashboard
          </Link>
          <Link href="/" className="rounded-lg border px-4 py-2 text-sm font-semibold text-navy">
            Owlaw home
          </Link>
        </div>
      </div>
    </main>
  );
}
