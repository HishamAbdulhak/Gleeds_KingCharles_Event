"use client";

import { QRCodeSVG } from "qrcode.react";
import { useEffect, useState } from "react";
import { fetchLeaderboard, LeaderboardEntry, watchLeaderboard } from "@/lib/game";

/**
 * The Host idle screen (spec stories 24–25): the Day Leaderboard, live, and the QR code that brings walk-ups in.
 * Legible from 5 m: ten rows at most, rank and name in 48 px type.
 */
export default function Host() {
  const [top, setTop] = useState<LeaderboardEntry[]>([]);

  useEffect(() => {
    void fetchLeaderboard().then(setTop); // once; the topic keeps it current from here
    return watchLeaderboard(setTop);
  }, []);

  // client-only page (authed layout), so window exists; the env var wins when phones reach the app by another host
  const playUrl = `${process.env.NEXT_PUBLIC_BASE_URL ?? window.location.origin}/play`;

  return (
    <main className="flex flex-1 gap-16 p-12">
      <section className="flex flex-1 flex-col gap-8">
        <h1 className="text-6xl font-bold text-gold-500">Today&apos;s leaderboard</h1>
        {top.length === 0 ? (
          <p className="text-4xl text-cream/80">No Games yet — scan to be first!</p>
        ) : (
          <ol className="flex flex-col gap-3">
            {top.map((entry) => (
              <li key={entry.rank} className="flex items-baseline gap-8 text-5xl font-bold">
                <span className="w-20 text-right tabular-nums text-gold-300">{entry.rank}</span>
                <span className="flex-1 truncate">{entry.name}</span>
                <span className="tabular-nums">{entry.score}</span>
              </li>
            ))}
          </ol>
        )}
      </section>
      <aside className="flex flex-col items-center justify-center gap-6 text-center">
        <h2 className="text-4xl font-bold">Scan to play</h2>
        <div className="rounded-lg bg-cream p-4">
          <QRCodeSVG value={playUrl} size={360} bgColor="var(--color-cream)" fgColor="var(--color-royal-900)" />
        </div>
        <p className="text-2xl text-cream/80">{playUrl}</p>
        {/* dead until ticket 08 wires the Battle lobby */}
        <button
          type="button"
          disabled
          className="mt-6 rounded bg-gold-500 px-10 py-5 text-3xl font-bold text-royal-900 disabled:opacity-40"
        >
          New Battle
        </button>
      </aside>
    </main>
  );
}
