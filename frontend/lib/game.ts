import { Client } from "@stomp/stompjs";
import { api, API_URL } from "@/lib/api";

/** Server → client STOMP messages, mirroring backend GameEvent. Later tickets add members to the union. */
export type QuestionStart = {
  index: number;
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

/**
 * Connects with the session token, subscribes to the Game topic, then says ready (docs/adr/0001). Returns the
 * disconnect function. `onError` fires for a refused CONNECT/SUBSCRIBE (the server's ERROR frame) or a dropped socket.
 */
export function connectToGame(
  gameId: string,
  sessionToken: string,
  onEvent: (event: GameEvent) => void,
  onError: (message: string) => void,
) {
  const client = new Client({
    brokerURL: `${API_URL.replace(/^http/, "ws")}/ws`,
    connectHeaders: { "X-Session-Token": sessionToken },
    onConnect: () => {
      client.subscribe(`/topic/game/${gameId}`, (frame) => onEvent(JSON.parse(frame.body) as GameEvent));
      client.publish({ destination: `/app/game/${gameId}/ready` });
    },
    onStompError: (frame) => onError(frame.headers.message ?? "Refused by the server"),
    onWebSocketClose: () => onError("Connection lost"),
  });
  client.activate();
  return () => {
    client.onWebSocketClose = () => {}; // our own deactivate() closes the socket too
    void client.deactivate();
  };
}
