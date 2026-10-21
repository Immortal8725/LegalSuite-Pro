"use client";

import { useCallback, useEffect, useState } from "react";

/**
 * UI strings for public pages (firm sites, enquiry form, pay page) in English, Afrikaans and isiZulu.
 * Firm-written content (practice descriptions, bios) stays in the language the firm wrote it in.
 * Translations were prepared for Owlaw and should be checked by a first-language speaker.
 */
export type Lang = "en" | "af" | "zu";
export const LANGS: { code: Lang; label: string }[] = [
  { code: "en", label: "English" },
  { code: "af", label: "Afrikaans" },
  { code: "zu", label: "isiZulu" },
];

const en = {
  skip: "Skip to main content",
  expertise: "Expertise",
  people: "People",
  insights: "Insights",
  whoWeHelp: "Who we help",
  fees: "Fees",
  recognition: "Recognition",
  contact: "Contact",
  enquire: "Make an enquiry",
  menu: "Menu",
  visit: "Visit",
  subscribe: "Subscribe",
  legal: "Legal",
  privacy: "Privacy notice",
  cookies: "Cookie policy",
  paia: "PAIA manual",
  terms: "Terms",
  accessibility: "Accessibility",
  fraud: "Fraud warning",
  portal: "Client portal",
  notMandate: "General information, not a mandate to act.",
  cookieText: "This site can store your cookie choice on this browser. It does not load advertising or analytics scripts.",
  necessaryOnly: "Necessary only",
  accept: "Accept",
  language: "Language",
  formIntro: "Please do not send confidential details until the firm confirms it can act. Sending this form does not create a mandate.",
  name: "Name",
  email: "Email",
  phone: "Phone",
  matterType: "Matter type",
  otherSide: "Who is on the other side?",
  when: "When did it happen?",
  organOfState: "The other side is an organ of state (municipality, SAPS, department, metro)",
  whatHappened: "What happened?",
  send: "Send enquiry",
  couldNotSend: "Could not send.",
  payTitle: "Pay your invoice",
  invoice: "Invoice",
  testMode: "Test mode. No real money will move.",
  payButton: "Pay securely with PayFast",
  payNote: "Card, instant EFT and other PayFast methods. Owlaw never sees your card details.",
  paid: "Paid. Thank you.",
  confirming: "Thank you. PayFast is confirming your payment; this can take a minute.",
  cancelledTry: "Payment cancelled. You can try again below.",
  expired: "This link has expired. Ask your attorney for a new one.",
  replaced: "This link was replaced by a newer one. Ask your attorney for the latest link.",
  siteKicker: "Public site",
  siteInReview: "This firm's public site is waiting for Owlaw approval. Please check back soon.",
  siteUpdating: "This firm's public site is being updated. Please check back soon.",
  siteNotLive: "This public site is not live yet.",
  siteWorkHere: "Work at this firm?",
  siteSetUp: "Set up and submit your site",
  siteInOwlaw: "in Owlaw.",
  closed: "This invoice is no longer payable online. Please contact the firm if you have questions.",
};
export type Strings = typeof en;

const af: Strings = {
  skip: "Gaan na hoofinhoud",
  expertise: "Kundigheid",
  people: "Mense",
  insights: "Insigte",
  whoWeHelp: "Wie ons help",
  fees: "Fooie",
  recognition: "Erkenning",
  contact: "Kontak",
  enquire: "Rig 'n navraag",
  menu: "Kieslys",
  visit: "Besoek",
  subscribe: "Teken in",
  legal: "Regsinligting",
  privacy: "Privaatheidskennisgewing",
  cookies: "Koekiebeleid",
  paia: "PAIA-handleiding",
  terms: "Bepalings",
  accessibility: "Toeganklikheid",
  fraud: "Bedrogwaarskuwing",
  portal: "Kliëntportaal",
  notMandate: "Algemene inligting, nie 'n mandaat om op te tree nie.",
  cookieText: "Hierdie webwerf kan u koekiekeuse op hierdie blaaier stoor. Dit laai geen advertensie- of ontledingskripte nie.",
  necessaryOnly: "Slegs noodsaaklik",
  accept: "Aanvaar",
  language: "Taal",
  formIntro: "Moet asseblief nie vertroulike besonderhede stuur voordat die firma bevestig dat hy kan optree nie. Om hierdie vorm te stuur skep nie 'n mandaat nie.",
  name: "Naam",
  email: "E-pos",
  phone: "Telefoon",
  matterType: "Soort saak",
  otherSide: "Wie is aan die ander kant?",
  when: "Wanneer het dit gebeur?",
  organOfState: "Die ander party is 'n staatsorgaan (munisipaliteit, SAPD, departement, metro)",
  whatHappened: "Wat het gebeur?",
  send: "Stuur navraag",
  couldNotSend: "Kon nie stuur nie.",
  payTitle: "Betaal u faktuur",
  invoice: "Faktuur",
  testMode: "Toetsmodus. Geen regte geld sal oorgaan nie.",
  payButton: "Betaal veilig met PayFast",
  payNote: "Kaart, kitsoorbetaling en ander PayFast-metodes. Owlaw sien nooit u kaartbesonderhede nie.",
  paid: "Betaal. Dankie.",
  confirming: "Dankie. PayFast bevestig u betaling; dit kan 'n minuut neem.",
  cancelledTry: "Betaling gekanselleer. U kan hieronder weer probeer.",
  expired: "Hierdie skakel het verval. Vra u prokureur vir 'n nuwe een.",
  replaced: "Hierdie skakel is deur 'n nuwer een vervang. Vra u prokureur vir die nuutste skakel.",
  siteKicker: "Openbare webwerf",
  siteInReview: "Hierdie firma se openbare webwerf wag vir goedkeuring deur Owlaw. Kom asseblief later weer terug.",
  siteUpdating: "Hierdie firma se openbare webwerf word tans bygewerk. Kom asseblief later weer terug.",
  siteNotLive: "Hierdie openbare webwerf is nog nie aanlyn nie.",
  siteWorkHere: "Werk u by hierdie firma?",
  siteSetUp: "Stel u webwerf op en dien dit in",
  siteInOwlaw: "in Owlaw.",
  closed: "Hierdie faktuur kan nie meer aanlyn betaal word nie. Kontak asseblief die firma as u vrae het.",
};

const zu: Strings = {
  skip: "Yeqela kokuqukethwe okuyinhloko",
  expertise: "Ubuchwepheshe",
  people: "Abantu",
  insights: "Ulwazi",
  whoWeHelp: "Esibasizayo",
  fees: "Izimali",
  recognition: "Ukuqashelwa",
  contact: "Xhumana nathi",
  enquire: "Buza",
  menu: "Imenyu",
  visit: "Vakashela",
  subscribe: "Bhalisa",
  legal: "Ezomthetho",
  privacy: "Isaziso sobumfihlo",
  cookies: "Inqubomgomo yamakhukhi",
  paia: "Incwadi ye-PAIA",
  terms: "Imigomo",
  accessibility: "Ukufinyeleleka",
  fraud: "Isexwayiso sokukhwabanisa",
  portal: "Ingosi yeklayenti",
  notMandate: "Ulwazi olujwayelekile, akusona isigunyaziso sokusebenza.",
  cookieText: "Le sayithi ingagcina ukukhetha kwakho kwamakhukhi kulesi siphequluli. Ayilayishi izikripthi zokukhangisa noma zokuhlaziya.",
  necessaryOnly: "Okudingekayo kuphela",
  accept: "Yamukela",
  language: "Ulimi",
  formIntro: "Sicela ungathumeli imininingwane eyimfihlo kuze kube yilapho ifemu iqinisekisa ukuthi ingakusiza. Ukuthumela leli fomu akudali isigunyaziso.",
  name: "Igama",
  email: "I-imeyili",
  phone: "Ucingo",
  matterType: "Uhlobo lwendaba",
  otherSide: "Ubani ongakolunye uhlangothi?",
  when: "Kwenzeka nini?",
  organOfState: "Olunye uhlangothi luyisitho sombuso (umasipala, i-SAPS, umnyango, idolobha elikhulu)",
  whatHappened: "Kwenzekeni?",
  send: "Thumela umbuzo",
  couldNotSend: "Akukwazanga ukuthunyelwa.",
  payTitle: "Khokha i-invoyisi yakho",
  invoice: "I-invoyisi",
  testMode: "Imodi yokuhlola. Ayikho imali yangempela ezodluliswa.",
  payButton: "Khokha ngokuphepha nge-PayFast",
  payNote: "Ikhadi, i-EFT esheshayo nezinye izindlela ze-PayFast. I-Owlaw ayiyiboni imininingwane yekhadi lakho.",
  paid: "Kukhokhiwe. Siyabonga.",
  confirming: "Siyabonga. I-PayFast iqinisekisa inkokhelo yakho; lokhu kungathatha umzuzu.",
  cancelledTry: "Inkokhelo ikhanseliwe. Ungazama futhi ngezansi.",
  expired: "Lesi sixhumanisi siphelelwe yisikhathi. Cela ummeli wakho akunike esisha.",
  replaced: "Lesi sixhumanisi sithathelwe indawo esisha. Cela ummeli wakho akunike esakamuva.",
  siteKicker: "Iwebhusayithi yomphakathi",
  siteInReview: "Iwebhusayithi yomphakathi yaleli femu ilinde ukugunyazwa yi-Owlaw. Sicela uphinde ubheke maduze.",
  siteUpdating: "Iwebhusayithi yomphakathi yaleli femu iyabuyekezwa. Sicela uphinde ubheke maduze.",
  siteNotLive: "Le webhusayithi yomphakathi ayikakavulwa.",
  siteWorkHere: "Usebenza kuleli femu?",
  siteSetUp: "Setha bese uthumela iwebhusayithi yakho",
  siteInOwlaw: "ku-Owlaw.",
  closed: "Le invoyisi ayisakwazi ukukhokhwa ku-inthanethi. Sicela uxhumane nefemu uma unemibuzo.",
};

export const DICT: Record<Lang, Strings> = { en, af, zu };
const KEY = "ls_lang";

function initial(): Lang {
  if (typeof window === "undefined") return "en";
  const saved = window.localStorage.getItem(KEY) as Lang | null;
  if (saved && saved in DICT) return saved;
  const nav = (navigator.language || "").toLowerCase();
  if (nav.startsWith("af")) return "af";
  if (nav.startsWith("zu")) return "zu";
  return "en";
}

export function useLang() {
  const [lang, setLangState] = useState<Lang>("en");
  useEffect(() => {
    setLangState(initial());
    const on = () => setLangState(initial());
    window.addEventListener("ls-lang", on);
    return () => window.removeEventListener("ls-lang", on);
  }, []);
  useEffect(() => {
    document.documentElement.lang = lang === "en" ? "en-ZA" : lang;
  }, [lang]);
  const setLang = useCallback((l: Lang) => {
    window.localStorage.setItem(KEY, l);
    window.dispatchEvent(new Event("ls-lang"));
  }, []);
  return { lang, t: DICT[lang], setLang };
}
