import type { Metadata } from "next";
import "./globals.css";
import { Providers } from "@/components/providers";

export const metadata: Metadata = {
  title: "LegalSuite Pro",
  description: "Practice desk for a solo South African attorney. Light is R1,199 per month. The phone is pay-what-you-use.",
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
