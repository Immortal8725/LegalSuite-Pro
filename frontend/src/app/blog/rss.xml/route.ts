import { allBlogPosts, plainText } from "@/lib/blog";
import { SITE_URL } from "@/lib/seo";

export const dynamic = "force-dynamic";

const esc = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

export async function GET() {
  const posts = await allBlogPosts();
  const items = posts
    .map((p) => {
      const url = `${SITE_URL}/blog/${p.slug}`;
      return `    <item>\n      <title>${esc(p.title)}</title>\n      <link>${url}</link>\n      <guid isPermaLink="true">${url}</guid>\n${p.publishedAt ? `      <pubDate>${new Date(p.publishedAt).toUTCString()}</pubDate>\n` : ""}      <description>${esc(p.summary || plainText(p.body || "", 300))}</description>\n    </item>`;
    })
    .join("\n");
  const xml = `<?xml version="1.0" encoding="UTF-8"?>\n<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">\n  <channel>\n    <title>Owlaw blog</title>\n    <link>${SITE_URL}/blog</link>\n    <atom:link href="${SITE_URL}/blog/rss.xml" rel="self" type="application/rss+xml" />\n    <description>South African law in plain language. General information, not legal advice.</description>\n    <language>en-za</language>\n${items}\n  </channel>\n</rss>\n`;
  return new Response(xml, { headers: { "Content-Type": "application/rss+xml; charset=utf-8", "Cache-Control": "public, max-age=900" } });
}
