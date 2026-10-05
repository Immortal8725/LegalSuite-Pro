"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { Button, PageHeader, TableWrap, Td, Th } from "@/components/page";
import { Dialog, EmptyState } from "@/components/ui/dialog";
import { Input, Label, Select } from "@/components/ui/input";
import type { Contact } from "@/lib/types";

export default function ContactsPage() {
  const [rows, setRows] = useState<Contact[]>([]);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ type: "opposing_counsel", firstName: "", lastName: "", company: "", email: "", phone: "" });
  const load = () => apiGet<Contact[]>("/api/v1/contacts").then(setRows);
  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <PageHeader title="Contacts" subtitle="Judges, opposing counsel, experts, and vendors." actions={<Button onClick={() => setOpen(true)}>Add contact</Button>} />
      {rows.length === 0 ? (
        <EmptyState title="No contacts" body="Add opposing counsel before the first hearing." />
      ) : (
        <TableWrap>
          <thead>
            <tr>
              <Th>Name</Th>
              <Th>Role</Th>
              <Th>Organization</Th>
              <Th>Email</Th>
              <Th>Phone</Th>
            </tr>
          </thead>
          <tbody>
            {rows.map((c) => (
              <tr key={c.id}>
                <Td>{c.name}</Td>
                <Td>{c.type.replaceAll("_", " ")}</Td>
                <Td>{c.company}</Td>
                <Td>{c.email}</Td>
                <Td>{c.phone}</Td>
              </tr>
            ))}
          </tbody>
        </TableWrap>
      )}
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Contact"
        footer={
          <Button
            onClick={async () => {
              await apiPost("/api/v1/contacts", form);
              setOpen(false);
              load();
            }}
          >
            Save
          </Button>
        }
      >
        <div className="space-y-3">
          <Label>Type</Label>
          <Select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
            <option value="opposing_counsel">Opposing counsel</option>
            <option value="judge">Judge</option>
            <option value="expert_witness">Expert</option>
            <option value="vendor">Vendor</option>
          </Select>
          <Input placeholder="First name" value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} />
          <Input placeholder="Last name" value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} />
          <Input placeholder="Firm / court" value={form.company} onChange={(e) => setForm({ ...form, company: e.target.value })} />
          <Input placeholder="Email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </div>
      </Dialog>
    </div>
  );
}
