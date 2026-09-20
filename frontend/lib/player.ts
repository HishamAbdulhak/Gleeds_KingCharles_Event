import type { GameEvent, GameOver, QuestionStart, Result } from "./game.ts";

/** What the Player's screen shows, driven by Game events and the Player's own tap. */
export type PlayerState =
  | { phase: "waiting" }
  | { phase: "question"; question: QuestionStart; notice?: string }
  | { phase: "locked"; question: QuestionStart; selected: number }
  /** question is null when the page was reopened mid-question and the timeout RESULT arrived first */
  | { phase: "result"; question: QuestionStart | null; selected: number | null; result: Result }
  | { phase: "over"; gameOver: GameOver };

export type PlayerAction = GameEvent | { type: "SELECT"; option: number };

export const WAITING: PlayerState = { phase: "waiting" };

export function reducePlayer(state: PlayerState, action: PlayerAction): PlayerState {
  switch (action.type) {
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
    case "GAME_OVER":
      return { phase: "over", gameOver: action.payload };
  }
}
