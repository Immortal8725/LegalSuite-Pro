import type { Metadata } from "next";
import "./globals.css";
import { Providers } from "@/components/providers";

export const metadata: Metadata = {
  title: "LegalSuite Pro — Law Firm Operating System",
  description: "Cases, clients, billing, trust accounting, voice calling, and a live firm website — in one platform.",
  manifest: "/manifest.json",
  themeColor: "#1a365d",
  appleWebApp: {
    capable: true,
    title: "LegalSuite Pro",
    statusBarStyle: "default",
  },
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
