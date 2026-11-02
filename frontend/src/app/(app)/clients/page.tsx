"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost, apiPut } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { ADMIN_ROLES } from "@/lib/litigation";
import { OutboundPanel } from "@/components/outbound-panel";
import { Button, PageHeader, StatusBadge, TableWrap, Td, Th } from "@/components/page";
import { Dialog, EmptyState, ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import { moneyExact } from "@/lib/utils";
import type { Party } from "@/lib/types";

const EMPTY = { type: "individual", firstName: "", lastName: "", companyName: "", email: "", phone: "", idNumber: "", addressLine1: "", source: "website" };

type MergePreview = {
  matters: number;
  documents: number;
  messages: number;
  unpaidInvoicesToMove: number;
  paidInvoicesKeptOnOldRecord: number;
  trust: { accountName: string; amount: number }[];
  sameIdNumber: boolean;
};

export default function ClientsPage() {
  const { user } = useAuth();
  const isAdmin = ADMIN_ROLES.includes(String(user?.role ?? ""));
  const [editing, setEditing] = useState<Party | null>(null);
  const [merging, setMerging] = useState<Party | null>(null);
  const [mergeInto, setMergeInto] = useState("");
  const [mergeReason, setMergeReason] = useState("");
  const [mergePreview, setMergePreview] = useState<MergePreview | null>(null);
  const [mergeError, setMergeError] = useState<string | null>(null);
  const [rows, setRows] = useState<Party[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const openDialog = () => {
    setDialogError(null);
    setEditing(null);
    setForm(EMPTY);
    setOpen(true);
  };
  const openEdit = (c: Party) => {
    setDialogError(null);
    setEditing(c);
    setForm({
      type: c.type || "individual",
      firstName: c.firstName || "",
      lastName: c.lastName || "",
      companyName: c.companyName || "",
      email: c.email || "",
      phone: c.phone || "",
      idNumber: c.idNumber || "",
      addressLine1: c.addressLine1 || "",
      source: c.source || "website",
    });
    setOpen(true);
  };
  const openMerge = (c: Party) => {
    setMergeError(null);
    setMergeInto("");
    setMergeReason("");
    setMergePreview(null);
    setMerging(c);
  };
  useEffect(() => {
    setMergePreview(null);
    if (!merging || !mergeInto) return;
    apiGet<MergePreview>(`/api/v1/clients/${merging.id}/merge/preview?intoClientId=${encodeURIComponent(mergeInto)}`)
      .then(setMergePreview)
      .catch((e) => setMergeError(e instanceof Error ? e.message : "Could not load the preview"));
  }, [merging, mergeInto]);
  const [messaging, setMessaging] = useState<Party | null>(null);
  const [form, setForm] = useState(EMPTY);

  const load = () => apiGet<Party[]>("/api/v1/clients").then(setRows).catch((e) => setError(e.message));
  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <PageHeader title="Clients" subtitle="People and companies the firm represents. Portal access is a toggle." actions={<Button onClick={openDialog}>New client</Button>} />
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
              <Th>Actions</Th>
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
                <Td>
                  <div className="flex flex-wrap gap-1">
                    <Button size="sm" variant="outline" onClick={() => openEdit(c)} disabled={c.status === "merged"}>
                      Edit
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => setMessaging(c)}>
                      Message
                    </Button>
                    {isAdmin && c.status !== "merged" && (
                      <Button size="sm" variant="outline" onClick={() => openMerge(c)}>
                        Merge duplicate
                      </Button>
                    )}
                  </div>
                </Td>
              </tr>
            ))}
          </tbody>
        </TableWrap>
      )}
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? `Edit ${editing.displayName}` : "New client"}
        footer={
          <Button
            disabled={saving}
            onClick={async () => {
              setSaving(true);
              setDialogError(null);
              try {
                if (editing) await apiPut(`/api/v1/clients/${editing.id}`, form);
                else await apiPost("/api/v1/clients", form);
                setOpen(false);
                load();
              } catch (err) {
                setDialogError(err instanceof Error ? err.message : "Could not save the client");
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
        <div className="space-y-3">
          <Select aria-label="Client type" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
            <option value="individual">Individual</option>
            <option value="company">Company</option>
          </Select>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>First name</Label>
              <Input aria-label="First name" value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} />
            </div>
            <div>
              <Label>Last name</Label>
              <Input aria-label="Last name" value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} />
            </div>
          </div>
          <div>
            <Label>Company</Label>
            <Input aria-label="Company" value={form.companyName} onChange={(e) => setForm({ ...form, companyName: e.target.value })} />
          </div>
          <Input placeholder="Email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          <Input placeholder="Phone" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          <div>
            <Label>{form.type === "company" ? "Registration number" : "ID or passport number"}</Label>
            <Input
              aria-label="ID number"
              value={form.idNumber}
              onChange={(e) => setForm({ ...form, idNumber: e.target.value })}
              placeholder={form.type === "company" ? "2020/123456/07" : "13 digit SA ID"}
            />
            <p className="mt-1 text-xs text-slate-600">Helps tell apart two clients with the same name.</p>
          </div>
          <Input placeholder="Street address" value={form.addressLine1} onChange={(e) => setForm({ ...form, addressLine1: e.target.value })} />
        </div>
      </Dialog>
      <Dialog
        open={!!merging}
        onClose={() => setMerging(null)}
        title={merging ? `Merge ${merging.displayName} into another record` : "Merge"}
        footer={
          <Button
            disabled={saving || !mergeInto || mergeReason.trim().length < 10 || !mergePreview}
            onClick={async () => {
              if (!merging) return;
              setSaving(true);
              setMergeError(null);
              try {
                await apiPost(`/api/v1/clients/${merging.id}/merge`, { intoClientId: mergeInto, reason: mergeReason });
                setMerging(null);
                load();
              } catch (err) {
                setMergeError(err instanceof Error ? err.message : "Could not merge");
              } finally {
                setSaving(false);
              }
            }}
          >
            {saving ? "Merging…" : "Merge"}
          </Button>
        }
      >
        <ErrorBanner error={mergeError} />
        <div className="space-y-3 text-sm">
          <p className="text-slate-700">
            Use this only when both records are the same person or company. Matters, messages, documents and unpaid invoices move to the record you keep. Paid
            invoices stay on this record for history. Trust money moves through a transfer a second firm admin approves. This record is marked merged and its
            portal login is switched off.
          </p>
          <div>
            <Label>Keep this record</Label>
            <Select aria-label="Keep this record" value={mergeInto} onChange={(e) => setMergeInto(e.target.value)}>
              <option value="">Choose the record to keep</option>
              {rows
                .filter((r) => merging && r.id !== merging.id && r.status !== "merged")
                .map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.displayName}
                    {r.idNumber ? ` (${r.idNumber})` : r.email ? ` (${r.email})` : ""}
                  </option>
                ))}
            </Select>
          </div>
          {mergePreview && (
            <div className="rounded-lg border p-3">
              <p>
                Matters {mergePreview.matters}, messages {mergePreview.messages}, documents {mergePreview.documents}, unpaid invoices{" "}
                {mergePreview.unpaidInvoicesToMove}. Paid invoices kept here: {mergePreview.paidInvoicesKeptOnOldRecord}.
              </p>
              {mergePreview.trust.length > 0 && (
                <p className="mt-1">Trust to transfer: {mergePreview.trust.map((t) => `${moneyExact(t.amount)} in ${t.accountName}`).join(", ")}.</p>
              )}
              {!mergePreview.sameIdNumber && (
                <p className="mt-1 text-amber-900">The ID numbers do not match or are missing. Check this is really the same person.</p>
              )}
            </div>
          )}
          <div>
            <Label>Reason (goes in the audit log)</Label>
            <Textarea aria-label="Merge reason" rows={2} value={mergeReason} onChange={(e) => setMergeReason(e.target.value)} />
          </div>
        </div>
      </Dialog>
      <Dialog open={!!messaging} onClose={() => setMessaging(null)} title={messaging ? `Message ${messaging.displayName}` : "Message"} wide>
        {messaging && (
          <OutboundPanel
            clientId={messaging.id}
            defaultPhone={messaging.phone || ""}
            defaultEmail={messaging.email || ""}
          />
        )}
      </Dialog>
    </div>
  );
}
