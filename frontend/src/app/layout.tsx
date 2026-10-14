import type { Metadata, Viewport } from "next";
import "./globals.css";
import { Providers } from "@/components/providers";

export const metadata: Metadata = {
  title: "Owlaw",
  description: "Practice desk for a solo South African attorney. Light is R1,199 per month. The phone is pay-what-you-use.",
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
