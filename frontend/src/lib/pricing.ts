// Owlaw price list in rand, excluding VAT. Keep in step with backend Pricing.java.
export type PlanSlug = "solo" | "firm" | "practice";

export type PlanCard = {
  slug: PlanSlug;
  name: string;
  price: string;
  unit: string;
  tag?: string;
  items: string[];
};

export const PLANS: PlanCard[] = [
  {
    slug: "solo",
    name: "Solo",
    price: "R899",
    unit: "per month, 1 attorney",
    items: [
      "Matters, docket clocks, documents and calendar",
      "Billing, invoices, payments, write-offs and proof-of-payment review",
      "Section 86 trust, including trust-to-fee",
      "Firm number, SMS, WhatsApp and email",
      "100 assistant actions a month",
      "Public website add-on: R499 a month plus R1,500 setup",
    ],
  },
  {
    slug: "firm",
    name: "Firm",
    price: "R1,199",
    unit: "per attorney per month",
    tag: "Most firms",
    items: [
      "Everything in Solo",
      "Public website: people, expertise, insights, contact, theme and accent colours",
      "300 assistant actions per attorney",
      "Support staff at R349 a seat",
    ],
  },
  {
    slug: "practice",
    name: "Practice",
    price: "R1,599",
    unit: "per attorney per month",
    items: [
      "Everything in Firm",
      "1,000 assistant actions per attorney",
      "Call recording and advanced reporting",
      "Priority support and fast-track for website requests",
    ],
  },
];

export const ADD_ONS = [
  ["Support staff seat", "R349 per user a month (Firm and Practice)"],
  ["Calls on the firm number", "R1.49 a minute. No bundle, no unlimited voice. In-app calls are free."],
  ["Your own landline as caller ID", "R149 a month plus R8.90 a minute"],
  ["Public website for Solo", "R499 a month plus R1,500 setup"],
] as const;

export const PRICE_NOTE = "Prices exclude VAT. Plans are billed monthly through PayFast.";

export function planName(slug?: string | null): string {
  const s = (slug || "").toLowerCase();
  if (s === "light") return "Firm";
  return PLANS.find((p) => p.slug === s)?.name ?? "Firm";
}
