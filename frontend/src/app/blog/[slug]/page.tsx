import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { JsonLd } from "@/components/json-ld";
import { BlogAd } from "@/components/blog-ads";
import { BLOG_DISCLAIMER, coverPath, formatDate, getBlogPost, plainText, renderMarkdown } from "@/lib/blog";
import { SITE_URL } from "@/lib/seo";

export const revalidate = 300;

type Props = { params: Promise<{ slug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const post = await getBlogPost(slug);
  if (!post) return { title: "Post not found", robots: { index: false, follow: true } };
  const title = post.seoTitle || post.title;
  const description = post.seoDescription || post.summary || plainText(post.body || "");
  const url = `/blog/${post.slug}`;
  return {
    title,
    description,
    alternates: { canonical: url },
    openGraph: {
      type: "article",
      title: `${title} | Owlaw`,
      description,
      url,
      publishedTime: post.publishedAt || undefined,
      modifiedTime: post.updatedAt || undefined,
      authors: [post.authorName || "Owlaw"],
      ...(post.hasCover ? { images: [{ url: `${SITE_URL}${coverPath(post.slug)}` }] } : {}),
    },
  };
}

export default async function BlogPostPage({ params }: Props) {
  const { slug } = await params;
  const post = await getBlogPost(slug);
  if (!post) notFound();
  const url = `${SITE_URL}/blog/${post.slug}`;
  const article = {
    "@context": "https://schema.org",
    "@type": "Article",
    headline: post.title.slice(0, 110),
    description: post.seoDescription || post.summary || plainText(post.body || ""),
    datePublished: post.publishedAt,
    dateModified: post.updatedAt || post.publishedAt,
    author: { "@type": post.authorName && post.authorName !== "Owlaw" ? "Person" : "Organization", name: post.authorName || "Owlaw" },
    publisher: { "@type": "Organization", name: "Owlaw", url: SITE_URL },
    mainEntityOfPage: url,
    inLanguage: "en-ZA",
    ...(post.hasCover ? { image: `${SITE_URL}${coverPath(post.slug)}` } : {}),
  };
  const crumbs = {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: [
      { "@type": "ListItem", position: 1, name: "Owlaw", item: `${SITE_URL}/` },
      { "@type": "ListItem", position: 2, name: "Blog", item: `${SITE_URL}/blog` },
      { "@type": "ListItem", position: 3, name: post.title, item: url },
    ],
  };
  return (
    <article>
      <JsonLd data={article} />
      <JsonLd data={crumbs} />
      <nav className="text-sm text-slate-500" aria-label="Breadcrumb">
        <Link href="/" className="hover:underline">Owlaw</Link> / <Link href="/blog" className="hover:underline">Blog</Link>
      </nav>
      <h1 className="mt-3 text-3xl font-extrabold text-navy sm:text-4xl">{post.title}</h1>
      <p className="mt-2 text-sm text-slate-500">
        By {post.authorName || "Owlaw"} · <time dateTime={post.publishedAt || undefined}>{formatDate(post.publishedAt)}</time>
        {post.updatedAt && post.publishedAt && post.updatedAt.slice(0, 10) !== post.publishedAt.slice(0, 10)
          ? ` · Updated ${formatDate(post.updatedAt)}`
          : ""}
      </p>
      <p className="mt-4 rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900" data-testid="blog-disclaimer">
        {BLOG_DISCLAIMER}
      </p>
      {post.hasCover && (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={coverPath(post.slug)} alt="" className="mt-6 w-full rounded-2xl object-cover" />
      )}
      <div className="blog-body mt-6" dangerouslySetInnerHTML={{ __html: renderMarkdown(post.body || "") }} />

      <BlogAd slot="post" />

      <aside className="mt-10 rounded-2xl border bg-white p-6">
        <h2 className="text-lg font-bold text-navy">Need help with your own matter?</h2>
        <p className="mt-1 text-sm text-slate-600">Describe what happened and Owlaw will suggest the type of lawyer to look for.</p>
        <div className="mt-4 flex flex-wrap gap-3 text-sm font-semibold">
          <Link href="/#ask" className="rounded-lg bg-gold px-4 py-2 text-navy-dark hover:bg-gold-light">Ask the AI lawyer finder</Link>
          {post.practiceArea && (
            <Link href={`/find-a-lawyer/${post.practiceArea}`} className="rounded-lg border px-4 py-2 text-navy hover:bg-slate-50">
              {post.practiceAreaLabel || "Find a lawyer"} lawyers
            </Link>
          )}
          {post.practiceArea && post.city && (
            <Link href={`/find-a-lawyer/${post.practiceArea}/${post.city}`} className="rounded-lg border px-4 py-2 text-navy hover:bg-slate-50">
              {post.practiceAreaLabel} lawyers in {post.cityLabel || post.city}
            </Link>
          )}
          {!post.practiceArea && <Link href="/find-a-lawyer" className="rounded-lg border px-4 py-2 text-navy hover:bg-slate-50">Find a Lawyer</Link>}
        </div>
      </aside>
      <p className="mt-6 text-sm"><Link href="/blog" className="font-semibold text-sky-700 hover:underline">Back to the blog</Link></p>
    </article>
  );
}
