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

const warm: CSSProperties = {
  ["--ps-paper" as string]: "#f7efe4",
  ["--ps-cream" as string]: "#fff9f3",
  ["--ps-sand" as string]: "#f0dcc4",
  ["--ps-clay" as string]: "#e6c9a6",
  ["--ps-card" as string]: "#fff8f1",
  ["--ps-ink" as string]: "#2c2118",
  ["--ps-body" as string]: "#5e4938",
  ["--ps-meta" as string]: "#8d6b4f",
  ["--ps-line" as string]: "#e5d0b8",
  fontFamily: "var(--font-public-sans), 'Segoe UI', sans-serif",
};

export default function FirmLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className={`${serif.variable} ${sans.variable} min-h-screen bg-[var(--ps-paper)] text-[var(--ps-ink)] antialiased`} style={warm}>
      <PublicSiteRoot>{children}</PublicSiteRoot>
    </div>
  );
}
