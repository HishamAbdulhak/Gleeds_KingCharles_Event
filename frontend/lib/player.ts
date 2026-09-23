import type { GameEvent, GameOver, LobbyUpdate, QuestionStart, Result } from "./game.ts";

/** What the Player's screen shows, driven by Game events and the Player's own tap. */
export type PlayerState =
  | { phase: "waiting" }
  /** Battle: "You're in" with the names in the lobby, until the Host starts */
  | { phase: "lobby"; players: LobbyUpdate["players"] }
  | { phase: "question"; question: QuestionStart; notice?: string }
  | { phase: "locked"; question: QuestionStart; selected: number }
  /** question is null when the page was reopened mid-question and the timeout RESULT arrived first */
  | { phase: "result"; question: QuestionStart | null; selected: number | null; result: Result }
  /** Battle between questions: the leaderboard is on the big screen, so the phone says to look at it */
  | { phase: "between" }
  | { phase: "over"; gameOver: GameOver };

export type PlayerAction = GameEvent | { type: "SELECT"; option: number };

export const WAITING: PlayerState = { phase: "waiting" };

export function reducePlayer(state: PlayerState, action: PlayerAction): PlayerState {
  switch (action.type) {
    case "LOBBY_UPDATE":
      return { phase: "lobby", players: action.payload.players };
    case "QUESTION_START":
      return { phase: "question", question: action.payload };
    case "SELECT":
      return state.phase === "question"
        ? { phase: "locked", question: state.question, selected: action.option }
        : state;
    case "ANSWER_ACK":
      return state.phase === "locked" && !action.payload.accepted
        ? { phase: "question", question: state.question, notice: action.payload.reason ?? "Not accepted" }
        : state;
    case "RESULT":
      return {
        phase: "result",
        question: "question" in state ? state.question : null,
        selected: state.phase === "locked" ? state.selected : null,
        result: action.payload,
      };
    case "LEADERBOARD":
      return { phase: "between" };
    case "GAME_OVER":
      return { phase: "over", gameOver: action.payload };
    case "ANSWER_COUNT":
    case "REVEAL":
    case "HOST_STATE":
      return state; // the big screen's, not the phone's
  }
}
