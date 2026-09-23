import type { GameEvent, HostState, LobbyUpdate, QuestionStart, Reveal, Standing } from "./game.ts";

/** What the big screen shows, driven by the Game topic and the Host-only topic. */
export type HostScreen =
  | { phase: "lobby"; pin: string | null; players: LobbyUpdate["players"] }
  /** `roster` is filled by the HOST_STATE the server sends with every question, and says who is already in */
  | { phase: "question"; question: QuestionStart; roster: HostState["players"] }
  | { phase: "reveal"; question: QuestionStart; reveal: Reveal }
  | { phase: "standings"; standings: Standing[] }
  | { phase: "podium"; podium: Standing[] };

/** Before the first event: a Battle is always in its lobby when the Host screen opens. */
export const LOBBY: HostScreen = { phase: "lobby", pin: null, players: [] };

export function reduceHost(screen: HostScreen, event: GameEvent): HostScreen {
  switch (event.type) {
    case "LOBBY_UPDATE":
      return { phase: "lobby", pin: event.payload.pin, players: event.payload.players };
    case "QUESTION_START":
      return { phase: "question", question: event.payload, roster: [] };
    case "HOST_STATE":
      return screen.phase === "question" ? { ...screen, roster: event.payload.players } : screen;
    case "REVEAL":
      return screen.phase === "question"
        ? { phase: "reveal", question: screen.question, reveal: event.payload }
        : screen;
    case "LEADERBOARD":
      return { phase: "standings", standings: event.payload.players };
    case "GAME_OVER":
      return { phase: "podium", podium: event.payload.podium ?? [] };
    case "ANSWER_ACK":
    case "RESULT":
    case "BEST_SCORE":
      return screen; // a Player's own, on their queue; the big screen never subscribes to it
    default:
      // as in reducePlayer: exhaustive at compile time, but an unknown event leaves the room's screen standing
      event satisfies never;
      return screen;
  }
}
