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

export type GameEvent = { type: "QUESTION_START"; payload: QuestionStart };

export type Seat = { playerId: string; sessionToken: string };

export async function startSolo(name: string, email: string, consent: boolean) {
  return api<Seat & { gameId: string }>("/api/solo", {
    method: "POST",
    body: JSON.stringify({ name, email, consent }),
  });
}

/** The Player's seat in one Game, kept for the tab's life so a refresh reconnects (ids only; the name lives server-side). */
export const saveSeat = (gameId: string, seat: Seat) => sessionStorage.setItem(`seat:${gameId}`, JSON.stringify(seat));
/** Raw JSON (a stable snapshot for useSyncExternalStore); parse with {@link parseSeat}. */
export const loadSeat = (gameId: string) => sessionStorage.getItem(`seat:${gameId}`);
export const parseSeat = (raw: string) => JSON.parse(raw) as Seat;

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
  let closing = false; // our own deactivate() also fires onWebSocketClose
  const client = new Client({
    brokerURL: `${API_URL.replace(/^http/, "ws")}/ws`,
    connectHeaders: { "X-Session-Token": sessionToken },
    onConnect: () => {
      client.subscribe(`/topic/game/${gameId}`, (frame) => onEvent(JSON.parse(frame.body) as GameEvent));
      client.publish({ destination: `/app/game/${gameId}/ready` });
    },
    onStompError: (frame) => onError(frame.headers.message ?? "Refused by the server"),
    onWebSocketClose: () => {
      if (!closing) onError("Connection lost");
    },
  });
  client.activate();
  return () => {
    closing = true;
    void client.deactivate();
  };
}
