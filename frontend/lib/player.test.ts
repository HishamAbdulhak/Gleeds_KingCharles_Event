import assert from "node:assert/strict";
import { test } from "node:test";
import type { QuestionStart } from "./game.ts";
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
  assert.deepEqual(state, { phase: "result", question: q1, selected: 2, result: won });
});

test("RESULT with no Answer (timeout) shows nothing chosen", () => {
  const state = reducePlayer(asked, { type: "RESULT", payload: timedOut });
  assert.deepEqual(state, { phase: "result", question: q1, selected: null, result: timedOut });
});

test("RESULT while waiting (page reopened mid-question) shows the outcome without a question", () => {
  const state = reducePlayer(WAITING, { type: "RESULT", payload: timedOut });
  assert.deepEqual(state, { phase: "result", question: null, selected: null, result: timedOut });
});

test("the next QUESTION_START replaces the result without a tap", () => {
  const result = reducePlayer(locked, { type: "RESULT", payload: won });
  const q2 = { ...q1, index: 1, text: "When?" };
  assert.deepEqual(reducePlayer(result, { type: "QUESTION_START", payload: q2 }), { phase: "question", question: q2 });
});

test("GAME_OVER ends the Game with the final Score", () => {
  const result = reducePlayer(locked, { type: "RESULT", payload: won });
  const over = { score: 1500, rank: 3 };
  assert.deepEqual(reducePlayer(result, { type: "GAME_OVER", payload: over }), { phase: "over", gameOver: over });
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
