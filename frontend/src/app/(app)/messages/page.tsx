"use client";

import { useEffect, useMemo, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { Button, PageHeader } from "@/components/page";
import { Card, CardBody } from "@/components/ui/card";
import { EmptyState, ErrorBanner } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import type { Conversation } from "@/lib/types";
import { cn } from "@/lib/utils";

export default function MessagesPage() {
  const { user } = useAuth();
  const [threads, setThreads] = useState<Conversation[]>([]);
  const [active, setActive] = useState("");
  const [title, setTitle] = useState("Matter discussion");
  const [body, setBody] = useState("");
  const [error, setError] = useState<string | null>(null);

  const load = () =>
    apiGet<Conversation[]>("/api/v1/conversations")
      .then((rows) => {
        setThreads(rows);
        setActive((cur) => cur || rows[0]?.id || "");
      })
      .catch((e) => setError(e.message));

  useEffect(() => {
    load();
  }, []);

  const current = useMemo(() => threads.find((t) => t.id === active), [threads, active]);

  return (
    <div>
      <PageHeader title="Messages" subtitle="Internal threads stay on the matter. Client portal messages land here too." />
      <ErrorBanner error={error} />
      <div className="mb-4 flex gap-2">
        <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Thread title" />
        <Button
          onClick={async () => {
            const created = await apiPost<Conversation>("/api/v1/conversations", { title, type: "direct" });
            setTitle("Matter discussion");
            await load();
            setActive(created.id);
          }}
        >
          New thread
        </Button>
      </div>
      <div className="grid min-h-[480px] gap-4 lg:grid-cols-[280px_1fr]">
        <Card>
          <CardBody className="p-0">
            {threads.length === 0 ? (
              <EmptyState title="No threads" body="Start a conversation with the team." />
            ) : (
              threads.map((t) => (
                <button
                  key={t.id}
                  onClick={() => setActive(t.id)}
                  className={cn("w-full border-b px-4 py-3 text-left hover:bg-slate-50", active === t.id && "bg-navy/5")}
                >
                  <div className="text-sm font-semibold">{t.title}</div>
                  <div className="text-xs text-slate-400">{t.type}</div>
                </button>
              ))
            )}
          </CardBody>
        </Card>
        <Card className="flex flex-col">
          <CardBody className="flex flex-1 flex-col">
            <h2 className="mb-3 font-semibold">{current?.title ?? "Select a thread"}</h2>
            <div className="mb-3 flex-1 space-y-2 overflow-auto">
              {(current?.messages || []).map((m) => (
                <div
                  key={m.id}
                  className={cn(
                    "max-w-[80%] rounded-2xl px-3 py-2 text-sm",
                    m.senderId === user?.id ? "ml-auto bg-navy text-white" : "bg-slate-100"
                  )}
                >
                  {m.body}
                </div>
              ))}
            </div>
            <div className="flex gap-2">
              <Input
                value={body}
                onChange={(e) => setBody(e.target.value)}
                placeholder="Write a message…"
                onKeyDown={(e) => e.key === "Enter" && void send()}
              />
              <Button onClick={() => void send()}>Send</Button>
            </div>
          </CardBody>
        </Card>
      </div>
    </div>
  );

  async function send() {
    if (!body.trim()) return;
    try {
      await apiPost("/api/v1/messages", { conversationId: active || undefined, body });
      setBody("");
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Send failed.");
    }
  }
}
