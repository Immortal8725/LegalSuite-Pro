import { apiRead } from "@/lib/seo";

export type BlogCard = {
  id: string;
  slug: string;
  title: string;
  summary?: string | null;
  body?: string | null;
  practiceArea?: string | null;
  practiceAreaLabel?: string | null;
  city?: string | null;
  cityLabel?: string | null;
  authorName?: string | null;
  status: "draft" | "published";
  hasCover: boolean;
  seoTitle?: string | null;
  seoDescription?: string | null;
  publishedAt?: string | null;
  updatedAt?: string | null;
};

export type BlogPage = { results: BlogCard[]; page: number; pageSize: number; total: number };

export const BLOG_DISCLAIMER = "This is general information, not legal advice. Speak to an attorney about your own matter.";

export function getBlogPage(page = 1, area?: string) {
  const q = new URLSearchParams({ page: String(page) });
  if (area) q.set("area", area);
  return apiRead<BlogPage>(`/api/v1/public/blog/posts?${q}`, 300);
}

export const getBlogPost = (slug: string) => apiRead<BlogCard>(`/api/v1/public/blog/posts/${encodeURIComponent(slug)}`, 300);

/** Every published post (for the sitemap and RSS). */
export async function allBlogPosts(maxPages = 20): Promise<BlogCard[]> {
  const out: BlogCard[] = [];
  for (let p = 1; p <= maxPages; p++) {
    const r = await getBlogPage(p);
    if (!r) break;
    out.push(...r.results);
    if (r.results.length === 0 || p * r.pageSize >= r.total) break;
  }
  return out;
}

export const coverPath = (slug: string) => `/api/v1/public/blog/posts/${encodeURIComponent(slug)}/cover`;

export function formatDate(iso?: string | null): string {
  if (!iso) return "";
  return new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "long", year: "numeric", timeZone: "Africa/Johannesburg" });
}

const esc = (s: string) =>
  s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");

/** Inline Markdown on text that is already escaped: links (http, https, mailto or site paths only), bold, italic, code. */
function inline(raw: string): string {
  const codes: string[] = [];
  let s = esc(raw).replace(/`([^`]+)`/g, (_, c: string) => {
    codes.push(`<code>${c}</code>`);
    return `\u0000${codes.length - 1}\u0000`;
  });
  s = s.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (m, text: string, href: string) => {
    const h = href.replace(/&amp;/g, "&");
    if (!/^(https?:\/\/|mailto:|\/(?!\/))/i.test(h)) return text;
    const external = /^https?:\/\//i.test(h);
    return `<a href="${esc(h)}"${external ? ' rel="nofollow noopener" target="_blank"' : ""}>${text}</a>`;
  });
  s = s.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>").replace(/(^|[^*])\*([^*]+)\*/g, "$1<em>$2</em>");
  return s.replace(/\u0000(\d+)\u0000/g, (_, i: string) => codes[Number(i)]);
}

/**
 * Markdown to HTML for blog posts. Every character of the source is escaped first, so raw HTML in a post shows as
 * text and never runs. Supports headings (##, ###), paragraphs, lists, block quotes, links, bold, italic and code.
 */
export function renderMarkdown(src: string): string {
  const lines = (src || "").replace(/\r\n?/g, "\n").split("\n");
  const out: string[] = [];
  let para: string[] = [];
  let list: { tag: "ul" | "ol"; items: string[] } | null = null;
  let quote: string[] = [];
  const flushPara = () => {
    if (para.length) out.push(`<p>${inline(para.join(" "))}</p>`);
    para = [];
  };
  const flushList = () => {
    if (list) out.push(`<${list.tag}>${list.items.map((i) => `<li>${inline(i)}</li>`).join("")}</${list.tag}>`);
    list = null;
  };
  const flushQuote = () => {
    if (quote.length) out.push(`<blockquote><p>${inline(quote.join(" "))}</p></blockquote>`);
    quote = [];
  };
  const flushAll = () => {
    flushPara();
    flushList();
    flushQuote();
  };
  for (const line of lines) {
    const t = line.trim();
    let m: RegExpMatchArray | null;
    if (!t) {
      flushAll();
    } else if ((m = t.match(/^(#{1,4})\s+(.*)$/))) {
      flushAll();
      const level = Math.min(Math.max(m[1].length, 2), 4);
      out.push(`<h${level}>${inline(m[2])}</h${level}>`);
    } else if ((m = t.match(/^>\s?(.*)$/))) {
      flushPara();
      flushList();
      quote.push(m[1]);
    } else if ((m = t.match(/^[-*]\s+(.*)$/))) {
      flushPara();
      flushQuote();
      if (!list || list.tag !== "ul") {
        flushList();
        list = { tag: "ul", items: [] };
      }
      list.items.push(m[1]);
    } else if ((m = t.match(/^\d+[.)]\s+(.*)$/))) {
      flushPara();
      flushQuote();
      if (!list || list.tag !== "ol") {
        flushList();
        list = { tag: "ol", items: [] };
      }
      list.items.push(m[1]);
    } else if (/^(-{3,}|\*{3,})$/.test(t)) {
      flushAll();
      out.push("<hr />");
    } else {
      flushList();
      flushQuote();
      para.push(t);
    }
  }
  flushAll();
  return out.join("\n");
}

/** Plain text of a post, for descriptions and RSS. */
export function plainText(md: string, max = 160): string {
  const s = (md || "")
    .replace(/[#>*`_-]+/g, " ")
    .replace(/\[([^\]]+)\]\([^)]+\)/g, "$1")
    .replace(/\s+/g, " ")
    .trim();
  return s.length > max ? `${s.slice(0, max - 1).trimEnd()}…` : s;
}
