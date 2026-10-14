import type { Metadata, Viewport } from "next";
import "./globals.css";
import { Providers } from "@/components/providers";

export const metadata: Metadata = {
  title: "Owlaw",
  description: "Practice software for South African attorneys. Solo R899, Firm R1,199 and Practice R1,599 per attorney a month, ex VAT. Trust accounting included.",
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
    <html lang="en">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
