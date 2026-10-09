"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { count, Question } from "@/lib/admin";
import { api } from "@/lib/api";
import { fetchLeaderboard, formatNumber, LeaderboardEntry } from "@/lib/game";

type Overview = { questions: Question[]; questionsPerGame: number; top: LeaderboardEntry[] };

/** What the Admin checks before doors open: the Bank can fill a Game, and the Day Leaderboard (Reset, stories 43–44). */
export default function AdminHome() {
  const [overview, setOverview] = useState<Overview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(
    () =>
      Promise.all([
        api<Question[]>("/api/admin/questions"),
        api<{ questionsPerGame: number }>("/api/admin/settings"),
        fetchLeaderboard(),
      ])
        .then(([questions, { questionsPerGame }, top]) => setOverview({ questions, questionsPerGame, top }))
        .catch((e: Error) => setError(e.message)),
    [],
  );

  useEffect(() => {
    load();
  }, [load]);

  async function reset() {
    setResetting(true);
    setError(null);
    try {
      await api("/api/admin/reset", { method: "POST" });
      await load();
      setNotice("The Day Leaderboard is empty. Only Games started from now on count.");
    } catch (e) {
      setError((e as Error).message);
    }
    setResetting(false);
    setConfirming(false);
  }

  const active = overview?.questions.filter((q) => q.active).length ?? 0;

  return (
    <main className="flex flex-col gap-6">
      <h1 className="text-2xl">Dashboard</h1>

      {error ? (
        <p role="alert" className="text-sm text-danger-fg">
          {error}
        </p>
      ) : null}

      {/* every panel renders while loading or after a failed load, so Reset is always reachable */}
      <div className="grid gap-6 md:grid-cols-2">
        <section className="panel flex flex-col gap-3 text-sm">
          <h2 className="text-lg">Question Bank</h2>
          {overview ? (
            <p className="tabular-nums">
              {count(overview.questions.length, "question")} · {active} active · {overview.questions.length - active}{" "}
              inactive
            </p>
          ) : null}
          <Link href="/admin/questions" className="link self-start">
            Manage questions
          </Link>
        </section>

        <section className="panel flex flex-col gap-3 text-sm">
          <h2 className="text-lg">Games</h2>
          {overview ? (
            <>
              <p className="tabular-nums">Each Game draws {count(overview.questionsPerGame, "question")}.</p>
              {/* the server's own rule (QuestionBank.draw): a Game can't start with fewer active questions */}
              {active < overview.questionsPerGame ? (
                <p role="alert" className="text-danger-fg">
                  A Game needs {count(overview.questionsPerGame, "active question")}; the Bank has {active}. Activate or
                  add more before doors open.
                </p>
              ) : (
                <p className="text-success-fg">✓ Ready: enough active questions for a Game.</p>
              )}
            </>
          ) : null}
          <Link href="/admin/settings" className="link self-start">
            Change in Settings
          </Link>
        </section>

        <section className="panel flex flex-col gap-3 text-sm">
          <h2 className="text-lg">Day Leaderboard</h2>
          {!overview ? null : overview.top.length === 0 ? (
            <p className="text-marble/80">Empty. Nobody has played since the last reset.</p>
          ) : (
            <>
              <ol className="flex flex-col gap-1">
                {overview.top.slice(0, 3).map((e) => (
                  <li key={e.rank} className="flex gap-3 tabular-nums">
                    <span className="w-6 text-marble/60">{e.rank}</span>
                    <span className="flex-1 truncate">{e.name}</span>
                    <span>{formatNumber(e.score)}</span>
                  </li>
                ))}
              </ol>
              <p className="text-xs text-marble/60">The top 3; the big screen shows up to 10.</p>
            </>
          )}
          {notice ? (
            <p role="status" className="text-success-fg">
              {notice}
            </p>
          ) : null}
          <p className="text-marble/80">Reset it before doors open, so a test run can&apos;t win the prize.</p>
          <button
            type="button"
            onClick={() => {
              setNotice(null);
              setConfirming(true);
            }}
            className="btn btn-danger self-start"
          >
            Reset Day Leaderboard
          </button>
        </section>

        <section className="panel flex flex-col gap-3 text-sm">
          <h2 className="text-lg">Host screen</h2>
          <p className="text-marble/80">The big screen: today&apos;s leaderboard, the QR code, and Battles.</p>
          <Link href="/host" className="link self-start">
            Open the Host screen
          </Link>
        </section>
      </div>

      <ConfirmDialog
        open={confirming}
        title="Reset the Day Leaderboard?"
        confirmLabel="Reset Day Leaderboard"
        busyLabel="Resetting…"
        busy={resetting}
        onConfirm={reset}
        onCancel={() => setConfirming(false)}
      >
        <p>The Day Leaderboard on the big screen empties straight away.</p>
        <p>
          Every Game played so far is kept, but none of them count towards today&apos;s prize any more, including a Game
          in progress right now. Only Games started after the reset count.
        </p>
        <p className="text-marble">You can&apos;t undo this.</p>
      </ConfirmDialog>
    </main>
  );
}
