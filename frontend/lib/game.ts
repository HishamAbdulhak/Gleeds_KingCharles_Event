import { Client } from "@stomp/stompjs";
import { api, API_URL, getToken, logout } from "@/lib/api";

const WS_URL = `${API_URL.replace(/^http/, "ws")}/ws`;

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

/** Game topic, Solo: final Score and rank on the Day Leaderboard (null only if a Reset happened mid-Game). */
export type GameOver = { score: number; rank: number | null };

/** One row of the Day Leaderboard. */
export type LeaderboardEntry = { rank: number; name: string; score: number };

/**
 * Leaderboard topic (Admins only): the top of the Day Leaderboard, pushed whenever a Game finishes or a Reset happens.
 * Its own topic, so not part of the Game topic's union below.
 */
export type DayLeaderboard = { type: "DAY_LEADERBOARD"; payload: { top: LeaderboardEntry[] } };

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
 * Per-tab storage: the Seat (session token per Game, so a refresh reconnects) and the Lead (so "Play again" prefills).
 * sessionStorage can throw (private browsing, disabled); a lost Seat is reported by the game page.
 */
export const stored = {
  set(key: string, value: string) {
    try {
      sessionStorage.setItem(key, value);
    } catch {}
  },
  get(key: string) {
    try {
      return sessionStorage.getItem(key);
    } catch {
      return null;
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
  opts: {
    onEvent: (event: GameEvent) => void;
    onError: (message: string) => void;
    onOnline: (online: boolean) => void;
  },
) {
  const client = new Client({
    brokerURL: WS_URL,
    connectHeaders: { "X-Session-Token": sessionToken },
    reconnectDelay: 2000,
    onConnect: () => {
      opts.onOnline(true);
      const deliver = (frame: { body: string }) => opts.onEvent(JSON.parse(frame.body) as GameEvent);
      client.subscribe(`/topic/game/${gameId}`, deliver);
      client.subscribe("/user/queue/player", deliver);
      client.publish({ destination: `/app/game/${gameId}/ready` });
    },
    onStompError: (frame) => {
      opts.onError(frame.headers.message ?? "Refused by the server");
      void client.deactivate(); // the server closes the session after ERROR; don't reconnect with the same bad token
    },
    onWebSocketClose: () => opts.onOnline(false),
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

/** The board as the Host screen shows it; the server decides how many rows that is. */
export const fetchLeaderboard = () => api<LeaderboardEntry[]>("/api/leaderboard");

/**
 * Host screen: the top of the Day Leaderboard as it changes. Connects with the admin JWT and reconnects by itself;
 * a refused CONNECT (expired token) logs out like a 401 would. Returns the disconnect.
 */
export function watchLeaderboard(onTop: (top: LeaderboardEntry[]) => void) {
  const client = new Client({
    brokerURL: WS_URL,
    connectHeaders: { Authorization: `Bearer ${getToken()}` },
    reconnectDelay: 2000,
    onConnect: () =>
      client.subscribe("/topic/leaderboard", (frame) => onTop((JSON.parse(frame.body) as DayLeaderboard).payload.top)),
    onStompError: logout,
  });
  client.activate();
  return () => void client.deactivate();
}
