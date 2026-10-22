import type { Metadata } from "next";
import { legalBySlug } from "@/lib/legal";

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  const doc = legalBySlug(slug);
  if (!doc) return { title: "Legal", robots: { index: false, follow: true } };
  return { title: doc.title, description: `${doc.title} for Owlaw, South African practice software.`, alternates: { canonical: `/legal/${slug}` } };
}

export default function LegalLayout({ children }: { children: React.ReactNode }) {
  return children;
}
