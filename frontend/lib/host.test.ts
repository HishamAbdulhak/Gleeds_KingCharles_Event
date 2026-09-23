import assert from "node:assert/strict";
import { test } from "node:test";
import type { QuestionStart, Standing } from "./game.ts";
import { LOBBY, reduceHost } from "./host.ts";

const lobby = {
  pin: "123456",
  players: [
    { id: "p1", name: "Ada" },
    { id: "p2", name: "Bob" },
  ],
};

const q1: QuestionStart = {
  index: 0,
  total: 3,
  text: "Who?",
  options: ["A", "B", "C", "D"],
  timeLimitSec: 20,
  startedAt: "2026-09-21T10:00:00Z",
};

const standings: Standing[] = [
  { playerId: "p1", name: "Ada", score: 1500, points: 870 },
  { playerId: "p2", name: "Bob", score: 300, points: 0 },
];

const asked = reduceHost(LOBBY, { type: "QUESTION_START", payload: q1 });

test("LOBBY_UPDATE shows the PIN and who has joined", () => {
  const screen = reduceHost(LOBBY, { type: "LOBBY_UPDATE", payload: lobby });
  assert.deepEqual(screen, { phase: "lobby", pin: lobby.pin, players: lobby.players });
});

test("QUESTION_START puts the question up with nobody answered yet", () => {
  assert.deepEqual(asked, { phase: "question", question: q1, roster: [] });
});

test("HOST_STATE names who is in and what they have scored", () => {
  const roster = [
    { id: "p1", name: "Ada", answered: true, score: 1500 },
    { id: "p2", name: "Bob", answered: false, score: 300 },
  ];
  assert.deepEqual(reduceHost(asked, { type: "HOST_STATE", payload: { players: roster } }), { ...asked, roster });
});

test("REVEAL keeps the question on screen beside the answer", () => {
  const reveal = { correctOption: 2, counts: [0, 1, 1, 0] };
  assert.deepEqual(reduceHost(asked, { type: "REVEAL", payload: reveal }), { phase: "reveal", question: q1, reveal });
});

test("LEADERBOARD shows the Standings between questions", () => {
  const reveal = reduceHost(asked, { type: "REVEAL", payload: { correctOption: 2, counts: [0, 1, 1, 0] } });
  assert.deepEqual(reduceHost(reveal, { type: "LEADERBOARD", payload: { players: standings } }), {
    phase: "standings",
    standings,
  });
});

test("GAME_OVER shows the Podium", () => {
  const over = { score: null, rank: null, podium: standings };
  assert.deepEqual(reduceHost(asked, { type: "GAME_OVER", payload: over }), { phase: "podium", podium: standings });
});

test("a Player's own ack and result are not the big screen's", () => {
  assert.equal(reduceHost(asked, { type: "ANSWER_ACK", payload: { accepted: true, reason: null } }), asked);
  assert.equal(
    reduceHost(asked, { type: "RESULT", payload: { correct: true, points: 1, streak: 1, score: 1, correctOption: 0 } }),
    asked,
  );
});

test("a reloaded big screen's SYNC puts the open question back up; HOST_STATE fills the roster", () => {
  const sync = { status: "QUESTION", questionIndex: 0, question: q1, answered: false, score: 0, streak: 0 } as const;
  assert.deepEqual(reduceHost(LOBBY, { type: "SYNC", payload: sync }), asked);
});

test("a SYNC with no question leaves the screen to the events replayed after it", () => {
  const sync = { status: "REVEAL", questionIndex: 0, answered: false, score: 0, streak: 0 } as const;
  assert.equal(reduceHost(LOBBY, { type: "SYNC", payload: sync }), LOBBY);
});

test("an event from a newer server leaves the screen standing", () => {
  // version skew: the deployed backend publishes something this build has never heard of
  const unknown = { type: "FROM_THE_FUTURE", payload: {} } as unknown as Parameters<typeof reduceHost>[1];
  assert.equal(reduceHost(asked, unknown), asked);
});
