"use client";

import { useState } from "react";
import { apiBlob, apiPost, saveBlob } from "@/lib/api";
import { moneyExact } from "@/lib/utils";
import { Button } from "@/components/page";
import { Dialog, ErrorBanner } from "@/components/ui/dialog";

type Link = { url: string; amount: number; mode: string; expiresAt: string; invoiceNumber: string; clientName?: string; clientEmail?: string; clientPhone?: string };

/** South African numbers to wa.me format: 082 123 4567 becomes 27821234567. */
export function waNumber(phone?: string | null) {
  if (!phone) return "";
  let d = phone.replace(/\D/g, "");
  if (d.startsWith("0")) d = "27" + d.slice(1);
  return d;
}

export function PayLinkButton({ invoiceId, clientId }: { invoiceId: string; clientId?: string }) {
  const [link, setLink] = useState<Link | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [copied, setCopied] = useState(false);

  const msg = link
    ? `Good day ${link.clientName ?? ""}. Here is a secure link to pay invoice ${link.invoiceNumber} (${moneyExact(link.amount)}) by card or instant EFT: ${link.url}`
    : "";

  return (
    <>
      <button
        className="text-sm font-semibold text-navy"
        onClick={async () => {
          setError(null);
          setOpen(true);
          try {
            setLink(await apiPost<Link>(`/api/v1/invoices/${invoiceId}/payment-link`));
          } catch (e) {
            setError(e instanceof Error ? e.message : "Could not create the link");
          }
        }}
      >
        Pay link
      </button>
      <Dialog open={open} onClose={() => setOpen(false)} title="Online payment link">
        <div className="grid gap-3 text-sm">
          <ErrorBanner error={error} />
          {link && (
            <>
              {link.mode === "sandbox" && (
                <p className="rounded bg-amber-50 p-2 text-amber-800">
                  Test mode: this link uses the PayFast sandbox, so no real money moves. Add your PayFast details under Settings, Online payments to take real payments.
                </p>
              )}
              <p>
                {link.invoiceNumber} for {moneyExact(link.amount)}. The link works for 30 days. When the client pays, the payment is recorded on the invoice automatically.
              </p>
              <input readOnly value={link.url} className="w-full rounded border px-2 py-1 font-mono text-xs" onFocus={(e) => e.target.select()} />
              <div className="flex flex-wrap gap-2">
                <Button variant="outline" onClick={async () => { await navigator.clipboard.writeText(link.url); setCopied(true); }}>{copied ? "Copied" : "Copy link"}</Button>
                <a className="inline-flex h-10 items-center rounded bg-emerald-600 px-4 text-white" target="_blank" rel="noreferrer"
                   href={`https://wa.me/${waNumber(link.clientPhone)}?text=${encodeURIComponent(msg)}`}>Send on WhatsApp</a>
                <a className="inline-flex h-10 items-center rounded border px-4" href={`mailto:${link.clientEmail ?? ""}?subject=${encodeURIComponent(`Invoice ${link.invoiceNumber}`)}&body=${encodeURIComponent(msg)}`}>Email</a>
                {clientId && (
                  <Button variant="outline" onClick={async () => saveBlob(await apiBlob(`/api/v1/clients/${clientId}/statement.pdf`), "statement.pdf")}>Statement PDF</Button>
                )}
              </div>
            </>
          )}
        </div>
      </Dialog>
    </>
  );
}
