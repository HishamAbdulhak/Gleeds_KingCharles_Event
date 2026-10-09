"use client";

import { FormEvent, useEffect, useState } from "react";
import { count, Question } from "@/lib/admin";
import { api } from "@/lib/api";

// the server's bounds (SettingsController: @Min(1) @Max(50))
const MIN = 1;
const MAX = 50;

/** Spec story 42: the Question Set size. (Reset, stories 43–44, is on the dashboard beside the board it empties.) */
export default function SettingsPage() {
  const [saved, setSaved] = useState<number | null>(null);
  const [value, setValue] = useState("");
  const [active, setActive] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([api<{ questionsPerGame: number }>("/api/admin/settings"), api<Question[]>("/api/admin/questions")])
      .then(([{ questionsPerGame }, questions]) => {
        setSaved(questionsPerGame);
        setValue(String(questionsPerGame));
        setActive(questions.filter((q) => q.active).length);
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  const perGame = Number(value);
  const valid = value.trim() !== "" && Number.isInteger(perGame) && perGame >= MIN && perGame <= MAX;

  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!valid) return;
    setSaving(true);
    setError(null);
    try {
      await api("/api/admin/settings", { method: "PUT", body: JSON.stringify({ questionsPerGame: perGame }) });
      setSaved(perGame);
      setNotice(`✓ Saved. New Games draw ${count(perGame, "question")}.`);
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

      {saved !== null ? (
        <form onSubmit={save} noValidate className="panel flex max-w-xl flex-col gap-3 text-sm">
          <h2 className="text-lg">Games</h2>
          <label htmlFor="questionsPerGame">Questions per Game</label>
          <div className="flex items-start gap-3">
            <input
              id="questionsPerGame"
              type="number"
              min={MIN}
              max={MAX}
              value={value}
              onChange={(e) => {
                setValue(e.target.value);
                setNotice(null);
              }}
              aria-invalid={!valid}
              aria-describedby="questionsPerGame-hint"
              className="field w-24 tabular-nums"
            />
            <button type="submit" disabled={!valid || perGame === saved || saving} className="btn btn-primary">
              {saving ? "Saving…" : "Save"}
            </button>
          </div>
          <div id="questionsPerGame-hint" aria-live="polite" className="flex flex-col gap-1">
            {valid ? null : (
              <p className="text-danger-fg">
                Enter a whole number from {MIN} to {MAX}.
              </p>
            )}
            {valid && active !== null && perGame > active ? (
              <p className="text-danger-fg">
                Only {count(active, "question")} {active === 1 ? "is" : "are"} active. A Game with {perGame} can&apos;t
                start until you activate more.
              </p>
            ) : null}
            <p className="text-xs text-marble/60">A Game already running keeps the questions it drew.</p>
          </div>
          {notice ? (
            <p role="status" className="text-success-fg">
              {notice}
            </p>
          ) : null}
        </form>
      ) : null}
    </main>
  );
}
