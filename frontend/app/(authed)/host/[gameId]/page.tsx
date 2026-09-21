"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { publicUrl } from "@/lib/api";
import { LobbyUpdate, startBattle, watchGame } from "@/lib/game";

/** Spec stories 28–29, as BattleController has them. */
const MIN_PLAYERS = 2;
const MAX_PLAYERS = 4;

/** The Battle lobby on the big screen (spec stories 26–29): PIN legible from 5 m, Players as they arrive, Start at 2–4. */
export default function HostGame() {
  const { gameId } = useParams<{ gameId: string }>();
  const [lobby, setLobby] = useState<LobbyUpdate | null>(null);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(
    () =>
      watchGame(gameId, (event) => {
        if (event.type === "LOBBY_UPDATE") setLobby(event.payload);
      }),
    [gameId],
  );

  async function start() {
    setStarting(true);
    setError(null);
    try {
      await startBattle(gameId); // ticket 09: the first QUESTION_START then arrives on the topic
    } catch (err) {
      setError((err as Error).message);
      setStarting(false);
    }
  }

  const players = lobby?.players ?? [];
  const enough = players.length >= MIN_PLAYERS;

  return (
    <main className="flex flex-1 gap-16 p-12">
      <section className="flex min-w-0 flex-1 flex-col justify-center gap-6 text-center">
        <p className="text-4xl text-cream/80">Go to {publicUrl("/join")} and enter</p>
        {/* six digits at ~0.9 em each with tracking: fills two thirds of the width on any screen */}
        <p className="text-[clamp(5rem,11vw,14rem)] font-bold leading-none tracking-[0.15em] tabular-nums text-gold-500">
          {lobby?.pin ?? "······"}
        </p>
      </section>
      <aside className="flex w-1/3 flex-col gap-6">
        <h1 className="text-4xl font-bold">
          Players{" "}
          <span className="text-cream/60">
            {players.length} / {MAX_PLAYERS}
          </span>
        </h1>
        <ol className="flex flex-1 flex-col gap-3 text-5xl font-bold">
          {players.map((p) => (
            <li key={p.id} className="truncate">
              {p.name}
            </li>
          ))}
        </ol>
        <button
          type="button"
          onClick={start}
          disabled={!enough || starting}
          className="rounded bg-gold-500 px-10 py-5 text-3xl font-bold text-royal-900 disabled:opacity-40"
        >
          {starting ? "Starting…" : enough ? "Start" : `${MIN_PLAYERS}–${MAX_PLAYERS} players`}
        </button>
        {error && (
          <p role="alert" className="text-xl text-red-300">
            {error}
          </p>
        )}
        <Link href="/host" className="text-center text-2xl text-gold-300 underline">
          End Battle
        </Link>
      </aside>
    </main>
  );
}
