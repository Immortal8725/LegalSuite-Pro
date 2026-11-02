export type LitStage = { id: string; label: string; side: boolean; terminal?: boolean; after?: string | null };
export type LitRule = {
  id: string; trigger: string; title: string; amount: number; unit: string;
  source: string; needsReview: boolean; diesNon: boolean; note?: string | null;
};
export type LitTrack = { id: string; label: string; courtLevels: string[]; stages: LitStage[]; rules: LitRule[]; note?: string | null };
export type LitCatalog = { disclaimer: string; courtLevels: Record<string, string>; tracks: LitTrack[] };
export type LitDeadline = { id: string; title: string; description: string; status: string; priority: string; dueDate: string | null };
export type MatterLitigation = {
  caseId: string; track: string | null; trackLabel: string | null; courtLevel: string | null;
  stage: string | null; stageLabel: string | null;
  timeline: { id: string; stage: string; label: string; date: string; note: string | null }[];
  nextStages: { id: string; label: string; side: boolean }[];
  deadlines: LitDeadline[]; trackNote: string | null; disclaimer: string;
  createdDeadlines?: LitDeadline[];
};
export const UNIT_LABEL: Record<string, string> = {
  court_days: "court days", calendar_days: "days", weeks: "weeks", months: "months", years: "years", hours: "hours",
};
export const ADMIN_ROLES = ["owner", "admin", "partner", "director"];
