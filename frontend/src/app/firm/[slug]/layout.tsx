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

export default function FirmLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className={`${serif.variable} ${sans.variable} min-h-screen bg-[#f6f3ee] text-[#1c1917] antialiased`} style={{ fontFamily: "var(--font-public-sans), 'Segoe UI', sans-serif" }}>
      <PublicSiteRoot>{children}</PublicSiteRoot>
    </div>
  );
}
