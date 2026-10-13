/** The Admin's view of the Question Bank: the shapes `/api/admin/questions` and `/settings` speak, and the table's filtering. */

/** Every question has one of these (backend `Category.java`); `key` is its per-Game number in the settings (docs/adr/0007). */
export const CATEGORIES = [
  { name: "King Charles", key: "kingCharlesPerGame" },
  { name: "UK–Saudi", key: "ukSaudiPerGame" },
  { name: "Gleeds", key: "gleedsPerGame" },
] as const;

export type Category = (typeof CATEGORIES)[number]["name"];

/** How many questions of each Category every Game draws: `/api/admin/settings`. */
export type Mix = Record<(typeof CATEGORIES)[number]["key"], number>;

export type Question = {
  id: number;
  text: string;
  optionA: string;
  optionB: string;
  optionC: string;
  optionD: string;
  correctOption: number;
  timeLimitSec: number;
  category: Category;
  active: boolean;
};

export type ImportResult = { imported: number; errors: { row: number; message: string }[] };

export const LETTERS = ["A", "B", "C", "D"] as const;
export const OPTION_KEYS = ["optionA", "optionB", "optionC", "optionD"] as const;

/** `category`: "" for any, else that Category. */
export type QuestionFilter = { query: string; category: Category | ""; status: "all" | "active" | "inactive" };

export function filterQuestions(questions: Question[], { query, category, status }: QuestionFilter) {
  const needle = query.trim().toLowerCase();
  return questions.filter(
    (q) =>
      (status === "all" || q.active === (status === "active")) &&
      (category === "" || q.category === category) &&
      (needle === "" ||
        [q.text, ...OPTION_KEYS.map((k) => q[k]), q.category].some((s) => s.toLowerCase().includes(needle))),
  );
}

/** A 200 import: every row in, some rows skipped, or nothing in at all. */
export const importOutcome = ({ imported, errors }: ImportResult) =>
  imported === 0 ? "failed" : errors.length > 0 ? "partial" : "success";

/** "1 question", "12 questions". */
export const count = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;

/** Questions per Game: the mix's sum. */
export const perGameTotal = (mix: Mix) => CATEGORIES.reduce((sum, c) => sum + mix[c.key], 0);

/** Each Category with its active questions and its per-Game number, in CATEGORIES order. */
export function categoryCounts(questions: Question[], mix: Mix) {
  return CATEGORIES.map(({ name, key }) => ({
    name,
    active: questions.filter((q) => q.active && q.category === name).length,
    needed: mix[key],
  }));
}

/** The Categories a Game can't be drawn from: the server's own rule (QuestionBank.draw answers 409). */
export const shortCategories = (questions: Question[], mix: Mix) =>
  categoryCounts(questions, mix).filter((c) => c.active < c.needed);

/** One short Category in the server's own words (QuestionBank.draw's 409). */
export const shortfall = (c: { name: Category; active: number; needed: number }) =>
  `A Game needs ${count(c.needed, `active ${c.name} question`)}; the Bank has ${c.active}.`;

/** "4 King Charles · 3 UK–Saudi · 3 Gleeds". */
export const describeMix = (mix: Mix) => CATEGORIES.map((c) => `${mix[c.key]} ${c.name}`).join(" · ");
