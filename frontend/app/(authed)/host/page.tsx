"use client";

import { useRouter } from "next/navigation";
import { QRCodeSVG } from "qrcode.react";
import { useEffect, useState } from "react";
import { Brandmark } from "@/components/Brandmark";
import { Reconnecting } from "@/components/Reconnecting";
import { publicUrl } from "@/lib/api";
import { createBattle, fetchLeaderboard, formatNumber, LeaderboardEntry, watchLeaderboard } from "@/lib/game";

/**
 * The Host idle screen (spec stories 24–25): the Day Leaderboard, live, and the QR code that brings walk-ups in.
 * Legible from 5 m: ten rows at most, rank and name in 48 px type.
 */
export default function Host() {
  const router = useRouter();
  const [top, setTop] = useState<LeaderboardEntry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [online, setOnline] = useState<boolean | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void fetchLeaderboard().then(setTop); // once; the topic keeps it current from here
    return watchLeaderboard(setTop, setOnline);
  }, []);

  async function hostBattle() {
    setBusy(true); // a second tap would open a second Battle
    setError(null);
    try {
      const { gameId } = await createBattle();
      router.push(`/host/${gameId}`);
    } catch (err) {
      setError((err as Error).message); // e.g. the Question Bank is too small for a Game
      setBusy(false);
    }
  }

  const playUrl = publicUrl("/play"); // client-only page (authed layout), so window exists

  return (
    <main className="flex flex-1 flex-col gap-12 p-12">
      <Reconnecting online={online} />
      <Brandmark className="w-32" />
      <div className="flex flex-1 flex-col gap-16 lg:flex-row">
        <section className="flex min-w-0 flex-1 flex-col gap-8">
          <h1 className="text-6xl font-bold">Today&apos;s leaderboard</h1>
          {top.length === 0 ? (
            <p className="text-4xl text-marble/80">No Games yet — scan to be first!</p>
          ) : (
            <ol className="flex flex-col gap-3">
              {top.map((entry) => (
                <li key={entry.rank} className="flex items-baseline gap-8 text-5xl font-bold">
                  <span className="w-20 text-right tabular-nums text-marble/60">{entry.rank}</span>
                  <span className="flex-1 truncate">{entry.name}</span>
                  <span className="tabular-nums">{formatNumber(entry.score)}</span>
                </li>
              ))}
            </ol>
          )}
        </section>
        <aside className="flex flex-col items-center justify-center gap-6 text-center">
          {/* the instruction and the code are one object, the screen's call to action; Host a Battle is secondary to it */}
          <div className="flex flex-col items-center gap-6 rounded-xl bg-marble p-8 text-obsidian">
            <h2 className="text-6xl font-bold">Scan to play</h2>
            {/* 41vh (440 px at 1080): the largest that fits a 1080 px screen with the title above it and the URL and button
                below; it shrinks with a shorter screen, to no less than 16rem. `size` only sets the SVG's own attributes */}
            <QRCodeSVG
              value={playUrl}
              size={440}
              bgColor="var(--color-marble)"
              fgColor="var(--color-obsidian)"
              className="size-[max(16rem,41vh)]"
            />
          </div>
          <p className="text-2xl font-bold wrap-anywhere text-marble/60">{playUrl}</p>
          <button type="button" onClick={hostBattle} disabled={busy} className="btn btn-secondary btn-xl mt-6">
            {busy ? "Creating…" : "Host a Battle"}
          </button>
          {error && (
            <p role="alert" className="text-2xl text-danger-fg">
              {error}
            </p>
          )}
        </aside>
      </div>
    </main>
  );
}
