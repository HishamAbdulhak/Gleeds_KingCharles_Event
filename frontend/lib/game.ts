import { Client } from "@stomp/stompjs";
import { api, API_URL } from "@/lib/api";

/** Server → client STOMP messages, mirroring backend GameEvent. Later tickets add members to the union. */
export type QuestionStart = {
  index: number;
  total: number;
  text: string;
  options: [string, string, string, string];
  timeLimitSec: number;
  startedAt: string; // ISO instant
};

/** Personal queue (`/user/queue/player`): was the Answer taken? `reason` only when refused. */
export type AnswerAck = { accepted: boolean; reason: string | null };

/** Personal queue: what the Answer (or timeout) earned and the running Score. */
export type Result = { correct: boolean; points: number; streak: number; score: number; correctOption: number };

/** Game topic, Solo: final Score and total response time. */
export type GameOver = { score: number; totalResponseMs: number };

export type GameEvent =
  | { type: "QUESTION_START"; payload: QuestionStart }
  | { type: "ANSWER_ACK"; payload: AnswerAck }
  | { type: "RESULT"; payload: Result }
  | { type: "GAME_OVER"; payload: GameOver };

export async function startSolo(name: string, email: string, consent: boolean) {
  return api<{ gameId: string; playerId: string; sessionToken: string }>("/api/solo", {
    method: "POST",
    body: JSON.stringify({ name, email, consent }),
  });
}

/**
 * The Player's session token for one Game, kept for the tab's life so a refresh reconnects. sessionStorage can
 * throw (private browsing, disabled) — a lost seat is reported by the game page.
 */
export function saveSeat(gameId: string, sessionToken: string) {
  try {
    sessionStorage.setItem(`seat:${gameId}`, sessionToken);
  } catch {}
}
export function loadSeat(gameId: string) {
  try {
    return sessionStorage.getItem(`seat:${gameId}`);
  } catch {
    return null;
  }
}

export type Lead = { name: string; email: string };

/** The Lead this phone joined with, so "Play again" prefills the form. Same storage caveat as the Seat. */
export function saveLead(lead: Lead) {
  try {
    sessionStorage.setItem("lead", JSON.stringify(lead));
  } catch {}
}
let leadCache: { raw: string | null; lead: Lead | null } = { raw: null, lead: null };
/** Memoised on the stored string so useSyncExternalStore sees a stable snapshot. */
export function loadLead(): Lead | null {
  let raw: string | null = null;
  try {
    raw = sessionStorage.getItem("lead");
  } catch {}
  if (raw !== leadCache.raw) leadCache = { raw, lead: raw ? JSON.parse(raw) : null };
  return leadCache.lead;
}

/**
 * Connects with the session token, subscribes to the Game topic and the personal queue, then says ready
 * (docs/adr/0001; a no-op on a Game already running, so a reopened page just waits for the next message). Returns
 * `answer` to send an Answer and `disconnect`. `onError` fires once for a refused CONNECT/SUBSCRIBE (the server's
 * ERROR frame), which ends the connection; a dropped socket instead reports `onOnline(false)` and reconnects by itself.
 */
export function connectToGame(
  gameId: string,
  sessionToken: string,
  {
    onEvent,
    onError,
    onOnline,
  }: { onEvent: (event: GameEvent) => void; onError: (message: string) => void; onOnline: (online: boolean) => void },
) {
  const client = new Client({
    brokerURL: `${API_URL.replace(/^http/, "ws")}/ws`,
    connectHeaders: { "X-Session-Token": sessionToken },
    reconnectDelay: 2000,
    onConnect: () => {
      onOnline(true);
      const deliver = (frame: { body: string }) => onEvent(JSON.parse(frame.body) as GameEvent);
      client.subscribe(`/topic/game/${gameId}`, deliver);
      client.subscribe("/user/queue/player", deliver);
      client.publish({ destination: `/app/game/${gameId}/ready` });
    },
    onStompError: (frame) => {
      onError(frame.headers.message ?? "Refused by the server");
      void client.deactivate(); // the server closes the session after ERROR; don't reconnect with the same bad token
    },
    onWebSocketClose: () => onOnline(false),
  });
  client.activate();
  return {
    answer: (questionIndex: number, option: number) =>
      client.publish({ destination: `/app/game/${gameId}/answer`, body: JSON.stringify({ questionIndex, option }) }),
    disconnect: () => {
      client.onWebSocketClose = () => {}; // our own deactivate() closes the socket too
      void client.deactivate();
    },
  };
}
