import type { Metadata } from "next";
import Link from "next/link";
import { BLOG_DISCLAIMER, formatDate, getBlogPage } from "@/lib/blog";
import { getCatalog } from "@/lib/seo";

export const revalidate = 300;

type Props = { searchParams: Promise<{ area?: string; page?: string }> };

export async function generateMetadata({ searchParams }: Props): Promise<Metadata> {
  const { area, page } = await searchParams;
  const description = "Plain-language guides to South African law: RAF claims, labour disputes, divorce and more. General information, not legal advice.";
  return {
    title: "Owlaw blog: South African law in plain language",
    description,
    alternates: { canonical: "/blog", types: { "application/rss+xml": "/blog/rss.xml" } },
    openGraph: { title: "Owlaw blog", description, url: "/blog" },
    robots: area || (page && page !== "1") ? { index: false, follow: true } : undefined,
  };
}

export default async function BlogIndex({ searchParams }: Props) {
  const { area, page } = await searchParams;
  const p = Math.max(1, Number(page) || 1);
  const [data, catalog] = await Promise.all([getBlogPage(p, area), getCatalog()]);
  const posts = data?.results || [];
  const pages = data ? Math.max(1, Math.ceil(data.total / data.pageSize)) : 1;
  const link = (n: number) => `/blog?${new URLSearchParams({ ...(area ? { area } : {}), page: String(n) })}`;
  return (
    <div>
      <h1 className="text-3xl font-extrabold text-navy">Owlaw blog</h1>
      <p className="mt-2 text-slate-600">South African law in plain language. {BLOG_DISCLAIMER}</p>

      {catalog && (
        <nav className="mt-6 flex flex-wrap gap-2 text-sm" aria-label="Filter by type of law">
          <Link href="/blog" className={`rounded-full border px-3 py-1 ${!area ? "border-navy bg-navy text-white" : "bg-white text-slate-700"}`}>All</Link>
          {catalog.practiceAreas.map((a) => (
            <Link
              key={a.slug}
              href={`/blog?area=${a.slug}`}
              className={`rounded-full border px-3 py-1 ${area === a.slug ? "border-navy bg-navy text-white" : "bg-white text-slate-700"}`}
            >
              {a.label}
            </Link>
          ))}
        </nav>
      )}

      <div className="mt-8 space-y-6">
        {posts.length === 0 && <p className="text-slate-600">No posts here yet. Check back soon.</p>}
        {posts.map((post) => (
          <article key={post.id} className="rounded-2xl border bg-white p-6">
            <h2 className="text-xl font-bold text-navy">
              <Link href={`/blog/${post.slug}`} className="hover:underline">{post.title}</Link>
            </h2>
            <p className="mt-1 text-xs text-slate-500">
              {post.authorName || "Owlaw"} · {formatDate(post.publishedAt)}
              {post.practiceAreaLabel ? ` · ${post.practiceAreaLabel}` : ""}
            </p>
            {post.summary && <p className="mt-3 text-slate-700">{post.summary}</p>}
          </article>
        ))}
      </div>

      {pages > 1 && (
        <nav className="mt-8 flex justify-between text-sm font-semibold" aria-label="Pages">
          {p > 1 ? <Link href={link(p - 1)} className="text-sky-700">Newer posts</Link> : <span />}
          {p < pages ? <Link href={link(p + 1)} className="text-sky-700">Older posts</Link> : <span />}
        </nav>
      )}
    </div>
  );
}
