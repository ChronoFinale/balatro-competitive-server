// Renderer-side session: drives the IPC bridge (window.balatro) and holds the
// reactive game state. The main process owns the actual TCP socket; here we just
// send intents and react to server messages.
import { Store } from "@tanstack/store";
import type { ClientView, ServerMessage } from "./types";

export interface SessionState {
  status: "login" | "menu" | "game";
  conn: string; // connection status from main (connecting/connected/reconnecting/error)
  view: ClientView | null;
  myId: string | null;
  toast: string | null;
  banner: string | null;
  preview: { chips: number; mult: number; score: number } | null; // server score projection
  rulesets: string[]; // rulesets the server offers (sent on auth) — the client selects one
  lobbyCode: string; // code of a lobby this client created (to share), shown by the mp screen
  queueStatus: string; // matchmaking status, shown by the queue screen
}

export const store = new Store<SessionState>({
  status: "login",
  conn: "",
  view: null,
  myId: null,
  toast: null,
  banner: null,
  preview: null,
  rulesets: [],
  lobbyCode: "",
  queueStatus: "",
});

let toastTimer: ReturnType<typeof setTimeout> | undefined;
let seq = 1;

function set(patch: Partial<SessionState>) {
  store.setState((s) => ({ ...s, ...patch }));
}

export function toast(msg: string) {
  set({ toast: msg });
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => set({ toast: null }), 2000);
}

export function send(obj: Record<string, unknown>) {
  window.balatro.send({ ...obj, seq: seq++ });
}

/** Ask the server for a side-effect-free score projection of a selection. */
export function previewCards(cards: number[]) {
  if (!cards.length) {
    store.setState((s) => ({ ...s, preview: null }));
    return;
  }
  send({ type: "preview", cards });
}

export function newRun(ruleset?: string) {
  set({ banner: null });
  send({
    type: "newRun",
    ruleset, // server resolves the name (curated/bundle/custom); omitted = server default
    seed: Math.random().toString(36).slice(2, 8).toUpperCase(),
  });
}

/** Wire IPC message + status streams into the store. Call once at startup. */
export function initSession() {
  window.balatro.onStatus((s) => set({ conn: s.state }));
  window.balatro.onMessage((m: ServerMessage) => {
    switch (m.type) {
      case "authed":
        set({ myId: m.playerId ?? null, status: "menu", rulesets: m.rulesets ?? [] });
        break;
      case "update":
        if (m.accepted === false) toast(m.rejection ?? "rejected");
        if (m.view) set({ view: m.view, status: "game" });
        if (m.replay && m.replay.length) {
          const last = m.replay[m.replay.length - 1];
          toast(`scored ${last.runningChips} × ${last.runningMult}`);
        }
        break;
      case "preview":
        set({ preview: { chips: m.chips ?? 0, mult: m.mult ?? 0, score: m.score ?? 0 } });
        break;
      case "lobbyCreated":
        set({ lobbyCode: String(m.code ?? "") });
        toast(`Lobby ${m.code} — share the code`);
        break;
      case "queued":
        set({ queueStatus: String(m.status ?? "waiting") });
        break;
      case "error":
        toast(m.rejection ?? "error");
        break;
      case "pong":
        break;
      default:
        break;
    }
    const v = store.state.view;
    if (v && (v.phase === "RUN_WON" || v.phase === "RUN_LOST")) {
      set({ banner: v.phase === "RUN_WON" ? "🏆 RUN WON" : "💀 RUN LOST" });
    }
  });
}

export async function connect(username: string) {
  const token = await window.balatro.login(username);
  await window.balatro.connect(token); // main re-auths automatically on any reconnect
}
