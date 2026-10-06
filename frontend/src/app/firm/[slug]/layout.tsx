import type { CSSProperties } from "react";
import { Source_Sans_3, Source_Serif_4 } from "next/font/google";
import { PublicSiteRoot } from "@/components/public-site/site-context";

const serif = Source_Serif_4({
  subsets: ["latin"],
  weight: ["400", "600", "700"],
  variable: "--font-public-serif",
  display: "swap",
});

const sans = Source_Sans_3({
  subsets: ["latin"],
  weight: ["400", "600", "700"],
  variable: "--font-public-sans",
  display: "swap",
});

const neutral: CSSProperties = {
  ["--ps-paper" as string]: "#f4f5f6",
  ["--ps-cream" as string]: "#ffffff",
  ["--ps-sand" as string]: "#eceef1",
  ["--ps-clay" as string]: "#e4e7eb",
  ["--ps-card" as string]: "#ffffff",
  ["--ps-ink" as string]: "#1f1f1f",
  ["--ps-body" as string]: "#3d3d3d",
  ["--ps-meta" as string]: "#6e6e6e",
  ["--ps-line" as string]: "#e1e3e6",
  fontFamily: "var(--font-public-sans), 'Segoe UI', sans-serif",
};

export default function FirmLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className={`${serif.variable} ${sans.variable} min-h-screen bg-[var(--ps-paper)] text-[var(--ps-ink)] antialiased`} style={neutral}>
      <PublicSiteRoot>{children}</PublicSiteRoot>
    </div>
  );
}
