"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { api } from "@/lib/api";

type Settings = { questionsPerGame: number };

/** Spec stories 42–44: the Question Set size, and the Reset that empties the Day Leaderboard before doors open. */
export default function SettingsPage() {
  const [settings, setSettings] = useState<Settings | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<Settings>("/api/admin/settings")
      .then(setSettings)
      .catch((e: Error) => setError(e.message));
  }, []);

  /** Runs one API call and shows `done` on success, or the server's message on failure. */
  async function run(action: Promise<unknown>, done: string) {
    setError(null);
    setNotice(null);
    try {
      await action;
      setNotice(done);
    } catch (e) {
      setError((e as Error).message);
    }
  }

  function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const questionsPerGame = Number(new FormData(e.currentTarget).get("questionsPerGame"));
    run(
      api("/api/admin/settings", { method: "PUT", body: JSON.stringify({ questionsPerGame }) }),
      `New Games will draw ${questionsPerGame} questions.`,
    );
  }

  function reset() {
    if (confirm("Reset the Day Leaderboard?\nIt starts empty; every past Game is kept, it just stops counting.")) {
      run(api("/api/admin/reset", { method: "POST" }), "The Day Leaderboard is empty.");
    }
  }

  return (
    <main className="flex flex-1 flex-col gap-8 p-8">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Settings</h1>
        <Link href="/admin" className="text-sm text-marble/80 underline">
          Admin home
        </Link>
      </header>

      {error ? (
        <p role="alert" className="text-sm text-danger-fg">
          {error}
        </p>
      ) : null}
      {notice ? (
        <p role="status" className="text-sm text-success-fg">
          {notice}
        </p>
      ) : null}

      {settings ? (
        <section className="flex flex-col gap-1">
          <form onSubmit={save} className="flex items-end gap-3">
            <label className="flex flex-col gap-1 text-sm">
              Questions per Game
              <input
                name="questionsPerGame"
                type="number"
                min={1}
                max={50}
                required
                defaultValue={settings.questionsPerGame}
                className="field w-24"
              />
            </label>
            <button type="submit" className="btn btn-primary">
              Save
            </button>
          </form>
          <p className="text-xs text-marble/60">A Game already running keeps the questions it drew.</p>
        </section>
      ) : null}

      <section className="flex flex-col items-start gap-2">
        <h2 className="text-lg font-semibold">Day Leaderboard</h2>
        <p className="text-sm text-marble/80">Empty it before doors open, so a test run can&apos;t win the prize.</p>
        <button type="button" onClick={reset} className="btn btn-danger">
          Reset
        </button>
      </section>
    </main>
  );
}
