import type { Metadata, Viewport } from "next";
import "./globals.css";
import { Providers } from "@/components/providers";
import { SITE_URL } from "@/lib/seo";

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: { default: "Owlaw | Practice software for South African attorneys", template: "%s | Owlaw" },
  description: "Practice software for South African attorneys. Solo R899, Firm R1,199 and Practice R1,599 per attorney a month, ex VAT. Trust accounting included.",
  applicationName: "Owlaw",
  openGraph: {
    type: "website",
    siteName: "Owlaw",
    locale: "en_ZA",
    title: "Owlaw | Practice software for South African attorneys",
    description: "Practice software for South African attorneys, with trust accounting included, plus Find a Lawyer for the public.",
  },
  twitter: { card: "summary" },
  manifest: "/manifest.json",
  appleWebApp: {
    capable: true,
    title: "Owlaw",
    statusBarStyle: "default",
  },
};

export const viewport: Viewport = {
  themeColor: "#1a365d",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en-ZA">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
