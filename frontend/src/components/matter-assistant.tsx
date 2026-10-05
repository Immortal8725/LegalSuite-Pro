"use client";

import { useState } from "react";
import { apiPost } from "@/lib/api";
import { Button } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Label, Select, Textarea } from "@/components/ui/input";

type Turn = { role: "you" | "assistant"; text: string; notice?: string; providerStatus?: string };

type Reply = {
  reply: string;
  notice?: string;
  disclaimer?: string;
  mode?: string;
  providerStatus?: string;
};

const SUGGESTIONS = [
  "What statutory clocks apply, and what should we do next?",
  "Who are the parties?",
  "Summarize the notes",
  "Which files are on this matter?",
];

export function MatterAssistant({
  caseId,
  onCaseId,
  matters,
  matterLabel,
  title = "Draft help on this matter",
}: {
  caseId?: string;
  onCaseId?: (caseId: string) => void;
  matters?: { id: string; caseNumber: string; title: string }[];
  matterLabel?: string;
  title?: string;
}) {
  const [prompt, setPrompt] = useState("");
  const [turns, setTurns] = useState<Turn[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [disclaimer, setDisclaimer] = useState(
    "Assistive only. The attorney remains responsible. This is not legal advice, not a court filing, and not CaseLines."
  );

  async function ask(text: string) {
    const question = text.trim();
    if (!question || busy) return;
    setBusy(true);
    setError(null);
    try {
      const body: { prompt: string; caseId?: string } = { prompt: question };
      if (caseId) body.caseId = caseId;
      const res = await apiPost<Reply>("/api/v1/ai/chat", body);
      if (res.disclaimer) setDisclaimer(res.disclaimer);
      setTurns((prev) => [
        ...prev,
        { role: "you", text: question },
        { role: "assistant", text: res.reply, notice: res.notice, providerStatus: res.providerStatus },
      ]);
      setPrompt("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not prepare a draft");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
      </CardHeader>
      <CardBody className="space-y-3">
        {matters && onCaseId && (
          <div className="space-y-1">
            <Label>Matter</Label>
            <Select value={caseId || ""} onChange={(e) => onCaseId(e.target.value)}>
              <option value="">Whole docket, no single file</option>
              {matters.map((matter) => (
                <option key={matter.id} value={matter.id}>
                  {matter.caseNumber}: {matter.title}
                </option>
              ))}
            </Select>
          </div>
        )}
        <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-950">{disclaimer}</p>
        {matterLabel && (
          <p className="text-xs text-slate-500">
            Context is limited to <span className="font-semibold text-navy">{matterLabel}</span>. Other matters are not included.
          </p>
        )}
        {!caseId && (
          <p className="text-xs text-slate-500">
            No single matter is selected, so this search stays on the firm docket. Open a matter for its clocks, notes, and file names.
          </p>
        )}
        <ErrorBanner error={error} />
        <div className="max-h-72 space-y-3 overflow-y-auto rounded-lg bg-slate-50 p-3 text-sm">
          {turns.length === 0 && (
            <p className="text-slate-500">Parties, computed clocks, notes, and file names.</p>
          )}
          {turns.map((turn, i) => (
            <div key={i}>
              <p className={turn.role === "you" ? "font-semibold text-navy" : "whitespace-pre-wrap text-slate-700"}>
                {turn.role === "you" ? "Staff: " : "Draft: "}
                {turn.text}
              </p>
              {turn.notice && (
                <p
                  className={`mt-1 text-xs ${
                    turn.providerStatus === "missing-key" || turn.providerStatus === "vendor-error"
                      ? "text-amber-800"
                      : "text-slate-500"
                  }`}
                >
                  {turn.notice}
                </p>
              )}
            </div>
          ))}
        </div>
        <div className="flex flex-wrap gap-2">
          {SUGGESTIONS.map((suggestion) => (
            <button
              key={suggestion}
              type="button"
              className="rounded-full border border-slate-200 px-3 py-1 text-left text-xs text-slate-600 hover:border-navy hover:text-navy"
              onClick={() => {
                setPrompt(suggestion);
                void ask(suggestion);
              }}
            >
              {suggestion}
            </button>
          ))}
        </div>
        <Textarea
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          rows={3}
          placeholder="Parties, clocks, notes, or file names. Deadlines follow the computed clocks."
        />
        <Button disabled={busy || !prompt.trim()} onClick={() => void ask(prompt)}>
          {busy ? "Working…" : "Send"}
        </Button>
      </CardBody>
    </Card>
  );
}
