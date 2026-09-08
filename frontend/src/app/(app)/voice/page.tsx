"use client";

import { useEffect, useState } from "react";
import { apiGet } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { useCalls } from "@/components/call-provider";
import { Button, PageHeader, StatusBadge, TableWrap, Td, Th } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import type { CallRow, TeamMember } from "@/lib/types";
import { formatDateTime, moneyExact } from "@/lib/utils";

type Ethics = { state: string; country?: string; allPartyConsent: boolean; notice: string; statute?: string };

type Registry = {
  totalCalls: number;
  webrtcCalls: number;
  pstnCalls: number;
  totalMinutes: number;
  totalCost: number;
  includedMinutes: number;
  note: string;
};

export default function VoicePage() {
  const { user } = useAuth();
  const { startCall } = useCalls();
  const [rows, setRows] = useState<CallRow[]>([]);
  const [usage, setUsage] = useState<Registry | null>(null);
  const [team, setTeam] = useState<TeamMember[]>([]);
  const [peerId, setPeerId] = useState("");
  const [record, setRecord] = useState(false);
  const [ethics, setEthics] = useState<Ethics | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () =>
    Promise.all([
      apiGet<CallRow[]>("/api/v1/calls"),
      apiGet<Registry>("/api/v1/calls/registry"),
      apiGet<TeamMember[]>("/api/v1/users"),
      apiGet<Ethics>("/api/v1/voice/ethics"),
    ])
      .then(([c, u, t, e]) => {
        setRows(c);
        setUsage(u);
        setTeam(t.filter((x) => x.id !== user?.id));
        setEthics(e);
      })
      .catch((e) => setError(e.message));

  useEffect(() => {
    load();
  }, []);

  const peer = team.find((t) => t.id === peerId);

  return (
    <div>
      <PageHeader
        title="Voice registry"
        subtitle="In-app WebRTC is free. PSTN invoices at month end. Recording is opt-in and parked on the matter."
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <select
              className="h-10 rounded-lg border px-3 text-sm"
              value={peerId}
              onChange={(e) => setPeerId(e.target.value)}
            >
              <option value="">Call a teammate…</option>
              {team.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.fullName}
                </option>
              ))}
            </select>
            <label className="flex items-center gap-2 text-xs text-slate-600">
              <input type="checkbox" checked={record} onChange={(e) => setRecord(e.target.checked)} />
              Record (opt-in)
            </label>
            <Button
              disabled={!peer}
              onClick={async () => {
                if (!peer) return;
                if (record && ethics?.notice && !window.confirm(ethics.notice + "\n\nContinue with recording?")) return;
                await startCall(peer, { record });
                load();
              }}
            >
              Call
            </Button>
          </div>
        }
      />
      {ethics && (
        <p className={`mb-4 rounded-lg border px-3 py-2 text-xs ${ethics.allPartyConsent ? "border-red-200 bg-red-50 text-red-900" : "border-amber-200 bg-amber-50 text-amber-900"}`}>
          {ethics.state}: {ethics.notice}
        </p>
      )}
      <ErrorBanner error={error} />
      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {[
          ["Calls logged", usage?.totalCalls ?? 0],
          ["WebRTC (free)", usage?.webrtcCalls ?? 0],
          ["Minutes", usage?.totalMinutes ?? 0],
          ["Telecom cost", moneyExact(usage?.totalCost ?? 0)],
        ].map(([k, v]) => (
          <Card key={String(k)}>
            <CardHeader>
              <CardTitle>{k}</CardTitle>
            </CardHeader>
            <CardBody className="text-2xl font-semibold text-navy">{String(v)}</CardBody>
          </Card>
        ))}
      </div>
      <TableWrap>
        <thead>
          <tr>
            <Th>When</Th>
            <Th>Type</Th>
            <Th>Status</Th>
            <Th>Duration</Th>
            <Th>Cost</Th>
            <Th>Recording</Th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id}>
              <Td>{formatDateTime(r.startedAt)}</Td>
              <Td>{r.callType}</Td>
              <Td>
                <StatusBadge status={r.status} />
              </Td>
              <Td>{Math.round((r.durationSeconds ?? 0) / 60)} min</Td>
              <Td>{moneyExact(r.totalCost)}</Td>
              <Td>{r.recordingEnabled ? "Opt-in" : "Off"}</Td>
            </tr>
          ))}
        </tbody>
      </TableWrap>
      <p className="mt-3 text-xs text-slate-500">{usage?.note}</p>
    </div>
  );
}
