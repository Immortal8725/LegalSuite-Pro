"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  Scale,
  LayoutDashboard,
  Briefcase,
  Users,
  FileText,
  Calendar,
  CheckSquare,
  Timer,
  Receipt,
  Landmark,
  Wallet,
  MessageSquare,
  Phone,
  ShieldAlert,
  BarChart3,
  Puzzle,
  UserCog,
  Settings,
  Search,
  Bell,
  LogOut,
  Menu,
  Globe,
  Plus,
  FileSignature,
  Plug,
  ScrollText,
  PenLine,
} from "lucide-react";
import { useAuth } from "@/components/auth-provider";
import { useTimer } from "@/components/timer-provider";
import { apiGet, apiPatch } from "@/lib/api";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { Notice, Matter, Party } from "@/lib/types";

const NAV = [
  { section: "Main", items: [{ href: "/dashboard", label: "Docket", icon: LayoutDashboard }] },
  {
    section: "Practice",
    items: [
      { href: "/cases", label: "Cases & Matters", icon: Briefcase, module: "cases" },
      { href: "/clients", label: "Clients", icon: Users, module: "clients" },
      { href: "/contacts", label: "Contacts", icon: Users, module: "contacts" },
      { href: "/documents", label: "Documents", icon: FileText, module: "documents" },
      { href: "/templates", label: "Templates", icon: ScrollText, module: "templates" },
      { href: "/esign", label: "E-Sign", icon: FileSignature, module: "esignatures" },
      { href: "/calendar", label: "Calendar", icon: Calendar, module: "calendar" },
      { href: "/tasks", label: "Tasks", icon: CheckSquare, module: "tasks" },
    ],
  },
  {
    section: "Finance",
    items: [
      { href: "/time", label: "Time Tracking", icon: Timer, module: "timetracking" },
      { href: "/billing", label: "Client invoices", icon: Receipt, module: "billing" },
      { href: "/usage", label: "Month-end usage", icon: Receipt },
      { href: "/trust", label: "Trust Accounting", icon: Landmark, module: "trust" },
      { href: "/expenses", label: "Expenses", icon: Wallet, module: "expenses" },
    ],
  },
  {
    section: "Communication",
    items: [
      { href: "/messages", label: "Messages", icon: MessageSquare, module: "messages" },
      { href: "/voice", label: "Voice Calls", icon: Phone, module: "voice" },
      { href: "/leads", label: "Hire pipeline", icon: Globe },
      { href: "/ai", label: "Draft help", icon: PenLine, module: "ai" },
    ],
  },
  {
    section: "Admin",
    items: [
      { href: "/conflicts", label: "Conflict Check", icon: ShieldAlert, module: "conflicts" },
      { href: "/fitness", label: "Practice fitness", icon: ShieldAlert },
      { href: "/reports", label: "Reports", icon: BarChart3, module: "reports" },
      { href: "/integrations", label: "Integrations", icon: Plug, module: "integrations" },
      { href: "/audit", label: "Audit log", icon: PenLine, module: "audit" },
      { href: "/modules", label: "Modules", icon: Puzzle },
      { href: "/team", label: "Team", icon: UserCog },
      { href: "/settings", label: "Settings", icon: Settings },
    ],
  },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const { user, tenant, logout } = useAuth();
  const { timer, start, stop, label } = useTimer();
  const pathname = usePathname();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState("");
  const [hits, setHits] = useState<{ cases: Matter[]; clients: Party[] } | null>(null);
  const [notifs, setNotifs] = useState<Notice[]>([]);
  const [showNotifs, setShowNotifs] = useState(false);

  const enabled = new Set(tenant?.enabledModules || []);

  useEffect(() => {
    apiGet<Notice[]>("/api/v1/notifications")
      .then(setNotifs)
      .catch(() => setNotifs([]));
  }, []);

  useEffect(() => {
    if (!q.trim()) {
      setHits(null);
      return;
    }
    const t = setTimeout(() => {
      apiGet<{ cases: Matter[]; clients: Party[] }>(`/api/v1/search?q=${encodeURIComponent(q)}`)
        .then(setHits)
        .catch(() => setHits(null));
    }, 250);
    return () => clearTimeout(t);
  }, [q]);

  if (!user || user.role === "client") return <>{children}</>;

  return (
    <div className="min-h-screen bg-[#f6f8fb]">
      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-40 flex w-64 flex-col bg-navy-dark text-white transition-transform lg:translate-x-0",
          open ? "translate-x-0" : "-translate-x-full"
        )}
      >
        <div className="flex items-center gap-3 border-b border-white/10 px-5 py-5">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gold text-navy-dark">
            <Scale className="h-5 w-5" />
          </div>
          <div>
            <p className="text-sm font-bold">LegalSuite Pro</p>
            <p className="text-[11px] text-white/50">{tenant?.firmName}</p>
          </div>
        </div>
        <nav className="flex-1 overflow-y-auto px-3 py-4">
          {NAV.map((section) => (
            <div key={section.section} className="mb-5">
              <p className="mb-2 px-3 text-[10px] font-bold uppercase tracking-[0.16em] text-white/30">
                {section.section}
              </p>
              {section.items.map((item) => {
                const locked = "module" in item && item.module && !enabled.has(item.module);
                const active = pathname === item.href || pathname.startsWith(item.href + "/");
                const Icon = item.icon;
                return (
                  <Link
                    key={item.href}
                    href={locked ? "/modules" : item.href}
                    onClick={() => setOpen(false)}
                    className={cn(
                      "mb-0.5 flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition",
                      active ? "bg-gold/20 text-gold" : "text-white/70 hover:bg-white/5 hover:text-white",
                      locked && "opacity-40"
                    )}
                  >
                    <Icon className="h-4 w-4" />
                    <span className="flex-1">{item.label}</span>
                    {locked && <span className="text-[10px]">Locked</span>}
                  </Link>
                );
              })}
            </div>
          ))}
        </nav>
        <div className="border-t border-white/10 p-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-gold text-sm font-bold text-navy-dark">
              {user.initials}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold">{user.fullName}</p>
              <p className="text-[11px] text-white/50">{user.title || user.role}</p>
            </div>
          </div>
        </div>
      </aside>

      <div className="lg:pl-64">
        <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b bg-white px-4 sm:px-6">
          <button className="rounded-full bg-slate-100 p-2 lg:hidden" onClick={() => setOpen((v) => !v)}>
            <Menu className="h-4 w-4" />
          </button>
          <div className="relative max-w-md flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <Input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search cases, clients, documents..."
              className="rounded-full bg-slate-50 pl-9"
            />
            {hits && (
              <div className="absolute z-40 mt-2 w-full rounded-xl border bg-white p-2 shadow-lift">
                {hits.cases.map((c) => (
                  <button
                    key={c.id}
                    className="block w-full rounded-lg px-3 py-2 text-left text-sm hover:bg-slate-50"
                    onClick={() => {
                      router.push(`/cases/${c.id}`);
                      setQ("");
                    }}
                  >
                    <span className="font-mono text-xs text-navy">{c.caseNumber}</span> {c.title}
                  </button>
                ))}
                {hits.clients.map((c) => (
                  <button
                    key={c.id}
                    className="block w-full rounded-lg px-3 py-2 text-left text-sm hover:bg-slate-50"
                    onClick={() => {
                      router.push("/clients");
                      setQ("");
                    }}
                  >
                    {c.displayName} <span className="text-slate-400">{c.email}</span>
                  </button>
                ))}
                {hits.cases.length === 0 && hits.clients.length === 0 && (
                  <p className="px-3 py-2 text-sm text-slate-500">No matches.</p>
                )}
              </div>
            )}
          </div>
          <div className="ml-auto flex items-center gap-1">
            <Button size="icon" variant="ghost" title="New case" onClick={() => router.push("/cases")}>
              <Plus className="h-4 w-4" />
            </Button>
            <button
              className="relative rounded-full p-2 hover:bg-slate-100"
              onClick={() => setShowNotifs((v) => !v)}
            >
              <Bell className="h-4 w-4" />
              {notifs.some((n) => !n.read) && (
                <span className="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-red-500" />
              )}
            </button>
            <Button
              variant={timer.running ? "gold" : "ghost"}
              size="sm"
              onClick={() => (timer.running ? stop() : start())}
            >
              <Timer className="h-4 w-4" />
              {label}
            </Button>
            <Button size="icon" variant="ghost" onClick={logout} title="Sign out">
              <LogOut className="h-4 w-4" />
            </Button>
          </div>
        </header>
        {showNotifs && (
          <div className="fixed right-4 top-16 z-40 w-80 rounded-xl border bg-white shadow-lift">
            <div className="border-b px-4 py-3 font-bold">Notifications</div>
            <div className="max-h-80 overflow-y-auto">
              {notifs.length === 0 && <p className="p-4 text-sm text-slate-500">You are caught up.</p>}
              {notifs.map((n) => (
                <button
                  key={n.id}
                  className={cn("block w-full border-b px-4 py-3 text-left text-sm", !n.read && "bg-sky-50")}
                  onClick={() => {
                    apiPatch(`/api/v1/notifications/${n.id}/read`, {}).catch(() => undefined);
                    setNotifs((list) => list.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
                    setShowNotifs(false);
                    if (n.link) router.push(n.link);
                  }}
                >
                  <p className="font-semibold">{n.title}</p>
                  <p className="text-slate-500">{n.body}</p>
                </button>
              ))}
            </div>
          </div>
        )}
        <main className="p-4 sm:p-8">{children}</main>
      </div>
    </div>
  );
}
