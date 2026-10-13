"use client";

import { FormEvent, useEffect, useState } from "react";
import { CATEGORIES, shortCategories, shortfall, count, Mix, perGameTotal, Question } from "@/lib/admin";
import { api } from "@/lib/api";

type Draft = Record<keyof Mix, string>;

const isCount = (value: string) => value.trim() !== "" && Number.isInteger(Number(value)) && Number(value) >= 0;

/** Spec story 42 and docs/adr/0007: each Game's Category mix. (Reset, stories 43–44, is on the dashboard beside the board it empties.) */
export default function SettingsPage() {
  const [saved, setSaved] = useState<Mix | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [questions, setQuestions] = useState<Question[] | null>(null);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([api<Mix>("/api/admin/settings"), api<Question[]>("/api/admin/questions")])
      .then(([mix, questions]) => {
        setSaved(mix);
        setDraft(Object.fromEntries(CATEGORIES.map((c) => [c.key, String(mix[c.key])])) as Draft);
        setQuestions(questions);
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  // the server's rules (SettingsController): each a whole number ≥ 0, and a Game of at least one question
  const allCounts = draft !== null && CATEGORIES.every((c) => isCount(draft[c.key]));
  const mix = allCounts ? (Object.fromEntries(CATEGORIES.map((c) => [c.key, Number(draft[c.key])])) as Mix) : null;
  const total = mix ? perGameTotal(mix) : null;
  const valid = total !== null && total >= 1;
  const unchanged = mix !== null && saved !== null && CATEGORIES.every((c) => mix[c.key] === saved[c.key]);
  const short = mix && questions ? shortCategories(questions, mix) : [];

  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!valid || !mix) return;
    setSaving(true);
    setError(null);
    try {
      await api("/api/admin/settings", { method: "PUT", body: JSON.stringify(mix) });
      setSaved(mix);
      setNotice(`✓ Saved. New Games draw ${count(perGameTotal(mix), "question")}.`);
    } catch (err) {
      setError((err as Error).message);
    }
    setSaving(false);
  }

  return (
    <main className="flex flex-col gap-6">
      <h1 className="text-2xl">Settings</h1>

      {error ? (
        <p role="alert" className="text-sm text-danger-fg">
          {error}
        </p>
      ) : null}

      {draft !== null ? (
        <form onSubmit={save} noValidate className="panel flex max-w-xl flex-col gap-3 text-sm">
          <h2 className="text-lg">Games</h2>
          <p className="text-marble/80">How many questions of each category every Game draws, Solo and Battle alike.</p>
          {CATEGORIES.map((c) => {
            const ok = isCount(draft[c.key]);
            const shortHere = short.find((s) => s.name === c.name);
            return (
              <div key={c.key} className="flex flex-col gap-1">
                <div className="flex items-center gap-3">
                  <label htmlFor={c.key} className="w-32">
                    {c.name}
                  </label>
                  <input
                    id={c.key}
                    type="number"
                    min={0}
                    value={draft[c.key]}
                    onChange={(e) => {
                      setDraft({ ...draft, [c.key]: e.target.value });
                      setNotice(null);
                    }}
                    aria-invalid={!ok}
                    aria-describedby={`${c.key}-hint`}
                    className="field w-24 tabular-nums"
                  />
                </div>
                <div id={`${c.key}-hint`} aria-live="polite">
                  {!ok ? (
                    <p className="text-danger-fg">Enter a whole number, 0 or more.</p>
                  ) : shortHere ? (
                    <p className="text-danger-fg">{shortfall(shortHere)} Activate or add more first.</p>
                  ) : null}
                </div>
              </div>
            );
          })}
          <p aria-live="polite" className="tabular-nums">
            {total === null ? null : total >= 1 ? (
              <>Total: {count(total, "question")} per Game</>
            ) : (
              <span className="text-danger-fg">A Game needs at least one question.</span>
            )}
          </p>
          <div className="flex items-center gap-3">
            <button type="submit" disabled={!valid || unchanged || saving} className="btn btn-primary">
              {saving ? "Saving…" : "Save"}
            </button>
            <p className="text-xs text-marble/60">A Game already running keeps the questions it drew.</p>
          </div>
          {notice ? (
            <p role="status" className="text-success-fg">
              {notice}
            </p>
          ) : null}
        </form>
      ) : error ? null : (
        <p className="text-sm text-marble/60">Loading…</p>
      )}
    </main>
  );
}
