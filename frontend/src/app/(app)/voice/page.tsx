"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { useCalls } from "@/components/call-provider";
import { Button, PageHeader, StatusBadge, TableWrap, Td, Th } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select } from "@/components/ui/input";
import type { AvailableNumber, CallRow, FirmNumber, Matter, PstnReadiness, TeamMember } from "@/lib/types";
import { formatDateTime, moneyExact } from "@/lib/utils";

type Ethics = { state: string; country?: string; allPartyConsent: boolean; notice: string; statute?: string; pstnNotice?: string };

type Registry = {
  totalCalls: number;
  webrtcCalls: number;
  pstnCalls: number;
  totalMinutes: number;
  totalCost: number;
  includedMinutes: number;
  note: string;
};

function kindLabel(kind?: string) {
  if (kind === "did") return "Local number";
  if (kind === "verified_landline") return "Verified personal number";
  if (kind === "voice_from") return "Server caller ID";
  if (kind === "account_incoming") return "Twilio account number";
  if (kind === "account_outgoing") return "Verified caller ID on the account";
  return kind || "";
}

export default function VoicePage() {
  const { user } = useAuth();
  const { startCall } = useCalls();
  const [rows, setRows] = useState<CallRow[]>([]);
  const [usage, setUsage] = useState<Registry | null>(null);
  const [team, setTeam] = useState<TeamMember[]>([]);
  const [matters, setMatters] = useState<Matter[]>([]);
  const [readiness, setReadiness] = useState<PstnReadiness | null>(null);
  const [peerId, setPeerId] = useState("");
  const [record, setRecord] = useState(false);
  const [ethics, setEthics] = useState<Ethics | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [country, setCountry] = useState("ZA");
  const [areaCode, setAreaCode] = useState("");
  const [locality, setLocality] = useState("");
  const [available, setAvailable] = useState<AvailableNumber[]>([]);
  const [searching, setSearching] = useState(false);

  const [landline, setLandline] = useState("");
  const [landlineName, setLandlineName] = useState("Personal phone");

  const [destination, setDestination] = useState("");
  const [callback, setCallback] = useState("");
  const [callerId, setCallerId] = useState("");
  const [matterId, setMatterId] = useState("");
  const [nonMatter, setNonMatter] = useState(false);
  const [pstnRecord, setPstnRecord] = useState(false);
  const [placing, setPlacing] = useState(false);

  const load = () =>
    Promise.all([
      apiGet<CallRow[]>("/api/v1/calls"),
      apiGet<Registry>("/api/v1/calls/registry"),
      apiGet<TeamMember[]>("/api/v1/users"),
      apiGet<Ethics>("/api/v1/voice/ethics"),
      apiGet<Matter[]>("/api/v1/cases"),
      apiGet<PstnReadiness>("/api/v1/voice/pstn/readiness"),
    ])
      .then(([c, u, t, e, m, r]) => {
        setRows(c);
        setUsage(u);
        setTeam(t.filter((x) => x.id !== user?.id));
        setEthics(e);
        setMatters(m);
        setReadiness(r);
        setCountry((prev) => prev || (e.country === "ZA" ? "ZA" : "US"));
      })
      .catch((e) => setError(e.message));

  useEffect(() => {
    load();
  }, []);

  useEffect(() => {
    if (user?.phone && !callback) setCallback(user.phone);
  }, [user, callback]);

  const peer = team.find((t) => t.id === peerId);
  const activeIds = (readiness?.callerIds || []).filter((n) => n.status === "active");
  const pending = (readiness?.callerIds || []).filter((n) => n.status === "pending");

  async function run<T>(work: () => Promise<T>, ok?: string) {
    setError(null);
    setNotice(null);
    try {
      const result = await work();
      if (ok) setNotice(ok);
      await load();
      return result;
    } catch (e) {
      setError(e instanceof Error ? e.message : "Request failed");
      return null;
    }
  }

  return (
    <div>
      <PageHeader
        title="Voice registry"
        subtitle="In-app calls are free. Public network calls use a verified personal number or the caller ID configured on the server. Buying a number is optional. Recording stays opt-in."
      />
      {ethics && (
        <p className={`mb-4 rounded-lg border px-3 py-2 text-xs ${ethics.allPartyConsent ? "border-red-200 bg-red-50 text-red-900" : "border-amber-200 bg-amber-50 text-amber-900"}`}>
          {ethics.state}: {ethics.notice}
        </p>
      )}
      <ErrorBanner error={error} />
      {notice && <p className="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900">{notice}</p>}

      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {[
          ["Calls logged", usage?.totalCalls ?? 0],
          ["WebRTC (free)", usage?.webrtcCalls ?? 0],
          ["Public network", usage?.pstnCalls ?? 0],
          ["Telecom cost", moneyExact(usage?.totalCost ?? 0)],
        ].map(([k, v]) => (
          <Card key={String(k)}>
            <CardHeader>
              <CardTitle>{k}</CardTitle>
            </CardHeader>
            <CardBody className="text-2xl font-semibold text-navy">{String(v)}</CardBody>
          </Card>
        ))}
      </div>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle>Call a teammate</CardTitle>
        </CardHeader>
        <CardBody className="flex flex-wrap items-center gap-2">
          <select className="h-10 rounded-lg border px-3 text-sm" value={peerId} onChange={(e) => setPeerId(e.target.value)}>
            <option value="">Choose a teammate</option>
            {team.map((u) => (
              <option key={u.id} value={u.id}>
                {u.fullName}
              </option>
            ))}
          </select>
          <label className="flex items-center gap-2 text-xs text-slate-600">
            <input type="checkbox" checked={record} onChange={(e) => setRecord(e.target.checked)} />
            Record (opt-in)
          </label>
          <Button
            disabled={!peer}
            onClick={async () => {
              if (!peer) return;
              if (record && ethics?.notice && !window.confirm(ethics.notice + "\n\nContinue with recording?")) return;
              await startCall(peer, { record });
              load();
            }}
          >
            Call in app
          </Button>
          <p className="w-full text-xs text-slate-500">In-app WebRTC stays inside the browser and does not show a public caller ID.</p>
        </CardBody>
      </Card>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle>Public network</CardTitle>
        </CardHeader>
        <CardBody className="space-y-6">
          <p className="text-sm text-slate-600">{readiness?.message || ethics?.pstnNotice}</p>
          {ethics?.pstnNotice && <p className="text-xs text-slate-500">{ethics.pstnNotice}</p>}

          <div>
            <h3 className="mb-2 text-sm font-bold text-navy">Caller IDs</h3>
            {readiness?.automaticCallerId && (
              <p className="mb-2 text-sm text-slate-600">
                Automatic caller ID: <span className="font-mono">{readiness.automaticCallerId}</span> ({kindLabel(readiness.automaticSource || undefined)}). Buying a number is optional.
              </p>
            )}
            {(readiness?.callerIds || []).length === 0 ? (
              <p className="text-sm text-slate-500">
                No number is saved on this firm yet. Verify a personal mobile or landline, or set TWILIO_VOICE_FROM. Buying a local number is optional.
              </p>
            ) : (
              <TableWrap>
                <thead>
                  <tr>
                    <Th>Number</Th>
                    <Th>Type</Th>
                    <Th>Status</Th>
                    <Th>Place</Th>
                    <Th>Actions</Th>
                  </tr>
                </thead>
                <tbody>
                  {(readiness?.callerIds || []).map((n) => (
                    <tr key={n.id}>
                      <Td>
                        <span className="font-mono">{n.e164}</span>
                        {n.defaultOutbound && <span className="ml-2 text-[11px] font-bold uppercase text-gold">Default</span>}
                        {n.friendlyName && <div className="text-xs text-slate-500">{n.friendlyName}</div>}
                        {n.validationCode && (
                          <div className="mt-1 text-xs text-amber-800">
                            Enter {n.validationCode} when Twilio calls this phone.
                          </div>
                        )}
                      </Td>
                      <Td>{kindLabel(n.kind)}</Td>
                      <Td>
                        <StatusBadge status={n.status} />
                      </Td>
                      <Td>{[n.locality, n.region].filter(Boolean).join(", ")}</Td>
                      <Td>
                        <div className="flex flex-wrap gap-2">
                          {n.status === "pending" && (
                            <Button size="sm" variant="outline" onClick={() => run(() => apiPost(`/api/v1/voice/numbers/${n.id}/refresh`, {}), "Checked with Twilio.")}>
                              Check verification
                            </Button>
                          )}
                          {n.status === "active" && !n.defaultOutbound && (
                            <Button size="sm" variant="outline" onClick={() => run(() => apiPost(`/api/v1/voice/numbers/${n.id}/default`, {}), "Default caller ID updated.")}>
                              Make default
                            </Button>
                          )}
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => {
                              if (!window.confirm("Release this caller ID? A rented number goes back to Twilio. A verified personal number is removed as caller ID.")) return;
                              run(() => apiPost(`/api/v1/voice/numbers/${n.id}/release`, {}), "Caller ID released.");
                            }}
                          >
                            Release
                          </Button>
                        </div>
                      </Td>
                    </tr>
                  ))}
                </tbody>
              </TableWrap>
            )}
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <div>
              <h3 className="mb-2 text-sm font-bold text-navy">Verify a personal number</h3>
              <p className="mb-3 text-xs text-slate-500">
                Use a mobile or a landline you can answer. Twilio calls that phone and asks for a code. After you enter it, that number is the caller ID. You do not need to buy a number.
              </p>
              <div className="grid gap-3">
                <div>
                  <Label>Mobile or landline</Label>
                  <Input value={landline} onChange={(e) => setLandline(e.target.value)} placeholder="082 555 0100" />
                </div>
                <div>
                  <Label>Label</Label>
                  <Input value={landlineName} onChange={(e) => setLandlineName(e.target.value)} />
                </div>
              </div>
              <Button
                className="mt-3"
                variant="outline"
                onClick={() =>
                  run(
                    () => apiPost<FirmNumber>("/api/v1/voice/numbers/verify", { phoneNumber: landline, friendlyName: landlineName }),
                    "Twilio is calling that phone. Enter the code shown on the row above."
                  )
                }
              >
                Start verification
              </Button>
              {pending.length > 0 && (
                <p className="mt-2 text-xs text-amber-800">A verification call is still waiting. Use Check verification after you enter the code.</p>
              )}
            </div>
            <div>
              <h3 className="mb-2 text-sm font-bold text-navy">Rent a local number (optional)</h3>
              <p className="mb-3 text-xs text-slate-500">Optional. Search Twilio if you want a separate local number. Dialing does not require a purchase.</p>
              <div className="grid gap-3 sm:grid-cols-3">
                <div>
                  <Label>Country</Label>
                  <Select value={country} onChange={(e) => setCountry(e.target.value)}>
                    <option value="ZA">ZA</option>
                    <option value="US">US</option>
                    <option value="GB">GB</option>
                    <option value="CA">CA</option>
                    <option value="AU">AU</option>
                  </Select>
                </div>
                <div>
                  <Label>Area code</Label>
                  <Input value={areaCode} onChange={(e) => setAreaCode(e.target.value)} placeholder="011 or 415" />
                </div>
                <div>
                  <Label>City</Label>
                  <Input value={locality} onChange={(e) => setLocality(e.target.value)} placeholder="Sandton" />
                </div>
              </div>
              <Button
                className="mt-3"
                variant="outline"
                disabled={searching}
                onClick={async () => {
                  setSearching(true);
                  const found = await run(() =>
                    apiPost<AvailableNumber[]>("/api/v1/voice/numbers/search", { country, areaCode, locality })
                  );
                  if (found) setAvailable(found);
                  setSearching(false);
                }}
              >
                {searching ? "Searching" : "Search numbers"}
              </Button>
              {available.length > 0 && (
                <ul className="mt-3 divide-y rounded-lg border">
                  {available.map((n) => (
                    <li key={n.phoneNumber} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                      <span>
                        <span className="font-mono">{n.phoneNumber}</span>
                        <span className="ml-2 text-slate-500">{[n.locality, n.region].filter(Boolean).join(", ")}</span>
                      </span>
                      <Button
                        size="sm"
                        onClick={() =>
                          run(
                            () =>
                              apiPost("/api/v1/voice/numbers/buy", {
                                phoneNumber: n.phoneNumber,
                                locality: n.locality,
                                region: n.region,
                                country: n.country || country,
                                friendlyName: n.friendlyName,
                              }),
                            "Local number rented. It can be used as caller ID."
                          ).then(() => setAvailable((list) => list.filter((row) => row.phoneNumber !== n.phoneNumber)))
                        }
                      >
                        Rent
                      </Button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          <div>
            <h3 className="mb-2 text-sm font-bold text-navy">Dial out</h3>
            <p className="mb-3 text-xs text-slate-500">
              Your mobile rings first. When you answer, we connect the other party and they see the automatic caller ID, or the one you pick. This is not a browser softphone. Emergency numbers stay on the handset dialer. Buying a number is optional.
            </p>
            <div className="grid gap-3 md:grid-cols-2">
              <div>
                <Label>Number to call</Label>
                <Input value={destination} onChange={(e) => setDestination(e.target.value)} placeholder="082 555 0199" />
              </div>
              <div>
                <Label>Your phone (rings first)</Label>
                <Input value={callback} onChange={(e) => setCallback(e.target.value)} placeholder="083 555 0100" />
              </div>
              <div>
                <Label>Caller ID</Label>
                <Select value={callerId} onChange={(e) => setCallerId(e.target.value)}>
                  <option value="">Automatic caller ID</option>
                  {activeIds.map((n) => (
                    <option key={n.id} value={n.id}>
                      {n.e164} ({kindLabel(n.kind)})
                    </option>
                  ))}
                </Select>
              </div>
              <div>
                <Label>Matter</Label>
                <Select
                  value={matterId}
                  disabled={nonMatter}
                  onChange={(e) => {
                    setMatterId(e.target.value);
                    if (e.target.value) setNonMatter(false);
                  }}
                >
                  <option value="">Choose a matter</option>
                  {matters.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.caseNumber} {m.title}
                    </option>
                  ))}
                </Select>
              </div>
            </div>
            {(error || notice) && (
              <div className="mt-3 space-y-2">
                {error && <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>}
                {notice && <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900">{notice}</p>}
              </div>
            )}
            <div className="mt-3 flex flex-wrap items-center gap-4">
              <label className="flex items-center gap-2 text-sm text-slate-700">
                <input
                  type="checkbox"
                  checked={nonMatter}
                  onChange={(e) => {
                    setNonMatter(e.target.checked);
                    if (e.target.checked) setMatterId("");
                  }}
                />
                Not on a matter
              </label>
              <label className="flex items-center gap-2 text-sm text-slate-700">
                <input type="checkbox" checked={pstnRecord} onChange={(e) => setPstnRecord(e.target.checked)} />
                Record (opt-in)
              </label>
              <Button
                disabled={placing || !destination || !callback || (!matterId && !nonMatter)}
                onClick={async () => {
                  if (pstnRecord && ethics?.notice && !window.confirm(ethics.notice + "\n\nContinue with recording?")) return;
                  setPlacing(true);
                  const placed = await run(
                    () =>
                      apiPost<CallRow>("/api/v1/calls/pstn", {
                        to: destination,
                        staffCallback: callback,
                        firmPhoneNumberId: callerId || undefined,
                        caseId: nonMatter ? undefined : matterId,
                        nonMatter,
                        recordingEnabled: pstnRecord,
                        recordingConsentGiven: pstnRecord,
                      }),
                    "Calling your phone. Answer it to be connected. The other party will see the firm caller ID."
                  );
                  if (placed) setDestination("");
                  setPlacing(false);
                }}
              >
                {placing ? "Placing call" : "Place call"}
              </Button>
            </div>
          </div>
        </CardBody>
      </Card>

      <TableWrap>
        <thead>
          <tr>
            <Th>When</Th>
            <Th>Type</Th>
            <Th>From</Th>
            <Th>To</Th>
            <Th>Status</Th>
            <Th>Duration</Th>
            <Th>Cost</Th>
            <Th>Recording</Th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id}>
              <Td>{formatDateTime(r.startedAt)}</Td>
              <Td>{r.callType}</Td>
              <Td className="font-mono text-xs">{r.fromNumber || ""}</Td>
              <Td className="font-mono text-xs">{r.toNumber || ""}</Td>
              <Td>
                <StatusBadge status={r.status} />
              </Td>
              <Td>{Math.round((r.durationSeconds ?? 0) / 60)} min</Td>
              <Td>{moneyExact(r.totalCost)}</Td>
              <Td>{r.recordingEnabled ? "Opt-in" : "Off"}</Td>
            </tr>
          ))}
        </tbody>
      </TableWrap>
      <p className="mt-3 text-xs text-slate-500">{usage?.note}</p>
    </div>
  );
}
