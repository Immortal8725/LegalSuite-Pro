import Link from "next/link";
import { Scale } from "lucide-react";
import { LegalFooter } from "@/components/legal-footer";
import { BlogAdsProvider } from "@/components/blog-ads";

export default function BlogLayout({ children }: { children: React.ReactNode }) {
  return (
    <BlogAdsProvider>
    <div className="min-h-screen bg-slate-50">
      <nav className="border-b bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <Link href="/blog" className="flex items-center gap-2 font-extrabold text-navy">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gold text-navy-dark">
              <Scale className="h-4 w-4" />
            </span>
            <span>
              Ow<span className="text-gold">Law</span> <span className="font-semibold text-slate-500">Blog</span>
            </span>
          </Link>
          <div className="flex items-center gap-4 text-sm font-semibold text-slate-600">
            <Link href="/find-a-lawyer" className="hover:text-navy">Find a Lawyer</Link>
            <Link href="/" className="hidden hover:text-navy sm:inline">For law firms</Link>
          </div>
        </div>
      </nav>
      <main className="mx-auto max-w-4xl px-6 py-8">{children}</main>
      <LegalFooter />
    </div>
    </BlogAdsProvider>
  );
}
