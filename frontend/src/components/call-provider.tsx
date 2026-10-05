"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { apiGet, apiPost, getToken } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { Button } from "@/components/ui/button";
import type { CallRow, TeamMember } from "@/lib/types";

type Signal = Record<string, unknown> & { type: string; to?: string; from?: string };

type CallCtx = {
  startCall: (peer: TeamMember, extras?: { caseId?: string; clientId?: string; record?: boolean }) => Promise<void>;
};

const Ctx = createContext<CallCtx | null>(null);

function wsUrl(token: string) {
  if (typeof window === "undefined") return "";
  const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${proto}//${window.location.host}/ws/signal?token=${encodeURIComponent(token)}`;
}

export function CallProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [incoming, setIncoming] = useState<{ from: string; name?: string; callId?: string } | null>(null);
  const [active, setActive] = useState<{ peerName: string; started: number; callId?: string } | null>(null);
  const [elapsed, setElapsed] = useState("0:00");
  const pcRef = useRef<RTCPeerConnection | null>(null);
  const localStream = useRef<MediaStream | null>(null);
  const remoteAudio = useRef<HTMLAudioElement | null>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const iceRef = useRef<RTCIceServer[]>([{ urls: "stun:stun.l.google.com:19302" }]);

  const sendSignal = useCallback(async (msg: Signal) => {
    const ws = wsRef.current;
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(msg));
      return;
    }
    await apiPost("/api/v1/voice/signal", msg);
  }, []);

  const hangUp = useCallback(
    async (callId?: string) => {
      pcRef.current?.close();
      pcRef.current = null;
      localStream.current?.getTracks().forEach((t) => t.stop());
      localStream.current = null;
      if (callId) {
        try {
          await apiPost(`/api/v1/calls/${callId}/end`, { notes: "WebRTC call" });
        } catch {
          /* already closed */
        }
      }
      setActive(null);
      setIncoming(null);
    },
    []
  );

  const ensurePc = useCallback(async () => {
    if (pcRef.current) return pcRef.current;
    const pc = new RTCPeerConnection({ iceServers: iceRef.current });
    pc.onicecandidate = (ev) => {
      const peer = (pc as RTCPeerConnection & { _peerId?: string })._peerId;
      if (ev.candidate && peer) {
        sendSignal({ type: "ice", to: peer, candidate: ev.candidate });
      }
    };
    pc.ontrack = (ev) => {
      if (remoteAudio.current) remoteAudio.current.srcObject = ev.streams[0];
    };
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    localStream.current = stream;
    stream.getTracks().forEach((t) => pc.addTrack(t, stream));
    pcRef.current = pc;
    return pc;
  }, [sendSignal]);

  const handleSignal = useCallback(
    async (msg: Signal) => {
      if (msg.type === "offer" && msg.from) {
        setIncoming({ from: msg.from, name: String(msg.callerName || "Incoming call"), callId: String(msg.callId || "") });
        const pc = await ensurePc();
        (pc as RTCPeerConnection & { _peerId?: string })._peerId = msg.from;
        await pc.setRemoteDescription(new RTCSessionDescription(msg.offer as RTCSessionDescriptionInit));
      } else if (msg.type === "answer") {
        await pcRef.current?.setRemoteDescription(new RTCSessionDescription(msg.answer as RTCSessionDescriptionInit));
      } else if (msg.type === "ice") {
        if (msg.candidate) await pcRef.current?.addIceCandidate(new RTCIceCandidate(msg.candidate as RTCIceCandidateInit));
      } else if (msg.type === "hangup") {
        hangUp();
      }
    },
    [ensurePc, hangUp]
  );

  useEffect(() => {
    if (!user || user.role === "client") return;
    const token = getToken();
    if (!token) return;
    apiGet<{ iceServers: RTCIceServer[] }>("/api/v1/voice/ice-servers")
      .then((d) => {
        if (d.iceServers?.length) iceRef.current = d.iceServers;
      })
      .catch(() => undefined);

    let ws: WebSocket | null = null;
    try {
      ws = new WebSocket(wsUrl(token));
      ws.onopen = () => ws?.send(JSON.stringify({ type: "register", userId: user.id }));
      ws.onmessage = (ev) => {
        try {
          handleSignal(JSON.parse(ev.data));
        } catch {
          /* ignore */
        }
      };
      wsRef.current = ws;
    } catch {
      wsRef.current = null;
    }

    const poll = setInterval(async () => {
      try {
        const inbox = await apiGet<Signal[]>("/api/v1/voice/inbox");
        for (const msg of inbox) await handleSignal(msg);
      } catch {
        /* ignore */
      }
    }, 1200);

    return () => {
      clearInterval(poll);
      ws?.close();
    };
  }, [user, handleSignal]);

  useEffect(() => {
    if (!active) return;
    const id = setInterval(() => {
      const s = Math.floor((Date.now() - active.started) / 1000);
      setElapsed(`${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`);
    }, 500);
    return () => clearInterval(id);
  }, [active]);

  const startCall = useCallback(
    async (peer: TeamMember, extras?: { caseId?: string; clientId?: string; record?: boolean }) => {
      if (!user) return;
      const rec = await apiPost<CallRow>("/api/v1/calls/initiate", {
        callType: "webrtc",
        direction: "internal",
        calleeUserId: peer.id,
        caseId: extras?.caseId,
        clientId: extras?.clientId,
        recordingEnabled: extras?.record ?? false,
        recordingConsentGiven: extras?.record ?? false,
      });
      const pc = await ensurePc();
      (pc as RTCPeerConnection & { _peerId?: string })._peerId = peer.id;
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      await sendSignal({
        type: "offer",
        to: peer.id,
        offer,
        callerName: user.fullName,
        callId: rec.id,
      });
      setActive({ peerName: peer.fullName, started: Date.now(), callId: rec.id });
    },
    [ensurePc, sendSignal, user]
  );

  const accept = useCallback(async () => {
    if (!incoming) return;
    const pc = await ensurePc();
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    await sendSignal({ type: "answer", to: incoming.from, answer });
    if (incoming.callId) {
      try {
        await apiPost(`/api/v1/calls/${incoming.callId}/answer`, {});
      } catch {
        /* optional */
      }
    }
    setActive({ peerName: incoming.name || "Caller", started: Date.now(), callId: incoming.callId });
    setIncoming(null);
  }, [ensurePc, incoming, sendSignal]);

  return (
    <Ctx.Provider value={{ startCall }}>
      {children}
      <audio ref={remoteAudio} autoPlay />
      {incoming && (
        <div className="fixed bottom-6 right-6 z-[90] w-80 rounded-2xl bg-navy-dark p-5 text-white shadow-lift">
          <p className="text-xs uppercase tracking-wide text-gold">Incoming call</p>
          <p className="mt-1 text-lg font-bold">{incoming.name}</p>
          <p className="text-sm text-white/70">In-app WebRTC, free for both sides</p>
          <div className="mt-4 flex gap-2">
            <Button variant="gold" className="flex-1" onClick={accept}>
              Answer
            </Button>
            <Button variant="outline" className="flex-1 bg-white" onClick={() => hangUp(incoming.callId)}>
              Decline
            </Button>
          </div>
        </div>
      )}
      {active && (
        <div className="fixed bottom-6 right-6 z-[90] w-80 rounded-2xl border border-gold/40 bg-white p-5 shadow-lift">
          <p className="text-xs font-bold uppercase tracking-wide text-gold">Live call</p>
          <p className="mt-1 text-lg font-bold text-navy">{active.peerName}</p>
          <p className="font-mono text-sm text-slate-500">{elapsed}</p>
          <Button className="mt-4 w-full" variant="danger" onClick={() => hangUp(active.callId)}>
            End call
          </Button>
        </div>
      )}
    </Ctx.Provider>
  );
}

export function useCalls() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useCalls requires CallProvider");
  return ctx;
}
