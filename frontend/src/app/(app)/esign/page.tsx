"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { apiGet, apiPost } from "@/lib/api";
import { Button, PageHeader, StatusBadge, TableWrap, Td, Th } from "@/components/page";
import { Dialog, EmptyState, ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import type { Matter, Party, SignReq } from "@/lib/types";

export default function EsignPage() {
  const [rows, setRows] = useState<SignReq[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [cases, setCases] = useState<Matter[]>([]);
  const [clients, setClients] = useState<Party[]>([]);
  const [form, setForm] = useState({
    title: "Engagement letter",
    signerName: "",
    signerEmail: "",
    documentBody: "",
    caseId: "",
    clientId: "",
  });

  const load = () => apiGet<SignReq[]>("/api/v1/signatures").then(setRows).catch((e) => setError(e.message));
  useEffect(() => {
    load();
    apiGet<Matter[]>("/api/v1/cases").then(setCases);
    apiGet<Party[]>("/api/v1/clients").then(setClients);
  }, []);

  return (
    <div>
      <PageHeader
        title="E-signatures"
        subtitle="Built-in signing. Share the public link — no DocuSign account."
        actions={<Button onClick={() => setOpen(true)}>Send for signature</Button>}
      />
      <ErrorBanner error={error} />
      {rows.length === 0 ? (
        <EmptyState title="Nothing out for signature" body="Send a retainer or a release. The signer gets a public link." />
      ) : (
        <TableWrap>
          <thead>
            <tr>
              <Th>Title</Th>
              <Th>Signer</Th>
              <Th>Status</Th>
              <Th>Link</Th>
              <Th></Th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <Td className="font-semibold">{r.title}</Td>
                <Td>
                  {r.signerName}
                  <div className="text-xs text-slate-400">{r.signerEmail}</div>
                </Td>
                <Td>
                  <StatusBadge status={r.status} />
                </Td>
                <Td>
                  <Link className="text-sm font-semibold text-navy" href={r.signUrl || `/sign/${r.id}`} target="_blank">
                    Open sign page
                  </Link>
                </Td>
                <Td>
                  {r.status === "pending" && (
                    <button
                      className="text-sm text-red-600"
                      onClick={async () => {
                        await apiPost(`/api/v1/signatures/${r.id}/void`, {});
                        load();
                      }}
                    >
                      Void
                    </button>
                  )}
                </Td>
              </tr>
            ))}
          </tbody>
        </TableWrap>
      )}
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Send a document"
        wide
        footer={
          <Button
            onClick={async () => {
              await apiPost("/api/v1/signatures", form);
              setOpen(false);
              load();
            }}
          >
            Create link
          </Button>
        }
      >
        <Label>Title</Label>
        <Input className="mb-3" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
        <div className="mb-3 grid gap-3 sm:grid-cols-2">
          <div>
            <Label>Signer name</Label>
            <Input value={form.signerName} onChange={(e) => setForm({ ...form, signerName: e.target.value })} />
          </div>
          <div>
            <Label>Signer email</Label>
            <Input value={form.signerEmail} onChange={(e) => setForm({ ...form, signerEmail: e.target.value })} />
          </div>
        </div>
        <div className="mb-3 grid gap-3 sm:grid-cols-2">
          <Select value={form.caseId} onChange={(e) => setForm({ ...form, caseId: e.target.value })}>
            <option value="">Matter (optional)</option>
            {cases.map((c) => (
              <option key={c.id} value={c.id}>
                {c.caseNumber}
              </option>
            ))}
          </Select>
          <Select
            value={form.clientId}
            onChange={(e) => {
              const id = e.target.value;
              const cl = clients.find((c) => c.id === id);
              setForm({
                ...form,
                clientId: id,
                signerName: cl?.displayName || form.signerName,
                signerEmail: cl?.email || form.signerEmail,
              });
            }}
          >
            <option value="">Client (optional)</option>
            {clients.map((c) => (
              <option key={c.id} value={c.id}>
                {c.displayName}
              </option>
            ))}
          </Select>
        </div>
        <Label>Document</Label>
        <Textarea rows={8} value={form.documentBody} onChange={(e) => setForm({ ...form, documentBody: e.target.value })} />
      </Dialog>
    </div>
  );
}
