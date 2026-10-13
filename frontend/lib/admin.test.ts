import assert from "node:assert/strict";
import { test } from "node:test";
import type { Category, Question } from "./admin.ts";
import {
  count,
  describeMix,
  filterQuestions,
  importOutcome,
  perGameTotal,
  shortCategories,
  shortfall,
} from "./admin.ts";

const q = (id: number, text: string, category: Category, active: boolean): Question => ({
  id,
  text,
  optionA: `a${id}`,
  optionB: "Balmoral",
  optionC: "c",
  optionD: "d",
  correctOption: 0,
  timeLimitSec: 20,
  category,
  active,
});

const bank = [
  q(1, "Coronation year?", "King Charles", true),
  q(2, "Corgi name?", "Gleeds", false),
  q(3, "Horse?", "UK–Saudi", true),
];
const ids = (qs: Question[]) => qs.map((x) => x.id);
const all = { query: "", category: "", status: "all" } as const;

test("no filter keeps every question", () => {
  assert.deepEqual(ids(filterQuestions(bank, all)), [1, 2, 3]);
});

test("search matches question text, options and category, ignoring case", () => {
  assert.deepEqual(ids(filterQuestions(bank, { ...all, query: "  COR " })), [1, 2]);
  assert.deepEqual(ids(filterQuestions(bank, { ...all, query: "a3" })), [3]);
  assert.deepEqual(ids(filterQuestions(bank, { ...all, query: "king" })), [1]);
  assert.deepEqual(ids(filterQuestions(bank, { ...all, query: "balmoral" })), [1, 2, 3]);
});

test("category picks that category", () => {
  assert.deepEqual(ids(filterQuestions(bank, { ...all, category: "UK–Saudi" })), [3]);
  assert.deepEqual(ids(filterQuestions(bank, { ...all, category: "Gleeds" })), [2]);
});

test("status splits active from inactive, and combines with search and category", () => {
  assert.deepEqual(ids(filterQuestions(bank, { ...all, status: "active" })), [1, 3]);
  assert.deepEqual(ids(filterQuestions(bank, { ...all, status: "inactive" })), [2]);
  assert.deepEqual(ids(filterQuestions(bank, { query: "cor", category: "", status: "active" })), [1]);
  assert.deepEqual(ids(filterQuestions(bank, { query: "", category: "King Charles", status: "inactive" })), []);
});

test("an import is a success, partial or failed by what got in", () => {
  assert.equal(importOutcome({ imported: 3, errors: [] }), "success");
  assert.equal(importOutcome({ imported: 3, errors: [{ row: 4, message: "x" }] }), "partial");
  assert.equal(importOutcome({ imported: 0, errors: [{ row: 2, message: "x" }] }), "failed");
  assert.equal(importOutcome({ imported: 0, errors: [] }), "failed");
});

test("counts read as words do", () => {
  assert.equal(count(1, "question"), "1 question");
  assert.equal(count(0, "row"), "0 rows");
  assert.equal(count(12, "question"), "12 questions");
});

const mix = { kingCharlesPerGame: 4, ukSaudiPerGame: 3, gleedsPerGame: 3 };

test("a Game is the sum of its mix, described in Category order", () => {
  assert.equal(perGameTotal(mix), 10);
  assert.equal(perGameTotal({ kingCharlesPerGame: 0, ukSaudiPerGame: 0, gleedsPerGame: 1 }), 1);
  assert.equal(describeMix(mix), "4 King Charles · 3 UK–Saudi · 3 Gleeds");
});

test("a Category is short when its active questions are fewer than its per-Game number", () => {
  const bank4 = [...bank, q(4, "Treaty?", "UK–Saudi", true), q(5, "Founded?", "Gleeds", true)];
  // active: King Charles 1, UK–Saudi 2, Gleeds 1 (the inactive Gleeds question doesn't count)
  assert.deepEqual(shortCategories(bank4, { kingCharlesPerGame: 1, ukSaudiPerGame: 3, gleedsPerGame: 2 }), [
    { name: "UK–Saudi", active: 2, needed: 3 },
    { name: "Gleeds", active: 1, needed: 2 },
  ]);
  assert.deepEqual(shortCategories(bank4, { kingCharlesPerGame: 1, ukSaudiPerGame: 2, gleedsPerGame: 0 }), []);
  assert.equal(shortCategories([], mix).length, 3);
  assert.equal(
    shortfall({ name: "Gleeds", active: 2, needed: 3 }),
    "A Game needs 3 active Gleeds questions; the Bank has 2.",
  );
});
