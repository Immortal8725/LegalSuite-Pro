export type ClientSummary = {
  locale: string;
  labels: { means: string; next: string; needFromYou: string; nextDate: string };
  disclaimer: string;
  track: string | null;
  trackLabel: string | null;
  stage: string | null;
  stageLabel: string | null;
  means: string | null;
  next: string | null;
  needFromYou: string | null;
  firmNote: string | null;
  nextDate: { date: string; what: string; text: string } | null;
};

export type InboxMessage = {
  id: string;
  senderType: "staff" | "client" | "system";
  senderName: string | null;
  kind: "message" | "stage_update";
  body: string | null;
  createdAt: string;
  readByClientAt: string | null;
  readByStaffAt: string | null;
  attachment?: { name: string; size: number | null; documentId?: string };
};

export type StaffThread = {
  caseId: string;
  clientId: string | null;
  clientName: string | null;
  clientEmail: string | null;
  portalEnabled: boolean;
  emailConfigured: boolean;
  messages: InboxMessage[];
};

export const MAX_UPLOAD_BYTES = 50 * 1024 * 1024;

export function sizeLabel(n?: number | null) {
  if (!n) return "";
  if (n < 1024 * 1024) return `${Math.max(1, Math.round(n / 1024))} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

export function when(iso: string) {
  return new Date(iso).toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short", timeZone: "Africa/Johannesburg" });
}

export function inboxChanged() {
  if (typeof window !== "undefined") window.dispatchEvent(new Event("owlaw:inbox-changed"));
}
