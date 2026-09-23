import assert from "node:assert/strict";
import { test } from "node:test";
import type { QuestionStart, Sync } from "./game.ts";
import type { PlayerAction } from "./player.ts";
import { reducePlayer, WAITING } from "./player.ts";

const q1: QuestionStart = {
  index: 0,
  total: 10,
  text: "Who?",
  options: ["A", "B", "C", "D"],
  timeLimitSec: 20,
  startedAt: "2026-09-20T10:00:00Z",
};

test("QUESTION_START shows the question with nothing selected", () => {
  const state = reducePlayer(WAITING, { type: "QUESTION_START", payload: q1 });
  assert.deepEqual(state, { phase: "question", question: q1 });
});

const asked = reducePlayer(WAITING, { type: "QUESTION_START", payload: q1 });

test("tapping an option locks it in", () => {
  const state = reducePlayer(asked, { type: "SELECT", option: 2 });
  assert.deepEqual(state, { phase: "locked", question: q1, selected: 2 });
});

test("a locked Answer cannot be changed", () => {
  const locked = reducePlayer(asked, { type: "SELECT", option: 2 });
  assert.equal(reducePlayer(locked, { type: "SELECT", option: 3 }), locked);
});

const locked = reducePlayer(asked, { type: "SELECT", option: 2 });

test("a refused ANSWER_ACK reopens the question with the reason", () => {
  const state = reducePlayer(locked, { type: "ANSWER_ACK", payload: { accepted: false, reason: "Too late" } });
  assert.deepEqual(state, { phase: "question", question: q1, notice: "Too late" });
});

test("an accepted ANSWER_ACK keeps the Answer locked", () => {
  assert.equal(reducePlayer(locked, { type: "ANSWER_ACK", payload: { accepted: true, reason: null } }), locked);
});

const won = { correct: true, points: 870, streak: 2, score: 1500, correctOption: 2 };
const timedOut = { correct: false, points: 0, streak: 0, score: 630, correctOption: 1 };

test("RESULT after an Answer shows it against the question and the chosen option", () => {
  const state = reducePlayer(locked, { type: "RESULT", payload: won });
  assert.deepEqual(state, { phase: "result", question: q1, selected: 2, answered: true, result: won });
});

test("RESULT with no Answer (timeout) shows nothing chosen", () => {
  const state = reducePlayer(asked, { type: "RESULT", payload: timedOut });
  assert.deepEqual(state, { phase: "result", question: q1, selected: null, answered: false, result: timedOut });
});

test("RESULT while waiting (page reopened mid-question) shows the outcome without a question", () => {
  const state = reducePlayer(WAITING, { type: "RESULT", payload: timedOut });
  assert.deepEqual(state, { phase: "result", question: null, selected: null, answered: false, result: timedOut });
});

test("the next QUESTION_START replaces the result without a tap", () => {
  const result = reducePlayer(locked, { type: "RESULT", payload: won });
  const q2 = { ...q1, index: 1, text: "When?" };
  assert.deepEqual(reducePlayer(result, { type: "QUESTION_START", payload: q2 }), { phase: "question", question: q2 });
});

test("GAME_OVER ends the Game with the final Score", () => {
  const result = reducePlayer(locked, { type: "RESULT", payload: won });
  const over = { score: 1500, rank: 3, podium: null };
  assert.deepEqual(reducePlayer(result, { type: "GAME_OVER", payload: over }), { phase: "over", gameOver: over });
});

test("BEST_SCORE after GAME_OVER adds the name and best Score today the phone claims the prize with", () => {
  const over = reducePlayer(WAITING, { type: "GAME_OVER", payload: { score: 1500, rank: 3, podium: null } });
  const best = { name: "Ada", score: 2100 };
  assert.deepEqual(reducePlayer(over, { type: "BEST_SCORE", payload: best }), { ...over, best });
});

test("BEST_SCORE before GAME_OVER is ignored: the server always sends it after", () => {
  assert.equal(reducePlayer(locked, { type: "BEST_SCORE", payload: { name: "Ada", score: 2100 } }), locked);
});

const roster = {
  pin: "123456",
  players: [
    { id: "p1", name: "Ada" },
    { id: "p2", name: "Bob" },
  ],
};

test("LOBBY_UPDATE shows who is in the lobby", () => {
  const state = reducePlayer(WAITING, { type: "LOBBY_UPDATE", payload: roster });
  assert.deepEqual(state, { phase: "lobby", players: roster.players });
});

test("the first QUESTION_START replaces the lobby", () => {
  const lobby = reducePlayer(WAITING, { type: "LOBBY_UPDATE", payload: roster });
  assert.deepEqual(reducePlayer(lobby, { type: "QUESTION_START", payload: q1 }), { phase: "question", question: q1 });
});

// --- Battle: the phone follows the big screen between questions (#9) ---

test("the reveal on the big screen leaves the personal result alone", () => {
  const result = reducePlayer(locked, { type: "RESULT", payload: won });
  assert.equal(reducePlayer(result, { type: "REVEAL", payload: { correctOption: 2, counts: [0, 1, 1, 0] } }), result);
});

test("LEADERBOARD sends the phone to the big screen until the next question", () => {
  const result = reducePlayer(locked, { type: "RESULT", payload: won });
  const standings = [{ playerId: "p1", name: "Ada", score: 1500, points: 870 }];
  assert.deepEqual(reducePlayer(result, { type: "LEADERBOARD", payload: { players: standings } }), {
    phase: "between",
  });
});

test("GAME_OVER in a Battle carries the Podium instead of a Score", () => {
  const podium = [
    { playerId: "p1", name: "Ada", score: 1500, points: 870 },
    { playerId: "p2", name: "Bob", score: 300, points: 0 },
  ];
  const over = { score: null, rank: null, podium };
  const between = reducePlayer(WAITING, { type: "LEADERBOARD", payload: { players: podium } });
  assert.deepEqual(reducePlayer(between, { type: "GAME_OVER", payload: over }), { phase: "over", gameOver: over });
});

// --- Reconnect (#11): a reopened page or a reconnected socket gets SYNC, then what it missed ---

const sync = (payload: Partial<Sync>): PlayerAction => ({
  type: "SYNC",
  payload: { status: "QUESTION", questionIndex: 0, answered: false, score: 0, streak: 0, ...payload },
});

test("SYNC with the open question puts it back up, on the original clock", () => {
  const state = reducePlayer(WAITING, sync({ question: q1, startedAt: q1.startedAt, timeLimitSec: 20 }));
  assert.deepEqual(state, { phase: "question", question: q1 });
});

test("SYNC after answering says Locked in, with no question to show again", () => {
  const state = reducePlayer(WAITING, sync({ answered: true, score: 630, streak: 1 }));
  assert.deepEqual(state, { phase: "locked", question: null, selected: null });
});

test("a RESULT after that SYNC reads as answered: a wrong Answer is Wrong, not Time's up", () => {
  const reopened = reducePlayer(WAITING, sync({ answered: true }));
  assert.deepEqual(reducePlayer(reopened, { type: "RESULT", payload: timedOut }), {
    phase: "result",
    question: null,
    selected: null,
    answered: true,
    result: timedOut,
  });
});

test("SYNC after answering on a page that never went away keeps the question and the choice", () => {
  assert.equal(reducePlayer(locked, sync({ answered: true })), locked);
});

test("SYNC on a question that closed unanswered waits for the timeout RESULT, not the stale question", () => {
  const state = reducePlayer(asked, sync({ status: "REVEAL" }));
  assert.deepEqual(state, WAITING);
  assert.deepEqual(reducePlayer(state, { type: "RESULT", payload: timedOut }), {
    phase: "result",
    question: null,
    selected: null,
    answered: false,
    result: timedOut,
  });
});

test("a socket blip on the result screen keeps it: SYNC and the replayed RESULT leave the question and the choice up", () => {
  const result = reducePlayer(locked, { type: "RESULT", payload: won });
  const synced = reducePlayer(result, sync({ status: "REVEAL", answered: true }));
  assert.equal(synced, result);
  assert.deepEqual(reducePlayer(synced, { type: "RESULT", payload: won }), result);
});

test("SYNC between questions sends the phone to the big screen", () => {
  assert.deepEqual(reducePlayer(WAITING, sync({ status: "LEADERBOARD" })), { phase: "between" });
});

test("SYNC at the end leaves the screen for the GAME_OVER that follows it", () => {
  const over = reducePlayer(WAITING, { type: "GAME_OVER", payload: { score: 1500, rank: 3, podium: null } });
  assert.equal(reducePlayer(over, sync({ status: "FINISHED" })), over);
});

test("an event from a newer server leaves the phone standing", () => {
  const unknown = { type: "FROM_THE_FUTURE", payload: {} } as unknown as PlayerAction;
  assert.equal(reducePlayer(locked, unknown), locked);
});
