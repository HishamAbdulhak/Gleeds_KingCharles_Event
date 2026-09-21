import { Client } from "@stomp/stompjs";
import { api, API_URL, getToken, logout } from "@/lib/api";

/** Shared by every STOMP client: the broker URL and a reconnect that keeps trying while the socket is down. */
const STOMP = { brokerURL: `${API_URL.replace(/^http/, "ws")}/ws`, reconnectDelay: 2000 };

// Server → client STOMP messages, mirroring backend GameEvent. Later tickets add members to the union.

/** Game topic, Battle in LOBBY: who has joined, in join order, and the PIN the Host shows. Sent on every join and every `ready`. */
export type LobbyUpdate = { pin: string; players: { id: string; name: string }[] };

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

/** Leaderboard topic (Admins only), as DAY_LEADERBOARD: the top of the Day Leaderboard whenever a Game finishes or a Reset happens. */
export type Board = { top: LeaderboardEntry[] };

export type GameEvent =
  | { type: "LOBBY_UPDATE"; payload: LobbyUpdate }
  | { type: "QUESTION_START"; payload: QuestionStart }
  | { type: "ANSWER_ACK"; payload: AnswerAck }
  | { type: "RESULT"; payload: Result }
  | { type: "GAME_OVER"; payload: GameOver };

/** What a Player gives to join any Game: the Lead and consent (backend JoinRequest). */
export type JoinRequest = { name: string; email: string; consent: boolean };

/** A Player's Seat in one Game: the ids and the session token the phone keeps. */
export type Seat = { gameId: string; playerId: string; sessionToken: string };

export const startSolo = (req: JoinRequest) => api<Seat>("/api/solo", { method: "POST", body: JSON.stringify(req) });

/** 404 for an unknown PIN; 409 with message `LOBBY_FULL` / `GAME_STARTED`; the same email gets its existing Seat back. */
export const joinBattle = (pin: string, req: JoinRequest) =>
  api<Seat>(`/api/games/${pin}/join`, { method: "POST", body: JSON.stringify(req) });

// --- Host commands (admin JWT) ---

export const createBattle = () => api<{ gameId: string; pin: string }>("/api/games", { method: "POST" });

/** 409 below 2 Players. */
export const startBattle = (gameId: string) => api<void>(`/api/games/${gameId}/start`, { method: "POST" });

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
    ...STOMP,
    connectHeaders: { "X-Session-Token": sessionToken },
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
 * The Host screen's connection. Admin JWT; reconnects by itself, running `onConnect` again; a refused CONNECT (expired
 * token) logs out like a 401 would. Returns the disconnect.
 */
function connectAsAdmin(onConnect: (client: Client) => void) {
  const client = new Client({
    ...STOMP,
    connectHeaders: { Authorization: `Bearer ${getToken()}` },
    onConnect: () => onConnect(client),
    onStompError: logout,
  });
  client.activate();
  return () => void client.deactivate();
}

/** Host idle screen: the top of the Day Leaderboard as it changes. */
export const watchLeaderboard = (onTop: (top: LeaderboardEntry[]) => void) =>
  connectAsAdmin((client) =>
    client.subscribe("/topic/leaderboard", (frame) =>
      onTop((JSON.parse(frame.body) as { payload: Board }).payload.top),
    ),
  );

/** Host screen of one Battle: the Game topic. Says ready after subscribing, so the lobby arrives even after a refresh. */
export const watchGame = (gameId: string, onEvent: (event: GameEvent) => void) =>
  connectAsAdmin((client) => {
    client.subscribe(`/topic/game/${gameId}`, (frame) => onEvent(JSON.parse(frame.body) as GameEvent));
    client.publish({ destination: `/app/game/${gameId}/ready` });
  });
