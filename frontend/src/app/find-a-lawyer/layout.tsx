import Link from "next/link";
import { Scale } from "lucide-react";
import { LegalFooter } from "@/components/legal-footer";

export const metadata = {
  title: "Find a Lawyer",
  description: "Find a South African law firm by type of lawyer, province, city and language. Every listed firm has an LPC-verified practitioner.",
  alternates: { canonical: "/find-a-lawyer" },
  openGraph: { title: "Find a Lawyer | Owlaw", description: "Find a South African law firm by type of lawyer, province, city and language.", url: "/find-a-lawyer" },
};

export default function FindLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-slate-50">
      <nav className="border-b bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <Link href="/find-a-lawyer" className="flex items-center gap-2 font-extrabold text-navy">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gold text-navy-dark">
              <Scale className="h-4 w-4" />
            </span>
            <span>
              Ow<span className="text-gold">Law</span> <span className="font-semibold text-slate-500">Find a Lawyer</span>
            </span>
          </Link>
          <Link href="/" className="text-sm font-semibold text-slate-600 hover:text-navy">
            For law firms
          </Link>
        </div>
      </nav>
      <main className="mx-auto max-w-6xl px-6 py-8">{children}</main>
      <LegalFooter />
    </div>
  );
}
