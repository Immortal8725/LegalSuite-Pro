"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { publicFetch, type Option } from "@/lib/directory";
import { Button } from "@/components/ui/button";
import { Input, Label, Textarea } from "@/components/ui/input";

type Lang = "en" | "af" | "zu";
type TriageResult = {
  areas: Option[];
  allAreas: Option[];
  explanation: string;
  timeLimits: string[];
  source: "gemini" | "keywords";
  place: { city?: string; province?: string; name: string } | null;
  redacted: boolean;
};

export const TRIAGE_STORAGE_KEY = "owlaw_triage";
const MAX = 1500;

const T: Record<Lang, Record<string, string>> = {
  en: {
    title: "Tell us what happened",
    lead: "Describe your problem in your own words. We suggest the kind of lawyer to look for and any urgent time limits.",
    warn: "Do not enter ID numbers or other people's names. Your words are not stored unless you send an enquiry to a firm.",
    label: "What happened?",
    placeholder: "For example: I was dismissed last week without a hearing after six years at the same shop.",
    town: "Your town or city (optional)",
    submit: "Suggest the right lawyer",
    busy: "Thinking…",
    areas: "Suggested area of law. Tap to change:",
    more: "Other areas",
    limits: "Time limits that may apply",
    find: "Find lawyers",
    near: "near",
    notice: "This is general information, not legal advice. It does not judge your case. Only a lawyer who knows all the facts can advise you.",
    fallback: "Our AI helper is busy, so this suggestion comes from keywords.",
    redacted: "We removed what looked like ID numbers, phone numbers or email addresses before checking.",
    chars: "characters left",
  },
  af: {
    title: "Vertel ons wat gebeur het",
    lead: "Beskryf jou probleem in jou eie woorde. Ons stel voor watter soort prokureur jy moet soek en enige dringende tydsperke.",
    warn: "Moenie ID-nommers of ander mense se name invoer nie. Jou woorde word nie gestoor nie, tensy jy 'n navraag aan 'n firma stuur.",
    label: "Wat het gebeur?",
    placeholder: "Byvoorbeeld: Ek is verlede week sonder 'n verhoor afgedank ná ses jaar by dieselfde winkel.",
    town: "Jou dorp of stad (opsioneel)",
    submit: "Stel die regte prokureur voor",
    busy: "Besig…",
    areas: "Voorgestelde regsgebied. Tik om te verander:",
    more: "Ander gebiede",
    limits: "Tydsperke wat moontlik geld",
    find: "Vind prokureurs",
    near: "naby",
    notice: "Dit is algemene inligting, nie regsadvies nie. Dit oordeel nie oor jou saak nie. Net 'n prokureur wat al die feite ken, kan jou adviseer.",
    fallback: "Ons KI-helper is besig, so hierdie voorstel kom van sleutelwoorde.",
    redacted: "Ons het iets wat soos ID-nommers, telefoonnommers of e-posadresse lyk, verwyder voordat ons gekyk het.",
    chars: "karakters oor",
  },
  zu: {
    title: "Sitshele ukuthi kwenzekeni",
    lead: "Chaza inkinga yakho ngamazwi akho. Sizokuphakamisela uhlobo lommeli okufanele umfune kanye nezikhathi ezibalulekile.",
    warn: "Ungafaki izinombolo zikamazisi noma amagama abanye abantu. Amazwi akho awagcinwa ngaphandle uma uthumela umbuzo enkampanini.",
    label: "Kwenzekeni?",
    placeholder: "Isibonelo: Ngixoshwe emsebenzini ngesonto eledlule ngaphandle kokulalelwa ngemuva kweminyaka eyisithupha.",
    town: "Idolobha lakho (akuphoqelekile)",
    submit: "Phakamisa ummeli ofanele",
    busy: "Kuyacatshangwa…",
    areas: "Umkhakha womthetho ophakanyisiwe. Thepha ukuze ushintshe:",
    more: "Eminye imikhakha",
    limits: "Izikhathi ezingasebenza",
    find: "Thola abameli",
    near: "eduze ne",
    notice: "Lolu ulwazi olujwayelekile, akuseluleko somthetho. Alahluleli icala lakho. Ummeli owazi wonke amaqiniso nguye kuphela ongakweluleka.",
    fallback: "Umsizi wethu we-AI umatasa, ngakho lesi siphakamiso sivela emagameni abalulekile.",
    redacted: "Sisuse okubukeka njengezinombolo zikamazisi, izinombolo zocingo noma ama-imeyili ngaphambi kokuhlola.",
    chars: "izinhlamvu ezisele",
  },
};

export function HomeTriage() {
  const router = useRouter();
  const [lang, setLang] = useState<Lang>("en");
  const [text, setText] = useState("");
  const [town, setTown] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<TriageResult | null>(null);
  const [area, setArea] = useState("");
  const [showAll, setShowAll] = useState(false);
  const t = T[lang];

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const r = await publicFetch<TriageResult>("/api/v1/public/triage", {
        method: "POST",
        body: JSON.stringify({ text, town, lang }),
      });
      setResult(r);
      setArea(r.areas[0]?.slug || "");
      setShowAll(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  function find() {
    // Kept only in this browser tab, so the firm enquiry form can be pre-filled. Nothing is stored on the server.
    try {
      sessionStorage.setItem(TRIAGE_STORAGE_KEY, JSON.stringify({ text, area }));
    } catch {
      /* private mode: the enquiry form simply starts empty */
    }
    const q = new URLSearchParams();
    if (area) q.set("area", area);
    if (result?.place?.province) q.set("province", result.place.province);
    if (result?.place?.city) q.set("city", result.place.city);
    router.push(`/find-a-lawyer?${q.toString()}`);
  }

  const chips = result ? (showAll ? result.allAreas : result.areas) : [];

  return (
    <div className="mx-auto mt-8 max-w-2xl rounded-2xl border-2 border-gold bg-white p-5 text-left shadow-lift sm:p-6" data-testid="triage">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h2 className="text-2xl font-extrabold text-navy">{t.title}</h2>
        <div className="flex gap-1 text-xs font-bold" role="group" aria-label="Language">
          {(["en", "af", "zu"] as Lang[]).map((l) => (
            <button
              key={l}
              type="button"
              onClick={() => setLang(l)}
              aria-pressed={lang === l}
              className={`rounded-full px-3 py-1 ${lang === l ? "bg-navy text-white" : "bg-slate-100 text-slate-700"}`}
            >
              {l === "en" ? "English" : l === "af" ? "Afrikaans" : "isiZulu"}
            </button>
          ))}
        </div>
      </div>
      <p className="mt-2 text-sm text-slate-600">{t.lead}</p>
      <form onSubmit={submit} className="mt-4 space-y-3">
        <div>
          <Label htmlFor="triage-text">{t.label}</Label>
          <Textarea
            id="triage-text"
            rows={4}
            maxLength={MAX}
            required
            minLength={15}
            value={text}
            placeholder={t.placeholder}
            onChange={(e) => setText(e.target.value)}
          />
          <div className="mt-1 flex flex-wrap justify-between gap-2 text-xs text-slate-500">
            <span className="font-semibold text-amber-800">{t.warn}</span>
            <span>
              {MAX - text.length} {t.chars}
            </span>
          </div>
        </div>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <Label htmlFor="triage-town">{t.town}</Label>
            <Input id="triage-town" value={town} maxLength={60} onChange={(e) => setTown(e.target.value)} />
          </div>
          <Button type="submit" disabled={busy || text.trim().length < 15} className="w-full sm:w-auto">
            {busy ? t.busy : t.submit}
          </Button>
        </div>
      </form>
      {error ? <p className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p> : null}
      {result ? (
        <div className="mt-5 space-y-4 border-t pt-4" data-testid="triage-result">
          <div>
            <p className="text-sm font-semibold text-navy">{t.areas}</p>
            <div className="mt-2 flex flex-wrap gap-2">
              {chips.map((o) => (
                <button
                  key={o.slug}
                  type="button"
                  onClick={() => setArea(o.slug)}
                  aria-pressed={area === o.slug}
                  data-testid={`area-${o.slug}`}
                  className={`rounded-full border px-3 py-1.5 text-sm font-semibold ${area === o.slug ? "border-navy bg-navy text-white" : "border-slate-300 bg-white text-navy"}`}
                >
                  {o.label}
                </button>
              ))}
              {!showAll ? (
                <button type="button" onClick={() => setShowAll(true)} className="rounded-full px-3 py-1.5 text-sm font-semibold text-slate-600 underline">
                  {t.more}
                </button>
              ) : null}
            </div>
          </div>
          <p className="text-sm text-slate-700" data-testid="triage-explanation">{result.explanation}</p>
          {result.timeLimits.length ? (
            <div className="rounded-xl bg-amber-50 p-3">
              <p className="text-sm font-bold text-amber-900">{t.limits}</p>
              <ul className="mt-1 list-disc pl-5 text-sm text-amber-900">
                {result.timeLimits.map((l) => (
                  <li key={l}>{l}</li>
                ))}
              </ul>
            </div>
          ) : null}
          {result.source === "keywords" ? <p className="text-xs text-slate-500">{t.fallback}</p> : null}
          {result.redacted ? <p className="text-xs text-slate-500">{t.redacted}</p> : null}
          <Button type="button" onClick={find} className="w-full sm:w-auto" data-testid="triage-find">
            {t.find}
            {result.place ? ` ${t.near} ${result.place.name}` : ""}
          </Button>
        </div>
      ) : null}
      <p className="mt-4 text-xs text-slate-500">{t.notice}</p>
    </div>
  );
}
