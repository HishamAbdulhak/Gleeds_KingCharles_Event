"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useReducer, useRef, useState, useSyncExternalStore } from "react";
import { AnswerGrid } from "@/components/AnswerGrid";
import { Timer } from "@/components/Timer";
import { noSubscribe } from "@/lib/api";
import { connectToGame, podiumRevealMs, Result, Standing, stored } from "@/lib/game";
import { reducePlayer, WAITING } from "@/lib/player";

export default function PlayGame() {
  const { gameId } = useParams<{ gameId: string }>();
  const [state, dispatch] = useReducer(reducePlayer, WAITING);
  const [error, setError] = useState<string | null>(null);
  const [online, setOnline] = useState(false);
  const socket = useRef<ReturnType<typeof connectToGame>>(null);
  // undefined on the server render, null when this phone never started this Game
  const seat = useSyncExternalStore(
    noSubscribe,
    () => stored.get(`seat:${gameId}`),
    () => undefined,
  );
  // which row of a Battle's Podium is this phone's
  const playerId = useSyncExternalStore(
    noSubscribe,
    () => stored.get(`player:${gameId}`),
    () => null,
  );

  useEffect(() => {
    if (!seat) return;
    socket.current = connectToGame(gameId, seat, { onEvent: dispatch, onError: setError, onOnline: setOnline });
    return socket.current.disconnect;
  }, [gameId, seat]);

  if (error || seat === null) {
    return (
      <main className="flex flex-1 flex-col items-center justify-center gap-4 p-6 text-center">
        <p role="alert" className="text-lg text-red-300">
          {error ?? "No seat for this Game on this phone."}
        </p>
        <Link href="/play" className="text-gold-300 underline">
          Start again
        </Link>
      </main>
    );
  }

  if (state.phase === "waiting") {
    return <main className="flex flex-1 items-center justify-center p-6 text-lg text-cream/80">Get ready…</main>;
  }

  if (state.phase === "lobby") {
    return (
      <main className="flex flex-1 flex-col items-center justify-center gap-6 p-6 text-center">
        <h1 className="text-3xl font-bold text-gold-500">You&apos;re in!</h1>
        <p className="text-lg text-cream/80">{online ? "Waiting for the Host to start…" : "Reconnecting…"}</p>
        <ul className="flex flex-col gap-2 text-2xl font-bold">
          {state.players.map((p) => (
            <li key={p.id}>{p.name}</li>
          ))}
        </ul>
      </main>
    );
  }

  if (state.phase === "between") {
    return (
      <main className="flex flex-1 flex-col items-center justify-center gap-4 p-6 text-center">
        <h1 className="text-3xl font-bold text-gold-500">Look at the big screen</h1>
        <p className="text-lg text-cream/80">Next question coming up…</p>
      </main>
    );
  }

  if (state.phase === "over") {
    const { podium, score, rank } = state.gameOver;
    return (
      <main className="flex flex-1 flex-col items-center justify-center gap-6 p-6 text-center">
        {podium ? (
          <MyPlace podium={podium} playerId={playerId} />
        ) : (
          <>
            <h1 className="text-3xl font-bold text-gold-500">Game over</h1>
            <p className="text-lg">
              Your Score
              <br />
              <span className="text-6xl font-bold tabular-nums">{score}</span>
            </p>
            {rank !== null && <p className="text-2xl font-bold text-gold-300">You&apos;re #{rank} today</p>}
          </>
        )}
        <Link href="/play" className="rounded bg-gold-500 px-8 py-4 text-xl font-bold text-royal-900">
          Play again
        </Link>
      </main>
    );
  }

  const { question } = state;

  return (
    <main className="flex flex-1 flex-col gap-4 p-4">
      {question && (
        <header className="flex flex-col gap-2 text-gold-300">
          <div className="flex items-center justify-between text-sm">
            <span>
              {question.index + 1} / {question.total}
            </span>
            {!online && <span className="text-red-300">Reconnecting…</span>}
          </div>
          {state.phase !== "result" && <Timer question={question} />}
        </header>
      )}
      {state.phase === "result" && <ResultBanner result={state.result} answered={state.selected !== null} />}
      {question && <h1 className="text-2xl font-bold leading-snug">{question.text}</h1>}
      {question && (
        <AnswerGrid
          options={question.options}
          selected={state.phase === "question" ? null : state.selected}
          correctOption={state.phase === "result" ? state.result.correctOption : undefined}
          onSelect={
            state.phase === "question" && online // publish() throws on a closed socket; the header says Reconnecting…
              ? (option) => {
                  dispatch({ type: "SELECT", option });
                  socket.current?.answer(question.index, option);
                }
              : undefined
          }
        />
      )}
      <p role="status" aria-live="polite" className="min-h-6 text-center text-cream/80">
        {state.phase === "locked" && "Locked in…"}
        {state.phase === "question" && state.notice}
        {state.phase === "result" && "Next question coming up…"}
      </p>
    </main>
  );
}

/** RESULT: right / wrong / out of time, Points earned, Streak (on fire from 2) and running Score. */
function ResultBanner({ result: { correct, points, streak, score }, answered }: { result: Result; answered: boolean }) {
  return (
    <section className={`rounded-lg p-4 text-center ${correct ? "bg-saudi" : "bg-red-700"}`}>
      <h2 className="text-3xl font-bold">{correct ? "Correct!" : answered ? "Wrong" : "Time's up"}</h2>
      <p className="text-2xl font-bold tabular-nums">+{points}</p>
      <p className="flex justify-center gap-4 text-sm">
        <span>{streak >= 2 ? `🔥 ${streak} streak` : `Streak ${streak}`}</span>
        <span>Score {score}</span>
      </p>
    </section>
  );
}

/** A Battle is 2–4 Players (CONTEXT.md → Lobby). */
const PLACES = ["1st", "2nd", "3rd", "4th"];

/**
 * The end of a Battle on the phone (#9): the Player's own place, held back until the big screen has revealed it —
 * both run the same schedule off the one GAME_OVER. Only their own place, so a 4th place can't read out the winner.
 */
function MyPlace({ podium, playerId }: { podium: Standing[]; playerId: string | null }) {
  const place = podium.findIndex((entry) => entry.playerId === playerId) + 1; // 0: this phone lost track of its Player
  const [revealed, setRevealed] = useState(false);
  useEffect(() => {
    const timer = setTimeout(() => setRevealed(true), podiumRevealMs(place));
    return () => clearTimeout(timer);
  }, [place]);

  if (!revealed) {
    return <p className="text-2xl text-cream/80">Look at the big screen…</p>;
  }
  const you = podium[place - 1];
  return (
    <>
      <h1 className="text-6xl font-bold text-gold-500">{you ? PLACES[place - 1] : "Game over"}</h1>
      {you && (
        <p className="text-lg">
          Your Score
          <br />
          <span className="text-6xl font-bold tabular-nums">{you.score}</span>
        </p>
      )}
      <p className="text-cream/80">of {podium.length} players</p>
    </>
  );
}
