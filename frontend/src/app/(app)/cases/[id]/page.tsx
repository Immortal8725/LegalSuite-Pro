"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { apiGet, apiPatch, apiPost } from "@/lib/api";
import { formatDateTime } from "@/lib/utils";
import { Button, PageHeader, StatusBadge } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { Input, Select, Textarea } from "@/components/ui/input";
import type { Matter } from "@/lib/types";

export default function CaseDetailPage() {
  const params = useParams<{ id: string }>();
  const [c, setC] = useState<Matter | null>(null);
  const [note, setNote] = useState("");
  const [status, setStatus] = useState("");

  const load = () => apiGet<Matter>(`/api/v1/cases/${params.id}`).then((row) => {
    setC(row);
    setStatus(row.status);
  });

  useEffect(() => {
    load();
  }, [params.id]);

  if (!c) return <p className="text-sm text-slate-500">Loading matter…</p>;

  return (
    <div>
      <PageHeader
        title={c.title}
        subtitle={`${c.caseNumber} · ${c.clientName || "Client"} · ${c.practiceArea || ""}`}
        actions={
          <div className="flex gap-2">
            <Select value={status} onChange={(e) => setStatus(e.target.value)}>
              {["intake", "open", "pending", "discovery", "mediation", "trial", "settled", "closed"].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </Select>
            <Button
              onClick={async () => {
                await apiPatch(`/api/v1/cases/${c.id}/status`, { status });
                load();
              }}
            >
              Update status
            </Button>
          </div>
        }
      />
      <div className="mb-4">
        <StatusBadge status={c.status} />
      </div>
      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Overview</CardTitle>
          </CardHeader>
          <CardBody className="grid gap-3 text-sm sm:grid-cols-2">
            <p>
              <span className="text-slate-400">Court</span>
              <br />
              {c.courtName || "—"}
            </p>
            <p>
              <span className="text-slate-400">Judge</span>
              <br />
              {c.judgeName || "—"}
            </p>
            <p>
              <span className="text-slate-400">Opposing party</span>
              <br />
              {c.opposingParty || "—"}
            </p>
            <p>
              <span className="text-slate-400">Opposing counsel</span>
              <br />
              {c.opposingCounsel || "—"}
            </p>
            <p className="sm:col-span-2">
              <span className="text-slate-400">Assignment</span>
              <br />
              {c.description || "No narrative on file."}
            </p>
          </CardBody>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Add note</CardTitle>
          </CardHeader>
          <CardBody>
            <Textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder="Hearing prep, call recap, strategy…" />
            <Button
              className="mt-3 w-full"
              onClick={async () => {
                await apiPost(`/api/v1/cases/${c.id}/notes`, { title: "Note", body: note });
                setNote("");
                load();
              }}
            >
              Save note
            </Button>
          </CardBody>
        </Card>
      </div>
      <Card className="mt-6">
        <CardHeader>
          <CardTitle>Notes</CardTitle>
        </CardHeader>
        <CardBody className="space-y-4">
          {(c.notes || []).length === 0 && <p className="text-sm text-slate-500">No notes yet.</p>}
          {(c.notes || []).map((n) => (
            <div key={n.id}>
              <p className="text-xs text-slate-400">{formatDateTime(n.createdAt)}</p>
              <p className="font-semibold">{n.title}</p>
              <p className="text-sm">{n.body}</p>
            </div>
          ))}
        </CardBody>
      </Card>
    </div>
  );
}
