"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { Button, StatusBadge, TableWrap, Td, Th } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import type { Matter, OutboundMessage, OutboundReadiness } from "@/lib/types";
import { formatDateTime } from "@/lib/utils";

type Channel = "sms" | "whatsapp" | "email";

export function OutboundPanel({
  matterId: lockedMatterId,
  matterLabel,
  clientId,
  contactId,
  defaultPhone = "",
  defaultEmail = "",
  lockMatter = false,
  spread = false,
}: {
  matterId?: string;
  matterLabel?: string;
  clientId?: string;
  contactId?: string;
  defaultPhone?: string;
  defaultEmail?: string;
  lockMatter?: boolean;
  spread?: boolean;
}) {
  const [readiness, setReadiness] = useState<OutboundReadiness | null>(null);
  const [matters, setMatters] = useState<Matter[]>([]);
  const [rows, setRows] = useState<OutboundMessage[]>([]);
  const [matterId, setMatterId] = useState(lockedMatterId || "");
  const [smsPhone, setSmsPhone] = useState(defaultPhone);
  const [waPhone, setWaPhone] = useState(defaultPhone);
  const [email, setEmail] = useState(defaultEmail);
  const [smsBody, setSmsBody] = useState("");
  const [waBody, setWaBody] = useState("");
  const [templateSid, setTemplateSid] = useState("");
  const [templateVars, setTemplateVars] = useState("");
  const [subject, setSubject] = useState("");
  const [emailBody, setEmailBody] = useState("");
  const [busy, setBusy] = useState<Channel | null>(null);
  const [errors, setErrors] = useState<Partial<Record<Channel, string>>>({});
  const [notices, setNotices] = useState<Partial<Record<Channel, string>>>({});
  const [loadError, setLoadError] = useState<string | null>(null);

  const load = () => {
    const logPath = lockMatter && lockedMatterId ? `/api/v1/outbound?caseId=${lockedMatterId}` : "/api/v1/outbound";
    return Promise.all([
      apiGet<OutboundReadiness>("/api/v1/outbound/readiness"),
      apiGet<OutboundMessage[]>(logPath),
      lockMatter ? Promise.resolve([] as Matter[]) : apiGet<Matter[]>("/api/v1/cases"),
    ])
      .then(([ready, sent, matterRows]) => {
        setReadiness(ready);
        setRows(sent);
        setMatters(matterRows);
        setLoadError(null);
      })
      .catch((e) => setLoadError(e instanceof Error ? e.message : "Could not load messages"));
  };

  useEffect(() => {
    load();
  }, [lockedMatterId]);

  useEffect(() => {
    setSmsPhone(defaultPhone);
    setWaPhone(defaultPhone);
  }, [defaultPhone]);

  useEffect(() => {
    setEmail(defaultEmail);
  }, [defaultEmail]);

  const choices = matters.filter((m) => !clientId || !m.clientId || m.clientId === clientId);
  const notice = readiness?.attorneyNotice
    || "You remain responsible for what you send to a client. Check the number or address, the matter, and the wording before you send. This does not file anything at court.";

  function setChannelError(channel: Channel, message: string | null) {
    setErrors((cur) => ({ ...cur, [channel]: message || undefined }));
  }

  function setChannelNotice(channel: Channel, message: string | null) {
    setNotices((cur) => ({ ...cur, [channel]: message || undefined }));
  }

  async function send(channel: Channel) {
    setBusy(channel);
    setChannelError(channel, null);
    setChannelNotice(channel, null);
    const linkedMatter = lockMatter ? lockedMatterId : matterId;
    const common = {
      caseId: linkedMatter || undefined,
      clientId: clientId || undefined,
      contactId: contactId || undefined,
    };
    const payload =
      channel === "email"
        ? { ...common, to: email, subject, body: emailBody }
        : channel === "whatsapp"
          ? { ...common, to: waPhone, body: waBody, contentSid: templateSid || undefined, contentVariables: templateVars || undefined }
          : { ...common, to: smsPhone, body: smsBody };
    try {
      const result = await apiPost<OutboundMessage>(`/api/v1/outbound/${channel}`, payload);
      if (result.status === "dry_run") {
        setChannelNotice(channel, result.notice || result.errorMessage || "Logged on this firm. Not delivered.");
      } else {
        setChannelNotice(channel, result.notice || "Sent.");
        if (channel === "sms") setSmsBody("");
        if (channel === "whatsapp") setWaBody("");
        if (channel === "email") setEmailBody("");
      }
      await load();
    } catch (e) {
      setChannelError(channel, e instanceof Error ? e.message : "Could not send");
    } finally {
      setBusy(null);
    }
  }

  function beside(channel: Channel, ready: boolean, hint?: string) {
    if (errors[channel]) {
      return <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">{errors[channel]}</p>;
    }
    if (notices[channel]) {
      const dry = channel === "email" && !readiness?.emailReady;
      return (
        <p className={`rounded-lg border px-3 py-2 text-sm ${dry ? "border-amber-200 bg-amber-50 text-amber-950" : "border-emerald-200 bg-emerald-50 text-emerald-900"}`}>
          {notices[channel]}
        </p>
      );
    }
    if (!ready && hint) {
      return <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">{hint}</p>;
    }
    return null;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>SMS, WhatsApp, and email</CardTitle>
      </CardHeader>
      <CardBody className="space-y-6">
        <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">{notice}</p>
        {loadError && <p className="text-sm text-red-700">{loadError}</p>}
        {lockMatter ? (
          <p className="text-sm text-slate-600">Linked to {matterLabel || "this matter"}.</p>
        ) : (
          <div>
            <Label>Matter</Label>
            <Select
              value={matterId}
              onChange={(e) => {
                const id = e.target.value;
                setMatterId(id);
                const next = choices.find((m) => m.id === id);
                if (next?.clientPhone) {
                  setSmsPhone((cur) => cur || next.clientPhone || "");
                  setWaPhone((cur) => cur || next.clientPhone || "");
                }
                if (next?.clientEmail) setEmail((cur) => cur || next.clientEmail || "");
              }}
            >
              <option value="">Not on a matter</option>
              {choices.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.caseNumber} {m.title}
                </option>
              ))}
            </Select>
            <p className="mt-1 text-xs text-slate-500">Choose a matter to file the message on that matter.</p>
          </div>
        )}

        <div className={spread ? "grid gap-6 lg:grid-cols-3" : "grid gap-6"}>
          <section className="space-y-3">
            <h3 className="text-sm font-bold text-navy">SMS</h3>
            <p className="text-xs text-slate-500">{readiness?.smsHint || "Checking the SMS sender."}</p>
            <div>
              <Label>Mobile number</Label>
              <Input value={smsPhone} onChange={(e) => setSmsPhone(e.target.value)} placeholder="082 555 0144" />
            </div>
            <div>
              <Label>Message</Label>
              <Textarea value={smsBody} onChange={(e) => setSmsBody(e.target.value)} placeholder="Short update for the client" />
            </div>
            {beside("sms", !!readiness?.smsReady, readiness?.smsHint)}
            <Button disabled={busy !== null || !smsPhone || !smsBody.trim()} onClick={() => send("sms")}>
              {busy === "sms" ? "Sending SMS" : "Send SMS"}
            </Button>
          </section>

          <section className="space-y-3">
            <h3 className="text-sm font-bold text-navy">WhatsApp</h3>
            <p className="text-xs text-slate-500">{readiness?.whatsappHint || "Checking WhatsApp."}</p>
            <div>
              <Label>WhatsApp number</Label>
              <Input value={waPhone} onChange={(e) => setWaPhone(e.target.value)} placeholder="082 555 0144" />
            </div>
            <div>
              <Label>Message</Label>
              <Textarea value={waBody} onChange={(e) => setWaBody(e.target.value)} placeholder="Freeform text, or leave blank when you send a template" />
            </div>
            <details className="text-sm">
              <summary className="cursor-pointer text-xs font-semibold text-slate-600">Approved template</summary>
              <div className="mt-2 space-y-2">
                <div>
                  <Label>Content SID</Label>
                  <Input value={templateSid} onChange={(e) => setTemplateSid(e.target.value)} placeholder="HXxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" />
                </div>
                <div>
                  <Label>Variables JSON</Label>
                  <Input value={templateVars} onChange={(e) => setTemplateVars(e.target.value)} placeholder='{"1":"Nomsa"}' />
                </div>
              </div>
            </details>
            {beside("whatsapp", !!readiness?.whatsappReady, readiness?.whatsappHint)}
            <Button
              disabled={busy !== null || !waPhone || (!waBody.trim() && !templateSid.trim())}
              onClick={() => send("whatsapp")}
            >
              {busy === "whatsapp" ? "Sending WhatsApp" : "Send WhatsApp"}
            </Button>
          </section>

          <section className="space-y-3">
            <h3 className="text-sm font-bold text-navy">Email</h3>
            <p className="text-xs text-slate-500">{readiness?.emailHint || "Checking email."}</p>
            <div>
              <Label>Email address</Label>
              <Input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="nomsa@example.com" />
            </div>
            <div>
              <Label>Subject</Label>
              <Input value={subject} onChange={(e) => setSubject(e.target.value)} placeholder="Consultation" />
            </div>
            <div>
              <Label>Message</Label>
              <Textarea value={emailBody} onChange={(e) => setEmailBody(e.target.value)} placeholder="Write the note you want the client to read" />
            </div>
            {beside("email", !!readiness?.emailReady, readiness?.emailHint)}
            <Button disabled={busy !== null || !email || !subject.trim() || !emailBody.trim()} onClick={() => send("email")}>
              {busy === "email" ? "Sending email" : readiness?.emailReady ? "Send email" : "Log email"}
            </Button>
          </section>
        </div>

        <div>
          <h3 className="mb-2 text-sm font-bold text-navy">Recent sends</h3>
          {rows.length === 0 ? (
            <p className="text-sm text-slate-500">No SMS, WhatsApp, or email has been attempted yet.</p>
          ) : (
            <TableWrap>
              <thead>
                <tr>
                  <Th>When</Th>
                  <Th>Channel</Th>
                  <Th>To</Th>
                  <Th>Status</Th>
                  <Th>Matter</Th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.id}>
                    <Td>{formatDateTime(row.createdAt)}</Td>
                    <Td className="uppercase">{row.channel}</Td>
                    <Td className="font-mono text-xs">{row.to}</Td>
                    <Td>
                      <StatusBadge status={row.status} />
                      {row.errorMessage && row.status !== "sent" && row.status !== "delivered" && (
                        <p className="mt-1 max-w-xs text-xs text-slate-500">{row.errorMessage}</p>
                      )}
                    </Td>
                    <Td>{row.caseId ? "Linked" : "Not on a matter"}</Td>
                  </tr>
                ))}
              </tbody>
            </TableWrap>
          )}
        </div>
      </CardBody>
    </Card>
  );
}
