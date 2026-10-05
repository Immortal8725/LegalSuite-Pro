export type Id = string;

export type DocketClock = {
  kind: string;
  ruleId?: string;
  citation?: string;
  title?: string;
  date: string;
  daysLeft?: number;
  reason?: string;
  assumption?: string;
};

export type DocketPreview = {
  jurisdiction?: string;
  track?: string;
  solDate?: string;
  controllingDate?: string;
  controllingKind?: string;
  controllingCitation?: string;
  clocks?: DocketClock[];
  caveats?: string[];
  disclaimer?: string;
};

export type DocketItem = {
  kind: string;
  label?: string;
  date: string;
  daysLeft: number;
  urgency: string;
  caseId?: Id;
  caseNumber?: string;
  title?: string;
  citation?: string;
  reason?: string;
};

export type Dashboard = {
  activeCases: number;
  totalClients: number;
  pendingTasks: number;
  hoursBilled: number;
  revenueMtd: number;
  outstanding: number;
  unreadNotifications: number;
  callCount: number;
  newLeads?: number;
  jurisdiction?: string;
  trustLabel?: string;
  currency?: string;
  trustRecon?: TrustRecon;
  docket?: DocketItem[];
  recentCases: Matter[];
  upcomingEvents: CalEvent[];
};

export type Matter = {
  id: Id;
  clientId?: Id;
  clientName?: string;
  caseNumber: string;
  title: string;
  description?: string;
  caseType?: string;
  practiceArea?: string;
  status: string;
  priority?: string;
  courtName?: string;
  judgeName?: string;
  opposingParty?: string;
  opposingCounsel?: string;
  billingType?: string;
  billingRate?: number;
  dateOpened?: string;
  statuteOfLimitations?: string;
  accrualDate?: string;
  governmentalDefendant?: boolean;
  docketTrack?: string;
  jurisdiction?: string;
  solCitation?: string;
  solReason?: string;
  docketClocks?: DocketClock[];
  engagementStatus?: string;
  appearanceAuthorized?: boolean;
  engagementSignatureId?: Id;
  pendingRetainerAmount?: number;
  conflictWaiverSignatureId?: Id;
  conflictWaiverHash?: string;
  docketHold?: boolean;
  docketHoldReason?: string;
  noticeServed?: boolean;
  rafClaimLodged?: boolean;
  rafLodgedDate?: string;
  notes?: Note[];
  documents?: Doc[];
};

export type Party = {
  id: Id;
  type: string;
  status: string;
  firstName?: string;
  lastName?: string;
  companyName?: string;
  displayName: string;
  email?: string;
  phone?: string;
  city?: string;
  state?: string;
  source?: string;
  portalEnabled?: boolean;
  notes?: string;
};

export type Contact = {
  id: Id;
  type: string;
  name: string;
  firstName?: string;
  lastName?: string;
  company?: string;
  title?: string;
  email?: string;
  phone?: string;
};

export type Doc = {
  id: Id;
  name: string;
  category?: string;
  mimeType?: string;
  sizeBytes?: number;
  caseId?: Id;
  createdAt?: string;
  privileged?: boolean;
};

export type CalEvent = {
  id: Id;
  title: string;
  description?: string;
  type: string;
  startTime: string;
  endTime?: string;
  location?: string;
  caseId?: Id;
  status?: string;
};

export type Task = {
  id: Id;
  title: string;
  description?: string;
  status: string;
  priority: string;
  dueDate?: string;
  caseId?: Id;
  assignedTo?: Id;
};

export type Note = { id: Id; title?: string; body: string; type?: string; createdAt?: string };

export type TimeRow = {
  id: Id;
  caseId?: Id;
  userId?: Id;
  date: string;
  durationMinutes: number;
  hourlyRate?: number;
  totalAmount?: number;
  description: string;
  billable: boolean;
  billed: boolean;
  source?: string;
};

export type Invoice = {
  id: Id;
  invoiceNumber: string;
  clientId: Id;
  caseId?: Id;
  status: string;
  dateIssued?: string;
  dateDue?: string;
  subtotal?: number;
  taxAmount?: number;
  total: number;
  amountPaid?: number;
  balanceDue?: number;
  rawLineItems?: string;
  notes?: string;
};

export type Expense = {
  id: Id;
  caseId?: Id;
  category: string;
  description: string;
  amount: number;
  date: string;
  vendor?: string;
  billable?: boolean;
  billed?: boolean;
  invoiceId?: Id;
  status?: string;
};

export type TrustAcct = {
  id: Id;
  accountName: string;
  bankName?: string;
  balance: number;
  bankBalance?: number;
  lastReconciledAt?: string;
  accountType?: string;
  status?: string;
  recon?: TrustReconLive;
};
export type TrustTx = {
  id: Id;
  type: string;
  amount: number;
  balanceAfter: number;
  description: string;
  clientId?: Id;
  createdAt: string;
};
export type TrustReconLive = {
  accountId?: Id;
  accountName?: string;
  bookBalance: number;
  bankBalance: number;
  clientLedgerTotal: number;
  bankVsBook?: number;
  bookVsClients?: number;
  difference: number;
  status: string;
  ledgers?: { clientId?: string; balance: number; unallocated?: boolean }[];
  rule?: string;
};
export type TrustRecon = {
  worstStatus?: string;
  accounts?: TrustReconLive[];
  history?: {
    id: Id;
    periodEnd?: string;
    difference?: number;
    status: string;
    certified?: boolean;
    notes?: string;
  }[];
};

export type CallRow = {
  id: Id;
  callType: string;
  direction: string;
  status: string;
  startedAt: string;
  endedAt?: string;
  durationSeconds: number;
  totalCost?: number;
  recordingEnabled?: boolean;
  callerUserId?: Id;
  calleeUserId?: Id;
  caseId?: Id;
  clientId?: Id;
  notes?: string;
};

export type Conversation = {
  id: Id;
  title: string;
  type: string;
  lastMessageAt?: string;
  messages: { id: Id; senderId?: Id; body: string; createdAt: string }[];
};

export type Notice = {
  id: Id;
  title: string;
  body: string;
  type?: string;
  read: boolean;
  createdAt: string;
  link?: string;
};

export type ModuleCard = {
  id: Id;
  name: string;
  slug: string;
  description: string;
  category: string;
  icon?: string;
  priceMonthly?: number;
  core?: boolean;
  enabled?: boolean;
};

export type TeamMember = {
  id: Id;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  initials: string;
  role: string;
  title?: string;
  hourlyRate?: number;
  onlineStatus?: string;
};

export type Lead = {
  id: Id;
  name: string;
  email: string;
  phone?: string;
  caseType?: string;
  description?: string;
  opposingParty?: string;
  accrualDate?: string;
  governmentalDefendant?: boolean;
  status: string;
  createdAt: string;
  docket?: DocketPreview;
  caseId?: Id;
  waiver?: { id: Id; status: string; signUrl?: string; documentHash?: string; signatureHash?: string };
  engagement?: { id: Id; status: string; signUrl?: string; documentHash?: string; signatureHash?: string };
};

export type ConflictHit = {
  id: Id;
  searchName: string;
  status: string;
  matchCount: number;
  createdAt: string;
  matches?: { type: string; role?: string; name: string; detail: string; confidence: number; how?: string; caseNumber?: string }[];
};

export type AuditRow = {
  id: Id;
  action: string;
  entityType?: string;
  entityId?: string;
  detail?: string;
  actorEmail?: string;
  createdAt: string;
};

export type DocTemplate = {
  id: Id;
  name: string;
  category: string;
  body: string;
  createdAt?: string;
  merged?: string;
};

export type SignReq = {
  id: Id;
  title: string;
  documentBody: string;
  signerName: string;
  signerEmail: string;
  status: string;
  signUrl?: string;
  signatureDataUrl?: string | null;
  signedAt?: string;
  createdAt?: string;
  caseId?: Id;
  clientId?: Id;
  purpose?: string;
  documentHash?: string;
  signatureHash?: string | null;
  unlocked?: boolean;
  signatureStandard?: string;
  identityCaptured?: boolean;
};

export type Integration = {
  provider: string;
  name: string;
  category: string;
  description: string;
  connected: boolean;
  statusNote?: string;
  connectedAt?: string;
};

export type Landing = {
  tenant: {
    firmName: string;
    slug: string;
    phone?: string;
    email?: string;
    addressLine1?: string;
    city?: string;
    state?: string;
    zip?: string;
    country?: string;
    tagline?: string;
    practiceAreas?: string[];
  };
  template?: string;
  heroTitle: string;
  heroSubtitle?: string;
  aboutText?: string;
  attorneys: TeamMember[];
  practiceAreas: string[];
};
