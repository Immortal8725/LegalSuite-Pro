"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { Button, PageHeader } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Label, Select, Textarea } from "@/components/ui/input";
import { PrivilegeStrip } from "@/components/privilege-strip";
import type { Lead, Matter } from "@/lib/types";

type ChatTurn = { role: "you" | "assistant"; text: string };

export default function AiPage() {
  const [prompt, setPrompt] = useState("What is open on Johnson?");
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [cases, setCases] = useState<Matter[]>([]);
  const [leads, setLeads] = useState<Lead[]>([]);
  const [caseId, setCaseId] = useState("");
  const [leadId, setLeadId] = useState("");
  const [kind, setKind] = useState("status");
  const [summary, setSummary] = useState("");
  const [draft, setDraft] = useState("");
  const [screen, setScreen] = useState<string>("");

  useEffect(() => {
    apiGet<Matter[]>("/api/v1/cases").then((rows) => {
      setCases(rows);
      if (rows[0]) setCaseId(rows[0].id);
    });
    apiGet<Lead[]>("/api/v1/intake/leads")
      .then((rows) => {
        setLeads(rows);
        if (rows[0]) setLeadId(rows[0].id);
      })
      .catch(() => setLeads([]));
  }, []);

  return (
    <div>
      <PageHeader
        title="Draft help"
        subtitle="Drafts stay on this firm. The attorney remains responsible. This is not legal advice, a court filing, or CaseLines."
      />
      <PrivilegeStrip />
      <ErrorBanner error={error} />
      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Ask the docket</CardTitle>
          </CardHeader>
          <CardBody className="space-y-3">
            <div className="max-h-64 space-y-2 overflow-y-auto rounded-lg bg-slate-50 p-3 text-sm">
              {turns.length === 0 && (
                <p className="text-slate-500">Try a last name, a case number, or “how do I bill a retainer?”</p>
              )}
              {turns.map((t, i) => (
                <p key={i} className={t.role === "you" ? "font-semibold text-navy" : "whitespace-pre-wrap text-slate-700"}>
                  {t.role === "you" ? "You: " : "Assistant: "}
                  {t.text}
                </p>
              ))}
            </div>
            <Textarea value={prompt} onChange={(e) => setPrompt(e.target.value)} rows={3} />
            <Button
              disabled={busy}
              onClick={async () => {
                setBusy(true);
                setError(null);
                try {
                  const res = await apiPost<{ reply: string }>("/api/v1/ai/chat", { prompt });
                  setTurns((prev) => [...prev, { role: "you", text: prompt }, { role: "assistant", text: res.reply }]);
                } catch (e) {
                  setError(e instanceof Error ? e.message : "Chat failed");
                } finally {
                  setBusy(false);
                }
              }}
            >
              {busy ? "Thinking…" : "Ask"}
            </Button>
          </CardBody>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Summarize a matter</CardTitle>
            </CardHeader>
            <CardBody className="space-y-3">
              <Label>Matter</Label>
              <Select value={caseId} onChange={(e) => setCaseId(e.target.value)}>
                {cases.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.caseNumber}: {c.title}
                  </option>
                ))}
              </Select>
              <Button
                variant="outline"
                onClick={async () => {
                  setError(null);
                  try {
                    const res = await apiPost<{ summary: string }>("/api/v1/ai/summarize", { caseId });
                    setSummary(res.summary);
                  } catch (e) {
                    setError(e instanceof Error ? e.message : "Summarize failed");
                  }
                }}
              >
                Summarize notes
              </Button>
              {summary && <pre className="whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm">{summary}</pre>}
            </CardBody>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Draft an email</CardTitle>
            </CardHeader>
            <CardBody className="space-y-3">
              <Select value={kind} onChange={(e) => setKind(e.target.value)}>
                <option value="status">Status update</option>
                <option value="retainer">Engagement letter cover</option>
                <option value="demand">Demand letter</option>
              </Select>
              <Button
                variant="outline"
                onClick={async () => {
                  setError(null);
                  try {
                    const res = await apiPost<{ draft: string }>("/api/v1/ai/draft-email", { kind, caseId });
                    setDraft(res.draft);
                  } catch (e) {
                    setError(e instanceof Error ? e.message : "Draft failed");
                  }
                }}
              >
                Draft
              </Button>
              {draft && <pre className="whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm">{draft}</pre>}
            </CardBody>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Screen an intake</CardTitle>
            </CardHeader>
            <CardBody className="space-y-3">
              {leads.length === 0 ? (
                <p className="text-sm text-slate-500">No website leads yet. Submit one from the public firm page.</p>
              ) : (
                <>
                  <Select value={leadId} onChange={(e) => setLeadId(e.target.value)}>
                    {leads.map((l) => (
                      <option key={l.id} value={l.id}>
                        {l.name}: {l.caseType || "unspecified"}
                      </option>
                    ))}
                  </Select>
                  <Button
                    variant="outline"
                    onClick={async () => {
                      setError(null);
                      try {
                        const res = await apiPost<{ summary: string; recommendedNext: string; score: number }>(
                          "/api/v1/ai/screen-intake",
                          { leadId }
                        );
                        setScreen(`${res.score}/100. ${res.summary}\n\nNext: ${res.recommendedNext}`);
                      } catch (e) {
                        setError(e instanceof Error ? e.message : "Screen failed");
                      }
                    }}
                  >
                    Screen lead
                  </Button>
                </>
              )}
              {screen && <pre className="whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm">{screen}</pre>}
            </CardBody>
          </Card>
        </div>
      </div>
    </div>
  );
}
