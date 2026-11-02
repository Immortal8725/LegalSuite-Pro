import type { ClientSummary } from "@/lib/inbox";

/** Plain-language stage text shared by the staff matter page and the client portal. */
export function ClientSummaryBody({ s }: { s: ClientSummary }) {
  if (!s.stage) {
    return <p className="text-sm text-slate-600">No court stage has been recorded on this matter yet.</p>;
  }
  return (
    <div className="space-y-3 text-sm">
      <p className="font-semibold text-slate-900">
        {s.stageLabel}
        {s.trackLabel && <span className="font-normal text-slate-500"> ({s.trackLabel})</span>}
      </p>
      {s.means && <Row label={s.labels.means} text={s.means} />}
      {s.next && <Row label={s.labels.next} text={s.next} />}
      {s.needFromYou && <Row label={s.labels.needFromYou} text={s.needFromYou} highlight />}
      {s.nextDate && <Row label={s.labels.nextDate} text={s.nextDate.text} />}
      {s.firmNote && (
        <div className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2">
          <div className="text-xs font-semibold uppercase text-slate-500">Note from your attorney</div>
          <p className="mt-1 whitespace-pre-line text-slate-800">{s.firmNote}</p>
        </div>
      )}
      <p className="text-xs text-slate-500">{s.disclaimer}</p>
    </div>
  );
}

function Row({ label, text, highlight }: { label: string; text: string; highlight?: boolean }) {
  return (
    <div className={highlight ? "rounded-md border border-amber-200 bg-amber-50 px-3 py-2" : ""}>
      <div className="text-xs font-semibold uppercase text-slate-500">{label}</div>
      <p className="mt-0.5 text-slate-800">{text}</p>
    </div>
  );
}
