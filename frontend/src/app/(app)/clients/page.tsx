"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { Button, PageHeader, StatusBadge, TableWrap, Td, Th } from "@/components/page";
import { Dialog, EmptyState, ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import type { Party } from "@/lib/types";

export default function ClientsPage() {
  const [rows, setRows] = useState<Party[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ type: "individual", firstName: "", lastName: "", companyName: "", email: "", phone: "", source: "website" });

  const load = () => apiGet<Party[]>("/api/v1/clients").then(setRows).catch((e) => setError(e.message));
  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <PageHeader title="Clients" subtitle="People and companies the firm represents. Portal access is a toggle." actions={<Button onClick={() => setOpen(true)}>New client</Button>} />
      <ErrorBanner error={error} />
      {rows.length === 0 ? (
        <EmptyState title="No clients" body="Add a retained client or wait for an intake from your public site." />
      ) : (
        <TableWrap>
          <thead>
            <tr>
              <Th>Name</Th>
              <Th>Type</Th>
              <Th>Email</Th>
              <Th>Phone</Th>
              <Th>Source</Th>
              <Th>Status</Th>
              <Th>Portal</Th>
            </tr>
          </thead>
          <tbody>
            {rows.map((c) => (
              <tr key={c.id}>
                <Td className="font-semibold">{c.displayName}</Td>
                <Td>{c.type}</Td>
                <Td>{c.email}</Td>
                <Td>{c.phone}</Td>
                <Td>{c.source}</Td>
                <Td>
                  <StatusBadge status={c.status} />
                </Td>
                <Td>{c.portalEnabled ? "On" : "Off"}</Td>
              </tr>
            ))}
          </tbody>
        </TableWrap>
      )}
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="New client"
        footer={
          <Button
            onClick={async () => {
              await apiPost("/api/v1/clients", form);
              setOpen(false);
              load();
            }}
          >
            Save
          </Button>
        }
      >
        <div className="space-y-3">
          <Select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
            <option value="individual">Individual</option>
            <option value="company">Company</option>
          </Select>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>First name</Label>
              <Input value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} />
            </div>
            <div>
              <Label>Last name</Label>
              <Input value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} />
            </div>
          </div>
          <div>
            <Label>Company</Label>
            <Input value={form.companyName} onChange={(e) => setForm({ ...form, companyName: e.target.value })} />
          </div>
          <Input placeholder="Email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          <Input placeholder="Phone" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
        </div>
      </Dialog>
    </div>
  );
}
