"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { Scale } from "lucide-react";
import { LEGAL_DOCS, legalBySlug } from "@/lib/legal";
import { LegalFooter } from "@/components/legal-footer";

export default function LegalPage() {
  const params = useParams<{ slug: string }>();
  const doc = legalBySlug(params.slug);

  if (!doc) {
    return (
      <div className="p-8">
        <p className="text-sm text-slate-600">That policy is not in the pack.</p>
        <Link href="/legal/privacy" className="mt-3 inline-block font-semibold text-sky-600">
          Privacy & Cookie Policy
        </Link>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-white">
      <nav className="mx-auto flex max-w-3xl items-center justify-between px-6 py-5">
        <Link href="/" className="flex items-center gap-2 font-extrabold text-navy">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gold text-navy-dark">
            <Scale className="h-4 w-4" />
          </span>
          Legal<span className="text-gold">Suite</span> Pro
        </Link>
        <Link href="/legal/privacy" className="text-sm font-semibold text-sky-600">
          Legal pack
        </Link>
      </nav>
      <article className="mx-auto max-w-3xl px-6 pb-16">
        <p className="text-xs font-bold uppercase tracking-wide text-slate-400">PRODUCT.md §{doc.section}</p>
        <h1 className="mt-2 text-3xl font-extrabold text-navy">{doc.title}</h1>
        <p className="mt-2 text-sm text-slate-500">
          Effective 16 September 2026. Binding long-form text is in PRODUCT.md. This page is the public copy.
        </p>
        <aside className="mt-8 rounded-xl border bg-slate-50 p-4">
          <p className="text-xs font-bold uppercase tracking-wide text-slate-400">Also in this pack</p>
          <ul className="mt-2 columns-1 gap-x-8 text-sm sm:columns-2">
            {LEGAL_DOCS.map((d) => (
              <li key={d.slug} className="break-inside-avoid py-0.5">
                <Link
                  href={`/legal/${d.slug}`}
                  className={d.slug === doc.slug ? "font-bold text-sky-600" : "text-sky-600 hover:underline"}
                >
                  {d.title}
                </Link>
              </li>
            ))}
          </ul>
        </aside>
        <pre className="mt-8 whitespace-pre-wrap font-sans text-sm leading-relaxed text-slate-700">{doc.body}</pre>
      </article>
      <LegalFooter />
    </div>
  );
}
