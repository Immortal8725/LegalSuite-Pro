"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { hoursFromMinutes, moneyExact } from "@/lib/utils";
import { Button, PageHeader, TableWrap, Td, Th } from "@/components/page";
import { Dialog } from "@/components/ui/dialog";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import { useTimer } from "@/components/timer-provider";
import type { Matter, TimeRow } from "@/lib/types";

export default function TimePage() {
  const [rows, setRows] = useState<TimeRow[]>([]);
  const [cases, setCases] = useState<Matter[]>([]);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ caseId: "", durationMinutes: "36", description: "Legal research" });
  const { timer, start, stop, label } = useTimer();
  const load = () => apiGet<TimeRow[]>("/api/v1/time-entries").then(setRows);
  useEffect(() => {
    load();
    apiGet<Matter[]>("/api/v1/cases").then(setCases);
  }, []);

  return (
    <div>
      <PageHeader
        title="Time tracking"
        subtitle="Live timer uses 6-minute increments. Calls also post time when they hang up."
        actions={
          <div className="flex gap-2">
            {timer.running ? (
              <Button variant="gold" onClick={() => stop("Timed work").then(load)}>
                Stop {label}
              </Button>
            ) : (
              <Button onClick={() => start(form.caseId || undefined)}>Start timer</Button>
            )}
            <Button variant="outline" onClick={() => setOpen(true)}>
              Manual entry
            </Button>
          </div>
        }
      />
      <TableWrap>
        <thead>
          <tr>
            <Th>Date</Th>
            <Th>Description</Th>
            <Th>Hours</Th>
            <Th>Amount</Th>
            <Th>Source</Th>
            <Th>Billed</Th>
          </tr>
        </thead>
        <tbody>
          {rows.map((t) => (
            <tr key={t.id}>
              <Td>{t.date}</Td>
              <Td>{t.description}</Td>
              <Td>{hoursFromMinutes(t.durationMinutes)}</Td>
              <Td>{moneyExact(t.totalAmount)}</Td>
              <Td>{t.source}</Td>
              <Td>{t.billed ? "Yes" : t.billable ? "Ready" : "Non-billable"}</Td>
            </tr>
          ))}
        </tbody>
      </TableWrap>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Log time"
        footer={
          <Button
            onClick={async () => {
              await apiPost("/api/v1/time-entries", form);
              setOpen(false);
              load();
            }}
          >
            Save
          </Button>
        }
      >
        <div className="space-y-3">
          <Label>Matter</Label>
          <Select value={form.caseId} onChange={(e) => setForm({ ...form, caseId: e.target.value })}>
            <option value="">Unassigned</option>
            {cases.map((c) => (
              <option key={c.id} value={c.id}>
                {c.caseNumber} {c.title}
              </option>
            ))}
          </Select>
          <Label>Minutes</Label>
          <Input type="number" value={form.durationMinutes} onChange={(e) => setForm({ ...form, durationMinutes: e.target.value })} />
          <Textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </div>
      </Dialog>
    </div>
  );
}
