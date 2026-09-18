"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { api, getToken } from "@/lib/api";

type Question = {
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

type ImportResult = { imported: number; errors: { row: number; message: string }[] };

const LETTERS = ["A", "B", "C", "D"] as const;
const OPTION_KEYS = ["optionA", "optionB", "optionC", "optionD"] as const;

const inputClass = "rounded bg-cream p-2 text-royal-900";
const buttonClass = "rounded bg-gold-500 px-3 py-1 font-semibold text-royal-900 disabled:opacity-50";
const linkButtonClass = "text-gold-300 underline";

export default function QuestionBank() {
  const router = useRouter();
  const [questions, setQuestions] = useState<Question[]>([]);
  const [editing, setEditing] = useState<Partial<Question> | null>(null); // null = dialog closed, {} = new
  const [importResult, setImportResult] = useState<ImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const dialog = useRef<HTMLDialogElement>(null);

  const reload = useCallback(
    () => api<Question[]>("/api/admin/questions").then(setQuestions).catch((e: Error) => setError(e.message)),
    [],
  );

  useEffect(() => {
    if (!getToken()) {
      router.replace("/admin/login");
      return;
    }
    reload();
  }, [router, reload]);

  useEffect(() => {
    if (editing) dialog.current?.showModal();
    else dialog.current?.close();
  }, [editing]);

  /** Runs one API call, then reloads the table. Returns false (and shows the message) on failure. */
  async function run(action: Promise<unknown>) {
    setError(null);
    try {
      await action;
      await reload();
      return true;
    } catch (e) {
      setError((e as Error).message);
      return false;
    }
  }

  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const body = JSON.stringify({
      text: f.get("text"),
      optionA: f.get("optionA"),
      optionB: f.get("optionB"),
      optionC: f.get("optionC"),
      optionD: f.get("optionD"),
      correctOption: Number(f.get("correctOption")),
      timeLimitSec: Number(f.get("timeLimitSec")),
      category: f.get("category") || null,
      active: editing?.active ?? true,
    });
    const ok = await run(
      editing?.id
        ? api(`/api/admin/questions/${editing.id}`, { method: "PUT", body })
        : api("/api/admin/questions", { method: "POST", body }),
    );
    if (ok) setEditing(null);
  }

  function setActive(q: Question, active: boolean) {
    return run(api(`/api/admin/questions/${q.id}`, { method: "PUT", body: JSON.stringify({ ...q, active }) }));
  }

  function remove(q: Question) {
    if (confirm(`Delete "${q.text}"?\nIf it has already been played in a Game it will be deactivated instead.`)) {
      run(api(`/api/admin/questions/${q.id}`, { method: "DELETE" }));
    }
  }

  async function importFile(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    setImportResult(null);
    const ok = await run(
      api<ImportResult>("/api/admin/questions/import", { method: "POST", body: new FormData(form) }).then(setImportResult),
    );
    if (ok) form.reset();
  }

  const errorAlert = (
    <p role="alert" className="text-sm text-red-300">
      {error}
    </p>
  );

  return (
    <main className="flex flex-1 flex-col gap-6 p-8">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gold-500">Question Bank</h1>
        <Link href="/admin" className="text-sm text-cream/80 underline">
          Admin home
        </Link>
      </header>

      {error && !editing ? errorAlert : null}

      <section className="flex flex-col gap-2">
        <form onSubmit={importFile} className="flex flex-wrap items-center gap-3">
          <label className="text-sm">
            Import CSV or XLSX{" "}
            <input name="file" type="file" accept=".csv,.xlsx" required className="text-sm text-cream/80" />
          </label>
          <button type="submit" className={buttonClass}>
            Upload
          </button>
          <span className="text-xs text-cream/60">Columns: text, a, b, c, d, correct (A–D), time_limit, category</span>
        </form>
        {importResult ? (
          <div className="text-sm">
            <p>
              Imported {importResult.imported} question{importResult.imported === 1 ? "" : "s"}.
              {importResult.errors.length > 0 ? ` ${importResult.errors.length} row(s) skipped:` : ""}
            </p>
            {importResult.errors.length > 0 ? (
              <ul className="list-disc pl-6 text-red-300">
                {importResult.errors.map((err) => (
                  <li key={err.row}>
                    Row {err.row}: {err.message}
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        ) : null}
      </section>

      <div>
        <button type="button" onClick={() => setEditing({})} className={buttonClass}>
          Add question
        </button>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="text-gold-300">
            <tr>
              <th className="p-2">Text</th>
              <th className="p-2">Correct answer</th>
              <th className="p-2">Time limit</th>
              <th className="p-2">Category</th>
              <th className="p-2">Active</th>
              <th className="p-2"></th>
            </tr>
          </thead>
          <tbody>
            {questions.map((q) => (
              <tr key={q.id} className={`border-t border-cream/10 ${q.active ? "" : "text-cream/50"}`}>
                <td className="p-2">{q.text}</td>
                <td className="p-2">
                  {LETTERS[q.correctOption]}: {q[OPTION_KEYS[q.correctOption]]}
                </td>
                <td className="p-2">{q.timeLimitSec} s</td>
                <td className="p-2">{q.category ?? "—"}</td>
                <td className="p-2">{q.active ? "Yes" : "No"}</td>
                <td className="flex gap-3 p-2 whitespace-nowrap">
                  <button type="button" onClick={() => setEditing(q)} className={linkButtonClass}>
                    Edit
                  </button>
                  <button type="button" onClick={() => setActive(q, !q.active)} className={linkButtonClass}>
                    {q.active ? "Deactivate" : "Activate"}
                  </button>
                  <button type="button" onClick={() => remove(q)} className="text-red-300 underline">
                    Delete
                  </button>
                </td>
              </tr>
            ))}
            {questions.length === 0 ? (
              <tr>
                <td colSpan={6} className="p-2 text-cream/60">
                  No questions yet. Add one or upload the client&apos;s sheet.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>

      <dialog
        ref={dialog}
        onClose={() => {
          setEditing(null);
          setError(null);
        }}
        className="m-auto w-full max-w-lg rounded bg-royal-900 p-6 text-cream backdrop:bg-black/60"
      >
        {editing ? (
          <form key={editing.id ?? "new"} onSubmit={save} className="flex flex-col gap-3">
            <h2 className="text-xl font-bold text-gold-500">{editing.id ? "Edit question" : "New question"}</h2>
            <label className="flex flex-col gap-1 text-sm">
              Question
              <textarea name="text" required defaultValue={editing.text} className={inputClass} />
            </label>
            {OPTION_KEYS.map((key, i) => (
              <label key={key} className="flex flex-col gap-1 text-sm">
                Option {LETTERS[i]}
                <input name={key} required defaultValue={editing[key]} className={inputClass} />
              </label>
            ))}
            <label className="flex flex-col gap-1 text-sm">
              Correct answer
              <select name="correctOption" defaultValue={editing.correctOption ?? 0} className={inputClass}>
                {LETTERS.map((letter, i) => (
                  <option key={letter} value={i}>
                    {letter}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex flex-col gap-1 text-sm">
              Time limit (seconds, 5–120)
              <input
                name="timeLimitSec"
                type="number"
                min={5}
                max={120}
                required
                defaultValue={editing.timeLimitSec ?? 20}
                className={inputClass}
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              Category (optional)
              <input name="category" defaultValue={editing.category ?? ""} className={inputClass} />
            </label>
            {error ? errorAlert : null}
            <div className="flex justify-end gap-3">
              <button type="button" onClick={() => setEditing(null)} className="text-cream/80 underline">
                Cancel
              </button>
              <button type="submit" className={buttonClass}>
                Save
              </button>
            </div>
          </form>
        ) : null}
      </dialog>
    </main>
  );
}
