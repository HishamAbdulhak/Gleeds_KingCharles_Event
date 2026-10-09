/** The Admin's view of the Question Bank: the shapes `/api/admin/questions` speaks, and the table's filtering. */

export type Question = {
  id: number;
  text: string;
  optionA: string;
  optionB: string;
  optionC: string;
  optionD: string;
  correctOption: number;
  timeLimitSec: number;
  category: string | null;
  active: boolean;
};

export type ImportResult = { imported: number; errors: { row: number; message: string }[] };

export const LETTERS = ["A", "B", "C", "D"] as const;
export const OPTION_KEYS = ["optionA", "optionB", "optionC", "optionD"] as const;

/** `category`: "" for any, NO_CATEGORY for questions without one, else that category exactly. */
export type QuestionFilter = { query: string; category: string; status: "all" | "active" | "inactive" };
export const NO_CATEGORY = "\0none";

export function filterQuestions(questions: Question[], { query, category, status }: QuestionFilter) {
  const needle = query.trim().toLowerCase();
  return questions.filter(
    (q) =>
      (status === "all" || q.active === (status === "active")) &&
      (category === "" || (q.category ?? NO_CATEGORY) === category) &&
      (needle === "" ||
        [q.text, ...OPTION_KEYS.map((k) => q[k]), q.category ?? ""].some((s) => s.toLowerCase().includes(needle))),
  );
}

/** A 200 import: every row in, some rows skipped, or nothing in at all. */
export const importOutcome = ({ imported, errors }: ImportResult) =>
  imported === 0 ? "failed" : errors.length > 0 ? "partial" : "success";

/** "1 question", "12 questions". */
export const count = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;
