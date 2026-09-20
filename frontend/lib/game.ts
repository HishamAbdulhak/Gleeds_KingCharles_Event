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

/** Game topic, Solo: final Score. */
export type GameOver = { score: number };

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

export type Lead = { name: string; email: string };

/**
 * Per-tab storage: the Seat (session token per Game, so a refresh reconnects) and the Lead (so "Play again" prefills).
 * sessionStorage can throw (private browsing, disabled); a lost Seat is reported by the game page. `get` is memoised
 * on the stored string so useSyncExternalStore sees a stable snapshot.
 */
const cache = new Map<string, unknown>();
export const stored = {
  set(key: string, value: unknown) {
    try {
      sessionStorage.setItem(key, JSON.stringify(value));
    } catch {}
  },
  get<T>(key: string): T | null {
    try {
      const raw = sessionStorage.getItem(key);
      if (raw === null) return null;
      if (!cache.has(raw)) cache.set(raw, JSON.parse(raw));
      return cache.get(raw) as T;
    } catch {
      return null; // storage unavailable, or a value this code didn't write
    }
  },
};

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
