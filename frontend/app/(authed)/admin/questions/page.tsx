"use client";

import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import {
  count,
  filterQuestions,
  ImportResult,
  importOutcome,
  LETTERS,
  NO_CATEGORY,
  OPTION_KEYS,
  Question,
  QuestionFilter,
} from "@/lib/admin";
import { api } from "@/lib/api";

/** What the last upload did: the server's per-row answer, or why the whole request failed. */
type ImportState = { file: string } & ({ result: ImportResult } | { failed: string });

const NO_FILTER: QuestionFilter = { query: "", category: "", status: "all" };

export default function QuestionBank() {
  const [questions, setQuestions] = useState<Question[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [filter, setFilter] = useState(NO_FILTER);
  const [editing, setEditing] = useState<Partial<Question> | null>(null); // null = dialog closed, {} = new
  const [deleting, setDeleting] = useState<Question | null>(null);
  const [busy, setBusy] = useState(false);
  const [lastImport, setLastImport] = useState<ImportState | null>(null);
  const [importing, setImporting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const dialog = useRef<HTMLDialogElement>(null);

  const reload = useCallback(
    () =>
      api<Question[]>("/api/admin/questions")
        .then(setQuestions)
        .catch((e: Error) => setError(e.message))
        .finally(() => setLoaded(true)),
    [],
  );

  useEffect(() => {
    reload();
  }, [reload]);

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
      ...Object.fromEntries(f), // text, optionA–D
      correctOption: Number(f.get("correctOption")),
      timeLimitSec: Number(f.get("timeLimitSec")),
      category: f.get("category") || null,
    });
    setBusy(true);
    const ok = await run(
      editing?.id
        ? api(`/api/admin/questions/${editing.id}`, { method: "PUT", body })
        : api("/api/admin/questions", { method: "POST", body }),
    );
    setBusy(false);
    if (ok) setEditing(null);
  }

  function setActive(q: Question, active: boolean) {
    return run(api(`/api/admin/questions/${q.id}`, { method: "PUT", body: JSON.stringify({ ...q, active }) }));
  }

  async function remove(q: Question) {
    setBusy(true);
    await run(api(`/api/admin/questions/${q.id}`, { method: "DELETE" }));
    setBusy(false);
    setDeleting(null);
  }

  async function importFile(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const body = new FormData(form);
    const file = (body.get("file") as File).name;
    setLastImport(null);
    setImporting(true);
    try {
      const result = await api<ImportResult>("/api/admin/questions/import", { method: "POST", body });
      setLastImport({ file, result });
      form.reset();
      await reload();
    } catch (err) {
      const status = (err as { status?: number }).status;
      setLastImport({
        file,
        // the import is one transaction: an error status means nothing was saved; no status means we can't know
        failed:
          status === undefined
            ? "Couldn't reach the server, so the import may not have run. Check the table before trying again."
            : `No questions were imported. ${(err as Error).message.replace(/[.\s]+$/, "")}.`,
      });
    }
    setImporting(false);
  }

  const shown = filterQuestions(questions, filter);
  const categories = [...new Set(questions.flatMap((q) => (q.category ? [q.category] : [])))].sort();

  const errorAlert = (
    <p role="alert" className="text-sm text-danger-fg">
      {error}
    </p>
  );

  return (
    <main className="flex flex-col gap-6 text-sm">
      <h1 className="text-2xl">Question Bank</h1>

      {error && !editing ? errorAlert : null}

      <section className="panel flex flex-col gap-3">
        <h2 className="text-lg">Import questions</h2>
        <form onSubmit={importFile} className="flex flex-wrap items-center gap-3">
          <label className="flex flex-wrap items-center gap-2">
            CSV or XLSX file
            <input name="file" type="file" accept=".csv,.xlsx" required className="text-marble/80" />
          </label>
          <button type="submit" disabled={importing} className="btn btn-secondary">
            {importing ? "Importing…" : "Import"}
          </button>
        </form>
        <p className="text-xs text-marble/60">
          Columns: text, a, b, c, d, correct (A–D), and optionally time_limit (seconds) and category. The first row is
          the header, columns in any order. Valid rows are added; rows with problems are listed so you can fix them.
        </p>
        {lastImport ? <ImportFeedback state={lastImport} /> : null}
      </section>

      <section className="flex flex-col gap-3">
        <div className="flex flex-wrap items-end gap-3">
          <label className="flex max-w-full flex-col gap-1">
            Search questions
            <input
              type="search"
              value={filter.query}
              onChange={(e) => setFilter({ ...filter, query: e.target.value })}
              placeholder="Question, answer or category"
              className="field w-72 max-w-full"
            />
          </label>
          <label className="flex flex-col gap-1">
            Category
            <select
              value={filter.category}
              onChange={(e) => setFilter({ ...filter, category: e.target.value })}
              className="field"
            >
              <option value="">All categories</option>
              {categories.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
              <option value={NO_CATEGORY}>No category</option>
            </select>
          </label>
          <label className="flex flex-col gap-1">
            Status
            <select
              value={filter.status}
              onChange={(e) => setFilter({ ...filter, status: e.target.value as QuestionFilter["status"] })}
              className="field"
            >
              <option value="all">Active and inactive</option>
              <option value="active">Active</option>
              <option value="inactive">Inactive</option>
            </select>
          </label>
          <p aria-live="polite" className="py-2 text-marble/60 tabular-nums">
            Showing {shown.length} of {questions.length}
          </p>
          <button type="button" onClick={() => setEditing({})} className="btn btn-primary ml-auto">
            Add question
          </button>
        </div>

        <div tabIndex={0} role="region" aria-label="Questions" className="overflow-x-auto">
          <table className="w-full text-left">
            <thead className="border-b border-marble/15 text-marble/60">
              <tr>
                <th className="p-2 font-normal">Question</th>
                <th className="p-2 font-normal">Correct answer</th>
                <th className="p-2 text-right font-normal">Time limit</th>
                <th className="p-2 font-normal">Category</th>
                <th className="p-2 font-normal">Status</th>
                <th className="p-2 font-normal">Actions</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((q) => (
                <tr key={q.id} className={`border-b border-marble/10 align-top ${q.active ? "" : "text-marble/60"}`}>
                  <td className="p-2">{q.text}</td>
                  <td className="p-2">
                    <span className="font-bold">{LETTERS[q.correctOption]}</span> {q[OPTION_KEYS[q.correctOption]]}
                  </td>
                  <td className="p-2 text-right whitespace-nowrap tabular-nums">{q.timeLimitSec} s</td>
                  <td className="p-2">{q.category ?? "—"}</td>
                  <td className="p-2">
                    <span className={`chip ${q.active ? "chip-on" : ""}`}>{q.active ? "Active" : "Inactive"}</span>
                  </td>
                  <td className="p-2 whitespace-nowrap">
                    <div className="flex gap-4">
                      <button
                        type="button"
                        onClick={() => setEditing(q)}
                        aria-label={`Edit: ${q.text}`}
                        className="link"
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        onClick={() => setActive(q, !q.active)}
                        aria-label={`${q.active ? "Deactivate" : "Activate"}: ${q.text}`}
                        className="link"
                      >
                        {q.active ? "Deactivate" : "Activate"}
                      </button>
                      <button
                        type="button"
                        onClick={() => setDeleting(q)}
                        aria-label={`Delete: ${q.text}`}
                        className="text-danger-fg underline underline-offset-4 transition-colors duration-150 hover:text-marble"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {shown.length === 0 ? (
                <tr>
                  <td colSpan={6} className="p-2 text-marble/60">
                    {!loaded ? (
                      "Loading questions…"
                    ) : questions.length > 0 ? (
                      <>
                        No questions match.{" "}
                        <button type="button" onClick={() => setFilter(NO_FILTER)} className="link">
                          Clear the search and filters
                        </button>
                      </>
                    ) : (
                      "No questions yet. Add one or import the client's sheet."
                    )}
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>

      <dialog
        ref={dialog}
        onClose={() => {
          setEditing(null);
          setError(null);
        }}
        className="panel m-auto w-[calc(100%-2rem)] max-w-lg text-marble backdrop:bg-black/60"
      >
        {editing ? (
          <form key={editing.id ?? "new"} onSubmit={save} className="flex flex-col gap-3 text-sm">
            <h2 className="text-lg">{editing.id ? "Edit question" : "New question"}</h2>
            <label className="flex flex-col gap-1">
              Question
              <textarea name="text" required defaultValue={editing.text} className="field" />
            </label>
            {OPTION_KEYS.map((key, i) => (
              <label key={key} className="flex flex-col gap-1">
                Option {LETTERS[i]}
                <input name={key} required defaultValue={editing[key]} className="field" />
              </label>
            ))}
            <label className="flex flex-col gap-1">
              Correct answer
              <select name="correctOption" defaultValue={editing.correctOption ?? 0} className="field">
                {LETTERS.map((letter, i) => (
                  <option key={letter} value={i}>
                    {letter}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex flex-col gap-1">
              Time limit (seconds, 5–120)
              <input
                name="timeLimitSec"
                type="number"
                min={5}
                max={120}
                required
                defaultValue={editing.timeLimitSec ?? 20}
                className="field"
              />
            </label>
            <label className="flex flex-col gap-1">
              Category (optional)
              <input name="category" defaultValue={editing.category ?? ""} className="field" />
            </label>
            {error ? errorAlert : null}
            <div className="flex flex-wrap justify-end gap-3">
              <button type="button" onClick={() => setEditing(null)} className="btn btn-secondary">
                Cancel
              </button>
              <button type="submit" disabled={busy} className="btn btn-primary">
                {busy ? "Saving…" : "Save"}
              </button>
            </div>
          </form>
        ) : null}
      </dialog>

      <ConfirmDialog
        open={deleting !== null}
        title="Delete this question?"
        confirmLabel="Delete question"
        busyLabel="Deleting…"
        busy={busy}
        onConfirm={() => remove(deleting!)}
        onCancel={() => setDeleting(null)}
      >
        <p className="text-marble">“{deleting?.text}”</p>
        <p>
          If a Game has already used it, it can&apos;t be deleted, so it&apos;s made inactive instead and stays in the
          table.
        </p>
      </ConfirmDialog>
    </main>
  );
}

function ImportFeedback({ state }: { state: ImportState }) {
  if ("failed" in state) {
    return (
      <div role="alert" className="flex flex-col gap-1 text-danger-fg">
        <p>Import of {state.file} failed.</p>
        <p>{state.failed}</p>
      </div>
    );
  }
  const { imported, errors } = state.result;
  const outcome = importOutcome(state.result);
  return (
    <div role={outcome === "failed" ? "alert" : "status"} className="flex flex-col gap-1">
      {outcome === "success" ? (
        <p className="text-success-fg">
          ✓ Imported {count(imported, "question")} from {state.file}.
        </p>
      ) : outcome === "partial" ? (
        <p>
          <span className="text-success-fg">
            Imported {count(imported, "question")} from {state.file}.
          </span>{" "}
          {count(errors.length, "row")}{" "}
          {errors.length === 1 ? "was skipped and needs fixing:" : "were skipped and need fixing:"}
        </p>
      ) : (
        <p className="text-danger-fg">
          Import of {state.file} failed. No questions were imported.{" "}
          {errors.length > 0 ? `${count(errors.length, "row")} had problems:` : "The file has a header but no rows."}
        </p>
      )}
      {errors.length > 0 ? (
        <ul className="list-disc pl-6 text-danger-fg">
          {errors.map((err) => (
            <li key={err.row}>
              Row {err.row}: {err.message}
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
