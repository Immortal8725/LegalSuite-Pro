"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { Button, PageHeader } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Label, Select } from "@/components/ui/input";
import { PrivilegeStrip } from "@/components/privilege-strip";
import { MatterAssistant } from "@/components/matter-assistant";
import type { Lead, Matter } from "@/lib/types";

export default function AiPage() {
  const [chatCaseId, setChatCaseId] = useState("");
  const [error, setError] = useState<string | null>(null);
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

  const chatMatter = cases.find((c) => c.id === chatCaseId);

  return (
    <div>
      <PageHeader
        title="Draft help"
        subtitle="Draft help for staff on this firm. The attorney remains responsible. A model vendor runs only if the firm configured one. Otherwise the answer stays on this tenant."
      />
      <PrivilegeStrip />
      <ErrorBanner error={error} />
      <div className="grid gap-6 lg:grid-cols-2">
        <MatterAssistant
          caseId={chatCaseId || undefined}
          onCaseId={setChatCaseId}
          matters={cases}
          matterLabel={chatMatter ? `${chatMatter.caseNumber} · ${chatMatter.title}` : undefined}
          title={chatCaseId ? "Draft help on the selected matter" : "Search this firm's docket"}
        />

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
