import assert from "node:assert/strict";
import { test } from "node:test";
import type { Question } from "./admin.ts";
import { count, filterQuestions, importOutcome, NO_CATEGORY } from "./admin.ts";

const q = (id: number, text: string, category: string | null, active: boolean): Question => ({
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
  q(1, "Coronation year?", "History", true),
  q(2, "Corgi name?", null, false),
  q(3, "Horse?", "Sport", true),
];
const ids = (qs: Question[]) => qs.map((x) => x.id);
const all = { query: "", category: "", status: "all" } as const;

test("no filter keeps every question", () => {
  assert.deepEqual(ids(filterQuestions(bank, all)), [1, 2, 3]);
});

test("search matches question text, options and category, ignoring case", () => {
  assert.deepEqual(ids(filterQuestions(bank, { ...all, query: "  COR " })), [1, 2]);
  assert.deepEqual(ids(filterQuestions(bank, { ...all, query: "a3" })), [3]);
  assert.deepEqual(ids(filterQuestions(bank, { ...all, query: "history" })), [1]);
  assert.deepEqual(ids(filterQuestions(bank, { ...all, query: "balmoral" })), [1, 2, 3]);
});

test("category picks that category, or the questions without one", () => {
  assert.deepEqual(ids(filterQuestions(bank, { ...all, category: "Sport" })), [3]);
  assert.deepEqual(ids(filterQuestions(bank, { ...all, category: NO_CATEGORY })), [2]);
});

test("status splits active from inactive, and combines with search and category", () => {
  assert.deepEqual(ids(filterQuestions(bank, { ...all, status: "active" })), [1, 3]);
  assert.deepEqual(ids(filterQuestions(bank, { ...all, status: "inactive" })), [2]);
  assert.deepEqual(ids(filterQuestions(bank, { query: "cor", category: "", status: "active" })), [1]);
  assert.deepEqual(ids(filterQuestions(bank, { query: "", category: "History", status: "inactive" })), []);
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
