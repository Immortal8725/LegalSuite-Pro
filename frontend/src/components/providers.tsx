"use client";

import { AuthProvider } from "@/components/auth-provider";
import { CallProvider } from "@/components/call-provider";
import { TimerProvider } from "@/components/timer-provider";

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider>
      <TimerProvider>
        <CallProvider>{children}</CallProvider>
      </TimerProvider>
    </AuthProvider>
  );
}
