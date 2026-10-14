"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { moneyExact } from "@/lib/utils";
import { Button, PageHeader, TableWrap, Td, Th } from "@/components/page";
import { Dialog, ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select } from "@/components/ui/input";
import type { Expense, Matter } from "@/lib/types";

export default function ExpensesPage() {
  const [rows, setRows] = useState<Expense[]>([]);
  const [open, setOpen] = useState(false);
  const [matters, setMatters] = useState<Matter[]>([]);
  const [form, setForm] = useState({ caseId: "", category: "filing fees", description: "", amount: "", vendor: "" });
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const load = () =>
    apiGet<Expense[]>("/api/v1/expenses")
      .then((r) => {
        setRows(r);
        setError(null);
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load expenses"));
  useEffect(() => {
    load();
    apiGet<Matter[]>("/api/v1/cases").then(setMatters).catch(() => setMatters([]));
  }, []);

  return (
    <div>
      <PageHeader title="Expenses" subtitle="Court costs and expert fees waiting to hit the next invoice." actions={
          <Button
            onClick={() => {
              setDialogError(null);
              setOpen(true);
            }}
          >
            Log expense
          </Button>
        }
      />
      <ErrorBanner error={error} />
      <TableWrap>
        <thead>
          <tr>
            <Th>Date</Th>
            <Th>Category</Th>
            <Th>Vendor</Th>
            <Th>Description</Th>
            <Th>Amount</Th>
            <Th>Status</Th>
          </tr>
        </thead>
        <tbody>
          {rows.map((e) => (
            <tr key={e.id}>
              <Td>{e.date}</Td>
              <Td>{e.category}</Td>
              <Td>{e.vendor}</Td>
              <Td>{e.description}</Td>
              <Td>{moneyExact(e.amount)}</Td>
              <Td>{e.billed ? "Billed" : e.status}</Td>
            </tr>
          ))}
        </tbody>
      </TableWrap>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Expense"
        footer={
          <Button
            disabled={saving}
            onClick={async () => {
              setSaving(true);
              setDialogError(null);
              try {
                await apiPost("/api/v1/expenses", { ...form, caseId: form.caseId || undefined });
                setOpen(false);
                setForm({ ...form, description: "", amount: "", vendor: "" });
                load();
              } catch (err) {
                setDialogError(err instanceof Error ? err.message : "Could not save the expense");
              } finally {
                setSaving(false);
              }
            }}
          >
            {saving ? "Saving…" : "Save"}
          </Button>
        }
      >
        <ErrorBanner error={dialogError} />
        <Label>Matter</Label>
        <Select aria-label="Matter" value={form.caseId} onChange={(e) => setForm({ ...form, caseId: e.target.value })}>
          <option value="">No matter (firm cost, not billed to a client)</option>
          {matters.map((m) => (
            <option key={m.id} value={m.id}>
              {m.caseNumber} {m.title}
              {m.clientName ? ` (${m.clientName})` : ""}
            </option>
          ))}
        </Select>
        <Label className="mt-3">Category</Label>
        <Select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
          <option>filing fees</option>
          <option>travel</option>
          <option>expert fees</option>
          <option>postage</option>
          <option>other</option>
        </Select>
        <Input className="mt-3" placeholder="Vendor" value={form.vendor} onChange={(e) => setForm({ ...form, vendor: e.target.value })} />
        <Input className="mt-3" placeholder="Description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <Input className="mt-3" type="number" placeholder="Amount" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} />
      </Dialog>
    </div>
  );
}
