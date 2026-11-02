import { adsensePublisherId } from "@/lib/adsense";

export const dynamic = "force-static";

/** ads.txt for AdSense. 404 until NEXT_PUBLIC_ADSENSE_CLIENT is set. */
export function GET() {
  const pub = adsensePublisherId();
  if (!pub) return new Response("Not found\n", { status: 404, headers: { "Content-Type": "text/plain; charset=utf-8" } });
  return new Response(`google.com, ${pub}, DIRECT, f08c47fec0942fa0\n`, {
    headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "public, max-age=3600" },
  });
}
