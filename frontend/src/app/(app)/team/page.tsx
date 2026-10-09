"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPatch, apiPost } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { Button, PageHeader, TableWrap, Td, Th } from "@/components/page";
import { Dialog, ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select } from "@/components/ui/input";
import type { TeamMember } from "@/lib/types";

const ROLES = ["owner", "admin", "partner", "attorney", "associate", "paralegal", "receptionist", "staff"];
// Same order as the server (Roles.RANK): lowest first.
const RANK = ["staff", "receptionist", "paralegal", "associate", "attorney", "partner", "director", "admin", "owner"];
const TENANT_ADMINS = new Set(["owner", "admin", "partner", "director"]);

/** Roles the signed-in user may hand out. The server enforces the same rule. */
function grantable(myRole: string) {
  if (myRole === "owner") return ROLES;
  const mine = RANK.indexOf(myRole);
  return ROLES.filter((r) => r !== "owner" && RANK.indexOf(r) <= mine);
}

const EMPTY = { firstName: "", lastName: "", email: "", role: "associate", hourlyRate: "250" };

export default function TeamPage() {
  const { user } = useAuth();
  const myRole = user?.role || "";
  const canManage = TENANT_ADMINS.has(myRole);
  const roles = grantable(myRole);
  const [rows, setRows] = useState<TeamMember[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState(EMPTY);
  const [issued, setIssued] = useState<{ email: string; password: string } | null>(null);

  const load = () => apiGet<TeamMember[]>("/api/v1/users").then(setRows).catch((e) => setError(e.message));
  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <PageHeader
        title="Team & roles"
        subtitle={
          canManage
            ? "Invite attorneys and staff. Each invite gets a one-time password that must be changed at first sign-in."
            : "Your colleagues and their roles. Ask a firm owner or admin to invite people or change roles."
        }
        actions={
          canManage ? (
            <Button
              onClick={() => {
                setForm(EMPTY);
                setDialogError(null);
                setOpen(true);
              }}
            >
              Invite
            </Button>
          ) : undefined
        }
      />
      <ErrorBanner error={error} />
      {issued && (
        <div className="mb-4 rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900" role="status">
          <p className="font-semibold">One-time password for {issued.email}</p>
          <p className="mt-1">
            <code className="rounded bg-white px-2 py-1 font-mono text-base" data-testid="temporary-password">
              {issued.password}
            </code>
          </p>
          <p className="mt-2 text-xs">
            Share it with them privately. It is shown only once and they must choose their own password when they first sign in.
          </p>
          <button type="button" className="mt-2 text-xs font-semibold underline" onClick={() => setIssued(null)}>
            Done
          </button>
        </div>
      )}
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
          {rows.map((u) => {
            const editable =
              canManage && u.id !== user?.id && (myRole === "owner" || u.role !== "owner") && roles.includes(u.role);
            return (
              <tr key={u.id}>
                <Td className="font-semibold">{u.fullName}</Td>
                <Td>{u.email}</Td>
                <Td>
                  {editable ? (
                    <select
                      className="h-8 rounded border px-2 text-xs"
                      value={u.role}
                      aria-label={`Role for ${u.fullName}`}
                      onChange={async (e) => {
                        setError(null);
                        try {
                          await apiPatch(`/api/v1/users/${u.id}`, { role: e.target.value });
                        } catch (err) {
                          setError(err instanceof Error ? err.message : "Could not change the role");
                        }
                        await load();
                      }}
                    >
                      {roles.map((r) => (
                        <option key={r}>{r}</option>
                      ))}
                    </select>
                  ) : (
                    <span className="text-xs">{u.role}</span>
                  )}
                </Td>
                <Td>{u.hourlyRate ?? "n/a"}</Td>
                <Td>{u.onlineStatus || "offline"}</Td>
              </tr>
            );
          })}
        </tbody>
      </TableWrap>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Invite teammate"
        footer={
          <Button
            disabled={saving}
            onClick={async () => {
              setSaving(true);
              setDialogError(null);
              try {
                const created = await apiPost<{ email: string; temporaryPassword?: string }>("/api/v1/users/invite", form);
                if (created.temporaryPassword) setIssued({ email: created.email, password: created.temporaryPassword });
                setOpen(false);
                await load();
              } catch (err) {
                setDialogError(err instanceof Error ? err.message : "Could not send the invite");
              } finally {
                setSaving(false);
              }
            }}
          >
            {saving ? "Sending…" : "Send invite"}
          </Button>
        }
      >
        <ErrorBanner error={dialogError} />
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
              {roles.map((r) => (
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
