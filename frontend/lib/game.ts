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

/** Game topic, Battle: how many Players are in on the open question, after every accepted Answer. */
export type AnswerCount = { answered: number; total: number };

/** Game topic, Battle: the question is over — the correct option and how many chose each, by option index. */
export type Reveal = { correctOption: number; counts: number[] };

/** One Player's place in the Standings: their Score, and what the question just played earned them. */
export type Standing = { playerId: string; name: string; score: number; delta: number };

/** Game topic, Battle between questions, as LEADERBOARD (the spec's name): the Standings, best first. */
export type Standings = { players: Standing[] };

/** Host topic, Battle: the roster the big screen shows — who is in on the open question, and everyone's Score. */
export type HostState = { players: { id: string; name: string; answered: boolean; score: number }[] };

/**
 * Game topic, the end of the Game. Solo: the final Score and rank on the Day Leaderboard (null only if a Reset
 * happened mid-Game). Battle: the Podium, every Player ranked, and no score/rank — each phone finds its own row.
 */
export type GameOver = { score: number | null; rank: number | null; podium: Standing[] | null };

/** One row of the Day Leaderboard. */
export type LeaderboardEntry = { rank: number; name: string; score: number };

/** Leaderboard topic (Admins only), as DAY_LEADERBOARD: the top of the Day Leaderboard whenever a Game finishes or a Reset happens. */
export type Board = { top: LeaderboardEntry[] };

export type GameEvent =
  | { type: "LOBBY_UPDATE"; payload: LobbyUpdate }
  | { type: "QUESTION_START"; payload: QuestionStart }
  | { type: "ANSWER_ACK"; payload: AnswerAck }
  | { type: "ANSWER_COUNT"; payload: AnswerCount }
  | { type: "RESULT"; payload: Result }
  | { type: "REVEAL"; payload: Reveal }
  | { type: "LEADERBOARD"; payload: Standings }
  | { type: "HOST_STATE"; payload: HostState }
  | { type: "GAME_OVER"; payload: GameOver };

/**
 * The Podium is revealed 3rd, then 2nd, then 1st, a beat apart (#9); a place below the top three is listed from the
 * start, so it waits for nothing. The big screen and the phones run this same schedule off the one GAME_OVER, so a
 * Player sees their place exactly when the room does — no extra message.
 */
const REVEALED_PLACES = 3;
export const podiumRevealMs = (rank: number) => Math.max(0, REVEALED_PLACES + 1 - rank) * 1500;

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

export type GameStatus = "LOBBY" | "QUESTION" | "REVEAL" | "LEADERBOARD" | "FINISHED";

/** Every Host command is idempotent and answers with the Game's new status. `start` is 409 below 2 Players. */
export const hostCommand = (gameId: string, command: "start" | "reveal" | "next" | "end") =>
  api<{ status: GameStatus }>(`/api/games/${gameId}/${command}`, { method: "POST" });

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

/**
 * Host screen of one Battle: the Game topic and the Host-only topic (Admins only, so a visitor can't read the
 * answers off it). Says ready after subscribing, so the lobby arrives even after a refresh.
 */
export const watchGame = (gameId: string, onEvent: (event: GameEvent) => void) =>
  connectAsAdmin((client) => {
    const deliver = (frame: { body: string }) => onEvent(JSON.parse(frame.body) as GameEvent);
    client.subscribe(`/topic/game/${gameId}`, deliver);
    client.subscribe(`/topic/game/${gameId}/host`, deliver);
    client.publish({ destination: `/app/game/${gameId}/ready` });
  });
