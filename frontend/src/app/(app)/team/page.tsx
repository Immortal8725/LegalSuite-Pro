"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPatch, apiPost } from "@/lib/api";
import { Button, PageHeader, TableWrap, Td, Th } from "@/components/page";
import { Dialog, ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select } from "@/components/ui/input";
import type { TeamMember } from "@/lib/types";

const ROLES = ["owner", "admin", "partner", "attorney", "associate", "paralegal", "receptionist", "staff"];

export default function TeamPage() {
  const [rows, setRows] = useState<TeamMember[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ firstName: "", lastName: "", email: "", role: "associate", hourlyRate: "250", password: "changeme123" });

  const load = () => apiGet<TeamMember[]>("/api/v1/users").then(setRows).catch((e) => setError(e.message));
  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <PageHeader
        title="Team & roles"
        subtitle="Invite attorneys and staff. Temporary password is emailed in production; here it is set on the invite."
        actions={<Button onClick={() => setOpen(true)}>Invite</Button>}
      />
      <ErrorBanner error={error} />
      <TableWrap>
        <thead>
          <tr>
            <Th>Name</Th>
            <Th>Email</Th>
            <Th>Role</Th>
            <Th>Rate</Th>
            <Th>Status</Th>
          </tr>
        </thead>
        <tbody>
          {rows.map((u) => (
            <tr key={u.id}>
              <Td className="font-semibold">{u.fullName}</Td>
              <Td>{u.email}</Td>
              <Td>
                <select
                  className="h-8 rounded border px-2 text-xs"
                  value={u.role}
                  onChange={async (e) => {
                    await apiPatch(`/api/v1/users/${u.id}`, { role: e.target.value });
                    await load();
                  }}
                >
                  {ROLES.map((r) => (
                    <option key={r}>{r}</option>
                  ))}
                </select>
              </Td>
              <Td>${u.hourlyRate ?? "—"}</Td>
              <Td>{u.onlineStatus || "offline"}</Td>
            </tr>
          ))}
        </tbody>
      </TableWrap>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Invite teammate"
        footer={
          <Button
            onClick={async () => {
              await apiPost("/api/v1/users/invite", form);
              setOpen(false);
              await load();
            }}
          >
            Send invite
          </Button>
        }
      >
        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <Label>First name</Label>
            <Input value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} />
          </div>
          <div>
            <Label>Last name</Label>
            <Input value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} />
          </div>
          <div className="sm:col-span-2">
            <Label>Email</Label>
            <Input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </div>
          <div>
            <Label>Role</Label>
            <Select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
              {ROLES.map((r) => (
                <option key={r}>{r}</option>
              ))}
            </Select>
          </div>
          <div>
            <Label>Hourly rate</Label>
            <Input value={form.hourlyRate} onChange={(e) => setForm({ ...form, hourlyRate: e.target.value })} />
          </div>
        </div>
      </Dialog>
    </div>
  );
}
