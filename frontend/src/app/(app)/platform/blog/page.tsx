"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { api, apiGet, apiPost, apiPut } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page";
import { Badge, Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { useConfirm } from "@/components/confirm-provider";
import { ErrorBanner } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import { BLOG_DISCLAIMER, type BlogCard, renderMarkdown } from "@/lib/blog";
import type { Catalog } from "@/lib/directory";

type Form = {
  title: string;
  slug: string;
  summary: string;
  body: string;
  practiceArea: string;
  city: string;
  authorName: string;
  seoTitle: string;
  seoDescription: string;
};

const EMPTY: Form = { title: "", slug: "", summary: "", body: "", practiceArea: "", city: "", authorName: "Owlaw", seoTitle: "", seoDescription: "" };
const MAX_COVER = 2 * 1024 * 1024;

function toForm(p: BlogCard): Form {
  return {
    title: p.title || "",
    slug: p.slug || "",
    summary: p.summary || "",
    body: p.body || "",
    practiceArea: p.practiceArea || "",
    city: p.city || "",
    authorName: p.authorName || "Owlaw",
    seoTitle: p.seoTitle || "",
    seoDescription: p.seoDescription || "",
  };
}

export default function BlogAdminPage() {
  const { user } = useAuth();
  const [posts, setPosts] = useState<BlogCard[]>([]);
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [current, setCurrent] = useState<BlogCard | null>(null);
  const [form, setForm] = useState<Form>(EMPTY);
  const [editing, setEditing] = useState(false);
  const [tab, setTab] = useState<"write" | "preview">("write");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const operator = user?.role === "superadmin";
  const { confirm, undoable } = useConfirm();

  const load = useCallback(() => {
    apiGet<BlogCard[]>("/api/v1/platform/blog/posts")
      .then(setPosts)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : "Could not load the posts."));
  }, []);

  useEffect(() => {
    if (!operator) return;
    load();
    apiGet<Catalog>("/api/v1/public/directory/catalog").then(setCatalog).catch(() => undefined);
  }, [operator, load]);

  const preview = useMemo(() => renderMarkdown(form.body), [form.body]);
  const set = (k: keyof Form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function open(p: BlogCard) {
    setError(null);
    setNotice(null);
    try {
      const full = await apiGet<BlogCard>(`/api/v1/platform/blog/posts/${p.id}`);
      setCurrent(full);
      setForm(toForm(full));
      setEditing(true);
      setTab("write");
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Could not open the post.");
    }
  }

  function startNew() {
    setCurrent(null);
    setForm(EMPTY);
    setEditing(true);
    setTab("write");
    setError(null);
    setNotice(null);
  }

  async function run(fn: () => Promise<BlogCard | null>, done: string) {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const p = await fn();
      if (p) {
        setCurrent(p);
        setForm(toForm(p));
      }
      setNotice(done);
      load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Something went wrong.");
    } finally {
      setBusy(false);
    }
  }

  const save = () =>
    run(
      () =>
        current
          ? apiPut<BlogCard>(`/api/v1/platform/blog/posts/${current.id}`, form)
          : apiPost<BlogCard>("/api/v1/platform/blog/posts", form),
      "Saved.",
    );

  const blogArmed = useRef(false);
  useEffect(() => {
    blogArmed.current = false;
  }, [current?.id]);
  useEffect(() => {
    if (!editing || !current) return;
    if (!blogArmed.current) {
      blogArmed.current = true;
      return;
    }
    const handle = window.setTimeout(() => {
      void save();
    }, 1200);
    return () => window.clearTimeout(handle);
  }, [form.title, form.summary, form.body, form.slug, editing, current?.id]);

  const publish = () =>
    run(async () => {
      if (!current) return null;
      await apiPut<BlogCard>(`/api/v1/platform/blog/posts/${current.id}`, form);
      return apiPost<BlogCard>(`/api/v1/platform/blog/posts/${current.id}/publish`);
    }, "Published. It is now on owlaw.co.za/blog.");

  const unpublish = () =>
    run(() => apiPost<BlogCard>(`/api/v1/platform/blog/posts/${current!.id}/unpublish`), "Taken off the site and moved back to drafts.");

  async function remove() {
    if (!current) return;
    const post = current;
    const yes = await confirm({
      title: `Delete "${post.title}"?`,
      body: "The post leaves the list. You can undo this for a few seconds. After that it is gone.",
      confirmLabel: "Delete",
    });
    if (!yes) return;
    const previousPosts = posts;
    setPosts(posts.filter((row) => row.id !== post.id));
    setEditing(false);
    setCurrent(null);
    undoable("Post deleted", async () => {
      try {
        await api(`/api/v1/platform/blog/posts/${post.id}`, { method: "DELETE" });
      } catch (e) {
        setPosts(previousPosts);
        setCurrent(post);
        setError(e instanceof Error ? e.message : "Could not delete the post");
      }
    }, () => {
      setPosts(previousPosts);
      setCurrent(post);
      setEditing(true);
    });
  }

  async function uploadCover(file: File | undefined) {
    if (!file || !current) return;
    if (file.size > MAX_COVER) {
      setError("Cover images can be up to 2 MB.");
      return;
    }
    const fd = new FormData();
    fd.append("file", file);
    await run(() => apiPost<BlogCard>(`/api/v1/platform/blog/posts/${current.id}/cover`, fd), "Cover image saved.");
  }

  async function removeCover() {
    if (!current?.hasCover) return;
    const post = current;
    const yes = await confirm({
      title: "Remove the cover image?",
      body: "You can undo this for a few seconds.",
      confirmLabel: "Remove cover",
    });
    if (!yes) return;
    setCurrent({ ...post, hasCover: false });
    undoable("Cover image removed", async () => {
      try {
        await api<BlogCard>(`/api/v1/platform/blog/posts/${post.id}/cover`, { method: "DELETE" });
      } catch (e) {
        setCurrent(post);
        setError(e instanceof Error ? e.message : "Could not remove the cover");
      }
    }, () => setCurrent(post));
  }

  if (!operator) {
    return (
      <div>
        <PageHeader title="Blog" subtitle="This screen is for the Owlaw operator." />
        <p className="text-sm text-slate-600">Sign in as the platform operator to write blog posts.</p>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Blog"
        subtitle="Write posts for owlaw.co.za/blog. Posts stay as drafts until you publish them. Please have an attorney check any legal content first."
      />
      <ErrorBanner error={error} />
      {notice && <p className="mb-4 rounded-lg bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}

      {!editing ? (
        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle>Posts</CardTitle>
            <Button onClick={startNew} data-testid="blog-new">New post</Button>
          </CardHeader>
          <CardBody>
            {posts.length === 0 && <p className="text-sm text-slate-600">No posts yet.</p>}
            <ul className="divide-y">
              {posts.map((p) => (
                <li key={p.id} className="flex items-center justify-between gap-3 py-3">
                  <button type="button" className="text-left font-semibold text-navy hover:underline" onClick={() => open(p)}>
                    {p.title}
                  </button>
                  <div className="flex items-center gap-2 text-xs text-slate-500">
                    {p.practiceAreaLabel && <span>{p.practiceAreaLabel}</span>}
                    <Badge tone={p.status === "published" ? "green" : "slate"}>{p.status === "published" ? "Published" : "Draft"}</Badge>
                  </div>
                </li>
              ))}
            </ul>
          </CardBody>
        </Card>
      ) : (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <Button variant="outline" onClick={() => { setEditing(false); setCurrent(null); }}>Back to posts</Button>
            <Button onClick={save} disabled={busy} data-testid="blog-save">{current ? "Save" : "Save draft"}</Button>
            {current && current.status !== "published" && <Button onClick={publish} disabled={busy} data-testid="blog-publish">Publish</Button>}
            {current && current.status === "published" && (
              <>
                <Button variant="outline" onClick={unpublish} disabled={busy} data-testid="blog-unpublish">Unpublish</Button>
                <Link href={`/blog/${current.slug}`} target="_blank" className="text-sm font-semibold text-sky-700 hover:underline">View on site</Link>
              </>
            )}
            {current && <Button variant="danger" onClick={remove} disabled={busy} data-testid="blog-delete">Delete</Button>}
            {current && <Badge tone={current.status === "published" ? "green" : "slate"}>{current.status === "published" ? "Published" : "Draft"}</Badge>}
          </div>

          <Card>
            <CardBody className="grid gap-4 sm:grid-cols-2">
              <div className="flex flex-col gap-1 sm:col-span-2">
                <Label htmlFor="b-title">Title</Label>
                <Input id="b-title" value={form.title} maxLength={200} onChange={set("title")} />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="b-slug">Web address (owlaw.co.za/blog/...)</Label>
                <Input id="b-slug" value={form.slug} maxLength={120} placeholder="Made from the title if left empty" onChange={set("slug")} />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="b-author">Author name</Label>
                <Input id="b-author" value={form.authorName} maxLength={120} onChange={set("authorName")} />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="b-area">Type of law</Label>
                <Select id="b-area" value={form.practiceArea} onChange={set("practiceArea")}>
                  <option value="">None</option>
                  {catalog?.practiceAreas.map((a) => <option key={a.slug} value={a.slug}>{a.label}</option>)}
                </Select>
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="b-city">City</Label>
                <Select id="b-city" value={form.city} onChange={set("city")}>
                  <option value="">None</option>
                  {catalog?.cities.map((c) => <option key={c.slug} value={c.slug}>{c.label}</option>)}
                </Select>
              </div>
              <div className="flex flex-col gap-1 sm:col-span-2">
                <Label htmlFor="b-summary">Summary (shown on the blog list)</Label>
                <Textarea id="b-summary" rows={2} maxLength={400} value={form.summary} onChange={set("summary")} />
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center gap-2">
              <Button variant={tab === "write" ? "default" : "outline"} size="sm" onClick={() => setTab("write")}>Write</Button>
              <Button variant={tab === "preview" ? "default" : "outline"} size="sm" onClick={() => setTab("preview")}>Preview</Button>
            </CardHeader>
            <CardBody>
              {tab === "write" ? (
                <>
                  <Textarea id="b-body" rows={22} className="font-mono text-sm" value={form.body} onChange={set("body")} />
                  <p className="mt-2 text-xs text-slate-500">
                    Use ## for a heading, ### for a smaller heading, - for a bullet, 1. for a numbered list, **bold**, *italic* and [link text](https://...).
                  </p>
                </>
              ) : (
                <div>
                  <h1 className="text-2xl font-extrabold text-navy">{form.title || "Untitled"}</h1>
                  <p className="mt-3 rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">{BLOG_DISCLAIMER}</p>
                  <div className="blog-body mt-4" dangerouslySetInnerHTML={{ __html: preview }} />
                </div>
              )}
            </CardBody>
          </Card>

          <Card>
            <CardHeader><CardTitle>Search engines and sharing</CardTitle></CardHeader>
            <CardBody className="grid gap-4">
              <div className="flex flex-col gap-1">
                <Label htmlFor="b-seot">Search title (optional, uses the title if empty)</Label>
                <Input id="b-seot" value={form.seoTitle} maxLength={200} onChange={set("seoTitle")} />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="b-seod">Search description (optional, about 150 characters)</Label>
                <Textarea id="b-seod" rows={2} maxLength={300} value={form.seoDescription} onChange={set("seoDescription")} />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="b-cover">Cover image (JPG, PNG or WebP, up to 2 MB)</Label>
                {current ? (
                  <div className="flex flex-wrap items-center gap-3">
                    <input id="b-cover" type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => uploadCover(e.target.files?.[0])} />
                    {current.hasCover && <span className="text-sm text-slate-600">A cover image is set.</span>}
                    {current.hasCover && <Button variant="outline" size="sm" onClick={removeCover}>Remove cover</Button>}
                  </div>
                ) : (
                  <p className="text-sm text-slate-500">Save the draft first, then add a cover image.</p>
                )}
              </div>
            </CardBody>
          </Card>
        </div>
      )}
    </div>
  );
}
