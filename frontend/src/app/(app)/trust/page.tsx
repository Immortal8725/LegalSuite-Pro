"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { moneyExact } from "@/lib/utils";
import { useAuth } from "@/components/auth-provider";
import { Button, PageHeader, TableWrap, Td, Th } from "@/components/page";
import { Dialog, ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import type { Invoice, Matter, TrustAcct, TrustRecon, TrustTx } from "@/lib/types";
import { useLatest } from "@/lib/use-latest";

const TENANT_ADMINS = new Set(["owner", "admin", "partner", "director"]);

export default function TrustPage() {
  const { tenant, user } = useAuth();
  const canOpenAccount = TENANT_ADMINS.has((user?.role || "").toLowerCase());
  const [newOpen, setNewOpen] = useState(false);
  const [newAcct, setNewAcct] = useState({ accountName: "Firm Trust Account", bankName: "", accountType: "trust" });
  const [newError, setNewError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const createAccount = async () => {
    setNewError(null);
    setCreating(true);
    try {
      const made = await apiPost<TrustAcct>("/api/v1/trust/accounts", newAcct);
      setNewOpen(false);
      setSelected(made.id);
      setAccounts((prev) => [...prev, made]);
      setLedger([]);
      setRecon(await apiGet<TrustRecon>("/api/v1/trust/recon"));
    } catch (e) {
      setNewError(e instanceof Error ? e.message : "Could not open the trust account");
    } finally {
      setCreating(false);
    }
  };
  const za = tenant?.country === "ZA" || tenant?.jurisdiction === "ZA";
  const [accounts, setAccounts] = useState<TrustAcct[]>([]);
  const [selected, setSelected] = useState<string>("");
  const [ledger, setLedger] = useState<TrustTx[]>([]);
  const [recon, setRecon] = useState<TrustRecon | null>(null);
  const [open, setOpen] = useState(false);
  const [bankOpen, setBankOpen] = useState(false);
  const [csvOpen, setCsvOpen] = useState(false);
  const [move, setMove] = useState({ type: "deposit", amount: "5000", description: "Retainer deposit", caseId: "" });
  const [matters, setMatters] = useState<Matter[]>([]);
  const [moveError, setMoveError] = useState<string | null>(null);
  const [posting, setPosting] = useState(false);
  const moveMatter = matters.find((m) => m.id === move.caseId);
  const moveClientId = moveMatter?.clientId || "";
  const openMove = async () => {
    setMoveError(null);
    setOpen(true);
    try {
      setMatters(await apiGet<Matter[]>("/api/v1/cases"));
    } catch (e) {
      setMoveError(e instanceof Error ? e.message : "Could not load matters");
    }
  };
  const [bank, setBank] = useState({ bankBalance: "", notes: "" });
  const [csv, setCsv] = useState(
    "Date,Description,Amount,Balance\n"
  );
  const [error, setError] = useState<string | null>(null);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [feeOpen, setFeeOpen] = useState(false);
  const [openInvoices, setOpenInvoices] = useState<Invoice[]>([]);
  const [fee, setFee] = useState({ invoiceId: "", amount: "", note: "" });

  const load = async () => {
    const list = await apiGet<TrustAcct[]>("/api/v1/trust/accounts");
    setAccounts(list);
    const id = selected || list[0]?.id;
    if (id) {
      setSelected(id);
      setLedger(await apiGet<TrustTx[]>(`/api/v1/trust/accounts/${id}/ledger`));
    }
    setRecon(await apiGet<TrustRecon>("/api/v1/trust/recon"));
  };
  const loadLatest = useLatest(load);
  useEffect(() => {
    // Load once on mount. load() picks the selected account itself, so re-running on every change would loop.
    loadLatest.current().catch((e) => setError(e.message));
  }, [loadLatest]);

  const acct = accounts.find((a) => a.id === selected);
  const live = acct?.recon;
  const unbalanced = live?.status === "unbalanced";

  const ledgerFor = (clientId?: string) => {
    const row = live?.ledgers?.find((l) => l.clientId === clientId);
    return Number(row?.balance ?? 0);
  };

  const openFeeDialog = async () => {
    try {
      setError(null);
      const rows = await apiGet<Invoice[]>("/api/v1/invoices");
      const openRows = rows.filter((i) => Number(i.balanceDue) > 0);
      setOpenInvoices(openRows);
      const first = openRows[0];
      const cap = first ? Math.min(Number(first.balanceDue), ledgerFor(first.clientId)) : 0;
      setFee({ invoiceId: first?.id || "", amount: cap > 0 ? cap.toFixed(2) : "0.00", note: "" });
      setFeeOpen(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load invoices");
    }
  };

  const chosenInvoice = openInvoices.find((i) => i.id === fee.invoiceId);

  return (
    <div>
      <PageHeader
        title={za ? "Section 86 trust" : "Trust / IOLTA"}
        subtitle={
          za
            ? "Legal Practice Act ss 86–87. Bank statement, cashbook, and client ledgers must agree. You cannot spend another client's money."
            : "Client funds never mix with operating. Withdrawals refuse if this client's ledger would go negative."
        }
        actions={
          <div className="flex flex-wrap gap-2">
            {canOpenAccount && (
              <Button variant="outline" onClick={() => { setNewError(null); setNewOpen(true); }}>
                Open trust account
              </Button>
            )}
            {accounts.length > 0 && (
              <>
            <Button variant="outline" onClick={() => setCsvOpen(true)}>
              Import bank CSV
            </Button>
            <Button variant="outline" onClick={() => setBankOpen(true)}>
              Enter bank balance
            </Button>
            <Button variant="outline" onClick={() => openFeeDialog()}>
              Apply trust to fee
            </Button>
            <Button onClick={openMove}>Record movement</Button>
              </>
            )}
          </div>
        }
      />
      {error && <p className="mb-4 text-sm text-red-700">{error}</p>}
      {live && (
        <Card className={`mb-6 ${unbalanced ? "border-red-300 bg-red-50" : "border-emerald-200 bg-emerald-50"}`}>
          <CardHeader>
            <CardTitle>{unbalanced ? "Three-way unbalanced. Do not certify" : "Three-way balanced"}</CardTitle>
          </CardHeader>
          <CardBody className="grid grid-cols-1 gap-4 sm:grid-cols-3 text-sm">
            <div>
              <p className="text-xs text-slate-600">Bank statement</p>
              <p className="text-xl font-extrabold">{moneyExact(live.bankBalance)}</p>
            </div>
            <div>
              <p className="text-xs text-slate-600">Cashbook</p>
              <p className="text-xl font-extrabold">{moneyExact(live.bookBalance)}</p>
            </div>
            <div>
              <p className="text-xs text-slate-600">Client ledgers</p>
              <p className="text-xl font-extrabold">{moneyExact(live.clientLedgerTotal)}</p>
            </div>
            <p className="sm:col-span-3 text-xs text-slate-600">{live.rule}</p>
            {unbalanced && (
              <p className="sm:col-span-3 font-semibold text-red-900">
                Difference {moneyExact(live.difference)}. Find the missing deposit or the unrecorded transfer before an
                inspector does.
              </p>
            )}
          </CardBody>
        </Card>
      )}
      {accounts.length === 0 && !error && (
        <Card className="mb-6">
          <CardHeader>
            <CardTitle>No trust account yet</CardTitle>
          </CardHeader>
          <CardBody className="space-y-3 text-sm text-slate-700">
            <p>
              {za
                ? "Open your firm's section 86 trust account here before you take any client money. The cashbook starts at R0.00 and grows only through recorded deposits."
                : "Open the firm's trust (IOLTA) account here before you take any client money."}
            </p>
            {canOpenAccount ? (
              <Button onClick={() => { setNewError(null); setNewOpen(true); }}>Open trust account</Button>
            ) : (
              <p className="text-slate-600">Ask a firm owner, director or partner to open it.</p>
            )}
          </CardBody>
        </Card>
      )}
      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
        {accounts.map((a) => (
          <button
            key={a.id}
            onClick={() => {
              setSelected(a.id);
              apiGet<TrustTx[]>(`/api/v1/trust/accounts/${a.id}/ledger`).then(setLedger);
            }}
            className="text-left"
          >
            <Card className={a.id === selected ? "ring-2 ring-gold" : ""}>
              <CardHeader>
                <CardTitle>{a.accountName}</CardTitle>
              </CardHeader>
              <CardBody>
                <p className="text-2xl font-extrabold text-navy">{moneyExact(a.balance)}</p>
                <p className="text-xs text-slate-600">{a.bankName}</p>
              </CardBody>
            </Card>
          </button>
        ))}
      </div>
      {live?.ledgers && live.ledgers.length > 0 && (
        <Card className="mb-6">
          <CardHeader>
            <CardTitle>Client ledgers</CardTitle>
          </CardHeader>
          <CardBody className="space-y-2 text-sm">
            {live.ledgers.map((row, i) => (
              <div key={i} className="flex justify-between">
                <span className="font-mono text-xs">{row.unallocated ? "Unallocated" : row.clientId}</span>
                <span className="font-semibold">{moneyExact(row.balance)}</span>
              </div>
            ))}
          </CardBody>
        </Card>
      )}
      {recon?.history && recon.history.length > 0 && (
        <p className="mb-3 text-xs text-slate-600">
          Last saved recon: {recon.history[0].status}
          {recon.history[0].certified ? " · certified" : " · not certified"} · difference{" "}
          {moneyExact(recon.history[0].difference)}
        </p>
      )}
      <TableWrap>
        <thead>
          <tr>
            <Th>When</Th>
            <Th>Type</Th>
            <Th>Amount</Th>
            <Th>Balance after</Th>
            <Th>Memo</Th>
          </tr>
        </thead>
        <tbody>
          {ledger.map((t) => (
            <tr key={t.id}>
              <Td>{t.createdAt}</Td>
              <Td>{t.type}</Td>
              <Td>{moneyExact(t.amount)}</Td>
              <Td>{moneyExact(t.balanceAfter)}</Td>
              <Td>{t.description}</Td>
            </tr>
          ))}
        </tbody>
      </TableWrap>
      <Dialog
        error={newError}
        open={newOpen}
        onClose={() => setNewOpen(false)}
        title="Open trust account"
        footer={
          <Button disabled={creating || !newAcct.accountName.trim() || !newAcct.bankName.trim()} onClick={createAccount}>
            {creating ? "Opening..." : "Open account"}
          </Button>
        }
      >
        <div className="space-y-3">
          <div>
            <Label htmlFor="trust-name">Account name</Label>
            <Input id="trust-name" value={newAcct.accountName} onChange={(e) => setNewAcct({ ...newAcct, accountName: e.target.value })} />
          </div>
          <div>
            <Label htmlFor="trust-bank">Bank</Label>
            <Input id="trust-bank" placeholder="FNB, Standard Bank, ABSA, Nedbank..." value={newAcct.bankName} onChange={(e) => setNewAcct({ ...newAcct, bankName: e.target.value })} />
          </div>
          <div>
            <Label htmlFor="trust-type">Account type</Label>
            <Select id="trust-type" value={newAcct.accountType} onChange={(e) => setNewAcct({ ...newAcct, accountType: e.target.value })}>
              <option value="trust">{za ? "Trust account (s 86(2))" : "Trust account"}</option>
              <option value="trust_investment">{za ? "Trust investment account (s 86(3) or (4))" : "Trust investment account"}</option>
            </Select>
          </div>
          <p className="text-xs text-slate-600">The balance starts at zero. Record deposits against a matter once money arrives.</p>
        </div>
      </Dialog>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Trust movement"
        footer={
          <Button
            disabled={posting}
            onClick={async () => {
              setPosting(true);
              setMoveError(null);
              try {
                await apiPost("/api/v1/trust/move", {
                  accountId: acct?.id,
                  type: move.type,
                  amount: move.amount,
                  description: move.description,
                  caseId: move.caseId || undefined,
                  clientId: moveClientId || undefined,
                });
                setOpen(false);
                await load();
              } catch (e) {
                setMoveError(e instanceof Error ? e.message : "Could not post the movement");
              } finally {
                setPosting(false);
              }
            }}
          >
            {posting ? "Posting…" : "Post"}
          </Button>
        }
      >
        <ErrorBanner error={moveError} />
        <Label>Type</Label>
        <Select aria-label="Type" value={move.type} onChange={(e) => setMove({ ...move, type: e.target.value })}>
          <option value="deposit">Deposit</option>
          <option value="withdrawal">Withdrawal</option>
        </Select>
        <Label className="mt-3">Matter</Label>
        <Select aria-label="Matter" value={move.caseId} onChange={(e) => setMove({ ...move, caseId: e.target.value })}>
          <option value="">{move.type === "deposit" ? "Unallocated (no client yet)" : "Choose the matter"}</option>
          {matters.map((m) => (
            <option key={m.id} value={m.id}>
              {m.caseNumber} {m.title}
              {m.clientName ? ` (${m.clientName})` : ""}
            </option>
          ))}
        </Select>
        <p className="mt-1 text-xs text-slate-600">
          {moveMatter
            ? `Posts to ${moveMatter.clientName || "this client"}'s ledger.`
            : move.type === "deposit"
              ? "Money with no matter sits in Unallocated until you move it to a client."
              : "Withdrawals come off one client's ledger, so pick the matter. To pay an invoice from trust, use Apply trust to fee."}
        </p>
        <Label className="mt-3">Amount</Label>
        <Input aria-label="Amount" type="number" value={move.amount} onChange={(e) => setMove({ ...move, amount: e.target.value })} />
        <Label className="mt-3">Memo</Label>
        <Input aria-label="Memo" value={move.description} onChange={(e) => setMove({ ...move, description: e.target.value })} />
      </Dialog>
      <Dialog
        error={dialogError}
        open={feeOpen}
        onClose={() => {
          setFeeOpen(false);
          setDialogError(null);
        }}
        title="Apply trust to a fee invoice"
        footer={
          <Button
            onClick={async () => {
              if (!acct || !fee.invoiceId) return;
              try {
                setDialogError(null);
                await apiPost(`/api/v1/invoices/${fee.invoiceId}/apply-trust`, {
                  accountId: acct.id,
                  amount: fee.amount,
                  note: fee.note || undefined,
                });
                setFeeOpen(false);
                await load();
              } catch (e) {
                setDialogError(e instanceof Error ? e.message : "Could not apply trust");
              }
            }}
          >
            Apply trust to fees
          </Button>
        }
      >
        <p className="mb-3 text-sm text-slate-600">
          Withdraws from this client&apos;s ledger on {acct?.accountName || "the selected account"} and records the matching fee payment. Only this client&apos;s ledger is used. The amount starts at the smaller of the invoice balance and this client&apos;s ledger.
        </p>
        <Label>Invoice</Label>
        <Select aria-label="Invoice"
          value={fee.invoiceId}
          onChange={(e) => {
            const invoiceId = e.target.value;
            const invoice = openInvoices.find((i) => i.id === invoiceId);
            const cap = invoice ? Math.min(Number(invoice.balanceDue), ledgerFor(invoice.clientId)) : 0;
            setFee({ ...fee, invoiceId, amount: cap > 0 ? cap.toFixed(2) : "0.00" });
          }}
        >
          <option value="">Select invoice</option>
          {openInvoices.map((i) => (
            <option key={i.id} value={i.id}>
              {i.invoiceNumber} · balance {moneyExact(i.balanceDue)}
            </option>
          ))}
        </Select>
        {chosenInvoice && (
          <p className="mt-2 text-xs text-slate-600">
            This client&apos;s ledger on the selected account is {moneyExact(ledgerFor(chosenInvoice.clientId))}. Invoice balance is {moneyExact(chosenInvoice.balanceDue)}.
          </p>
        )}
        <Label className="mt-3">Amount</Label>
        <Input aria-label="Amount" value={fee.amount} onChange={(e) => setFee({ ...fee, amount: e.target.value })} />
        <Label className="mt-3">Note</Label>
        <Input aria-label="Note" value={fee.note} onChange={(e) => setFee({ ...fee, note: e.target.value })} />
      </Dialog>
      <Dialog
        error={dialogError}
        open={bankOpen}
        onClose={() => {
          setBankOpen(false);
          setDialogError(null);
        }}
        title="Three-way recon"
        footer={
          <Button
            onClick={async () => {
              try {
                setDialogError(null);
                await apiPost("/api/v1/trust/reconcile", {
                  accountId: acct?.id,
                  bankBalance: bank.bankBalance || live?.bankBalance,
                  notes: bank.notes,
                  certify: false,
                });
                setBankOpen(false);
                load();
              } catch (e) {
                setDialogError(e instanceof Error ? e.message : "Could not save recon");
              }
            }}
          >
            Save recon
          </Button>
        }
      >
        <p className="mb-3 text-sm text-slate-600">
          Type the closing balance from this month&apos;s bank statement. Certification is refused while the three legs
          disagree unless you write a full explanation.
        </p>
        <Label>Bank statement balance</Label>
        <Input aria-label="Bank statement balance"
          type="number"
          value={bank.bankBalance}
          onChange={(e) => setBank({ ...bank, bankBalance: e.target.value })}
          placeholder={live ? String(live.bankBalance) : ""}
        />
        <Label className="mt-3">Explanation</Label>
        <Textarea aria-label="Explanation" value={bank.notes} onChange={(e) => setBank({ ...bank, notes: e.target.value })} />
      </Dialog>
      <Dialog
        error={dialogError}
        open={csvOpen}
        onClose={() => {
          setCsvOpen(false);
          setDialogError(null);
        }}
        title="Import bank statement CSV"
        wide
        footer={
          <Button
            onClick={async () => {
              try {
                setDialogError(null);
                await apiPost("/api/v1/trust/bank-import", { accountId: acct?.id, csv });
                setCsvOpen(false);
                load();
              } catch (e) {
                setDialogError(e instanceof Error ? e.message : "Could not import CSV");
              }
            }}
          >
            Import and recon
          </Button>
        }
      >
        <p className="mb-3 text-sm text-slate-600">
          FNB, Standard Bank, or ABSA-style CSV. The last Balance column becomes the bank leg of the three-way. This is
          not Open Banking.
        </p>
        <Label>Statement file</Label>
        <input aria-label="Statement file"
          type="file"
          accept=".csv,text/csv,text/plain"
          className="mb-3 block text-sm"
          onChange={async (e) => {
            setDialogError(null);
            try {
              const file = e.target.files?.[0];
              if (!file) return;
              setCsv(await file.text());
            } catch (err) {
              setDialogError(err instanceof Error ? err.message : "Something went wrong. Try again.");
            }
          }}
        />
        <Label>CSV</Label>
        <Textarea aria-label="CSV" className="min-h-[180px] font-mono text-xs" value={csv} onChange={(e) => setCsv(e.target.value)} />
      </Dialog>
    </div>
  );
}
