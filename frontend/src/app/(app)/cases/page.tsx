"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { Button, Loading, PageHeader, StatusBadge, TableWrap, Td, Th } from "@/components/page";
import { Dialog, EmptyState, ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import type { Matter, Party } from "@/lib/types";

export default function CasesPage() {
  const [rows, setRows] = useState<Matter[]>([]);
  const [clients, setClients] = useState<Party[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ title: "", clientId: "", practiceArea: "Litigation", description: "" });

  const load = () =>
    Promise.all([apiGet<Matter[]>("/api/v1/cases"), apiGet<Party[]>("/api/v1/clients")])
      .then(([c, cl]) => {
        setRows(c);
        setClients(cl);
      })
      .catch((e) => setError(e.message));

  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <PageHeader
        title="Cases & matters"
        subtitle="Every open file, court, and opposing party — filter later, act now."
        actions={<Button onClick={() => setOpen(true)}>New matter</Button>}
      />
      <ErrorBanner error={error} />
      {rows.length === 0 && !error ? (
        <EmptyState title="No matters yet" body="Open a file from intake or add a retained client first." action={<Button onClick={() => setOpen(true)}>New matter</Button>} />
      ) : (
        <TableWrap>
          <thead>
            <tr>
              <Th>#</Th>
              <Th>Title</Th>
              <Th>Client</Th>
              <Th>Area</Th>
              <Th>Priority</Th>
              <Th>Status</Th>
            </tr>
          </thead>
          <tbody>
            {rows.map((c) => (
              <tr key={c.id} className="hover:bg-slate-50">
                <Td className="font-mono">
                  <Link className="text-navy font-semibold" href={`/cases/${c.id}`}>
                    {c.caseNumber}
                  </Link>
                </Td>
                <Td>{c.title}</Td>
                <Td>{c.clientName}</Td>
                <Td>{c.practiceArea}</Td>
                <Td>{c.priority}</Td>
                <Td>
                  <StatusBadge status={c.status} />
                </Td>
              </tr>
            ))}
          </tbody>
        </TableWrap>
      )}
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Open a matter"
        footer={
          <Button
            onClick={async () => {
              await apiPost("/api/v1/cases", form);
              setOpen(false);
              load();
            }}
          >
            Create
          </Button>
        }
      >
        <div className="space-y-3">
          <div>
            <Label>Title</Label>
            <Input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
          </div>
          <div>
            <Label>Client</Label>
            <Select value={form.clientId} onChange={(e) => setForm({ ...form, clientId: e.target.value })}>
              <option value="">Select…</option>
              {clients.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.displayName}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <Label>Practice area</Label>
            <Input value={form.practiceArea} onChange={(e) => setForm({ ...form, practiceArea: e.target.value })} />
          </div>
          <div>
            <Label>Facts / assignment</Label>
            <Textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
        </div>
      </Dialog>
    </div>
  );
}
