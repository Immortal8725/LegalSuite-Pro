"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost, apiPut } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { ADMIN_ROLES } from "@/lib/litigation";
import { OutboundPanel } from "@/components/outbound-panel";
import { useConfirm } from "@/components/confirm-provider";
import { DataTable, downloadCsv } from "@/components/data-table";
import { useDebouncedFilter } from "@/components/filter-bar";
import { Button, PageHeader, StatusBadge } from "@/components/page";
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
  const [selected, setSelected] = useState<string[]>([]);
  const { text, setText, query } = useDebouncedFilter();
  const { confirm } = useConfirm();
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
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <label className="text-sm">
          Search
          <input className="mt-1 block rounded border px-3 py-2" value={text} onChange={(e) => setText(e.target.value)} placeholder="Name, email or phone" />
        </label>
        {selected.length > 0 && (
          <>
            <Button variant="outline" onClick={() => {
              const chosen = rows.filter((c) => selected.includes(c.id));
              downloadCsv("clients.csv", ["Name", "Email", "Phone", "Status"], chosen.map((c) => [c.displayName || "", c.email || "", c.phone || "", c.status || ""]));
            }}>Export selected</Button>
            <Button onClick={async () => {
              const yes = await confirm({ title: `Mark ${selected.length} clients inactive?`, body: "They stay on the file. You can set them active again later.", confirmLabel: "Mark inactive" });
              if (!yes) return;
              await apiPost("/api/v1/clients/batch-status", { ids: selected, status: "inactive" });
              setSelected([]);
              load();
            }}>Mark inactive</Button>
          </>
        )}
      </div>
      <DataTable
        rows={rows.filter((c) => !query || [c.displayName, c.email, c.phone, c.source].join(" ").toLowerCase().includes(query))}
        selected={selected}
        onSelected={setSelected}
        empty={<EmptyState title="No clients" body="Add a retained client or wait for an intake from your public site." />}
        columns={[
          { key: "name", header: "Name", sortValue: (c) => c.displayName || "", render: (c) => <span className="font-semibold">{c.displayName}</span> },
          { key: "type", header: "Type", sortValue: (c) => c.type || "", render: (c) => c.type },
          { key: "email", header: "Email", sortValue: (c) => c.email || "", render: (c) => c.email },
          { key: "phone", header: "Phone", sortValue: (c) => c.phone || "", render: (c) => c.phone },
          { key: "source", header: "Source", sortValue: (c) => c.source || "", render: (c) => c.source },
          { key: "status", header: "Status", sortValue: (c) => c.status || "", render: (c) => <StatusBadge status={c.status} /> },
          { key: "portal", header: "Portal", sortValue: (c) => (c.portalEnabled ? 1 : 0), render: (c) => (c.portalEnabled ? "On" : "Off") },
          { key: "actions", header: "Actions", render: (c) => (
            <div className="flex flex-wrap gap-1">
              <Button size="sm" variant="outline" onClick={() => openEdit(c)} disabled={c.status === "merged"}>Edit</Button>
              <Button size="sm" variant="outline" onClick={() => setMessaging(c)}>Message</Button>
              {isAdmin && c.status !== "merged" && <Button size="sm" variant="outline" onClick={() => openMerge(c)}>Merge duplicate</Button>}
            </div>
          ) },
        ]}
      />
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
