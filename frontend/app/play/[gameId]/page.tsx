"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { ReactNode, useEffect, useReducer, useRef, useState, useSyncExternalStore } from "react";
import { AnswerGrid } from "@/components/AnswerGrid";
import { Reconnecting } from "@/components/Reconnecting";
import { Timer } from "@/components/Timer";
import { noSubscribe } from "@/lib/api";
import { BestScore, connectToGame, podiumRevealedMs, Result, Standing, stored } from "@/lib/game";
import { reducePlayer, WAITING } from "@/lib/player";

const formatNumber = (n: number) => n.toLocaleString("en-GB");

export default function PlayGame() {
  const { gameId } = useParams<{ gameId: string }>();
  const [state, dispatch] = useReducer(reducePlayer, WAITING);
  const [error, setError] = useState<string | null>(null);
  const [online, setOnline] = useState<boolean | null>(null);
  const socket = useRef<ReturnType<typeof connectToGame>>(null);
  // undefined on the server render, null when this phone never started this Game
  const seat = useSyncExternalStore(
    noSubscribe,
    () => stored.get(`seat:${gameId}`),
    () => undefined,
  );
  // which row of a Battle's lobby and Podium is this phone's
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

  const reconnecting = <Reconnecting online={online} detail="Stay on this page. It reconnects by itself." />;

  if (error || seat === null) {
    return (
      <main className="flex flex-1 flex-col items-center justify-center p-6">
        <div className="panel flex w-full max-w-sm flex-col gap-4">
          <p role="alert" className="alert">
            {error ?? "No seat for this Game on this phone."}
          </p>
          <Link href="/play" className="link text-lg">
            Start again
          </Link>
        </div>
      </main>
    );
  }

  if (state.phase === "waiting") {
    return (
      <main className="flex flex-1 items-center justify-center p-6 text-2xl font-bold">
        {reconnecting}
        Get ready…
      </main>
    );
  }

  if (state.phase === "lobby") {
    const me = state.players.find((p) => p.id === playerId);
    return (
      <main className="flex flex-1 flex-col items-center justify-center gap-6 p-6">
        {reconnecting}
        <header className="flex flex-col items-center gap-2 text-center">
          <h1 className="text-4xl font-bold">You&apos;re in!</h1>
          {me && (
            <p className="text-lg">
              Playing as <span className="font-bold">{me.name}</span>
            </p>
          )}
        </header>
        <section className="flex w-full max-w-sm flex-col gap-2">
          {/* a Battle is 2–4 Players (CONTEXT.md → Lobby) */}
          <h2 className="text-lg text-marble/80 tabular-nums">{state.players.length} of 4 players</h2>
          <ul className="flex flex-col gap-2">
            {state.players.map((p) => (
              <li key={p.id} className="reveal rounded-xl border border-marble/15 px-4 py-3 text-xl font-bold">
                {p.name}
                {p.id === playerId && <span className="font-normal text-marble/80"> (you)</span>}
              </li>
            ))}
          </ul>
        </section>
        <p className="text-lg text-marble/80">Waiting for the Host to start…</p>
      </main>
    );
  }

  if (state.phase === "between") {
    return (
      <main className="flex flex-1 flex-col items-center justify-center gap-4 p-6 text-center">
        {reconnecting}
        <h1 className="text-4xl font-bold">Look at the big screen</h1>
        <p className="text-lg text-marble/80">Next question coming up…</p>
      </main>
    );
  }

  if (state.phase === "over") {
    const { podium, score, rank } = state.gameOver;
    return (
      <main className="flex flex-1 flex-col items-center justify-center gap-6 p-6">
        {reconnecting}
        {podium ? (
          <MyPlace podium={podium} playerId={playerId} best={state.best} />
        ) : (
          <PrizeProof score={score} rank={rank} best={state.best} />
        )}
        <Link href="/play" className="btn btn-primary btn-lg w-full max-w-sm">
          Play again
        </Link>
      </main>
    );
  }

  const { question } = state;

  return (
    <main className={`flex flex-1 flex-col gap-4 p-4 ${online === false ? "pt-24" : ""}`}>
      {reconnecting}
      {state.phase === "result" && <ResultBanner result={state.result} answered={state.answered} />}
      {question && (
        <>
          <p className="text-base text-marble/80 tabular-nums">
            Question {question.index + 1} of {question.total}
          </p>
          <h1 className={`font-bold leading-snug ${state.phase === "result" ? "text-xl text-marble/80" : "text-2xl"}`}>
            {question.text}
          </h1>
          {state.phase !== "result" && <Timer question={question} />}
          <AnswerGrid
            options={question.options}
            selected={state.phase === "question" ? null : state.selected}
            correctOption={state.phase === "result" ? state.result.correctOption : undefined}
            onSelect={
              state.phase === "question" && online // publish() throws on a closed socket; the banner says Reconnecting…
                ? (option) => {
                    dispatch({ type: "SELECT", option });
                    socket.current?.answer(question.index, option);
                  }
                : undefined
            }
          />
        </>
      )}
      {/* without the question (the page was reopened after answering), this is the whole screen */}
      <div
        role="status"
        aria-live="polite"
        className={question ? "min-h-14 text-center" : "panel my-auto flex flex-col gap-1 text-center"}
      >
        {state.phase === "locked" && (
          <>
            <p className="text-xl font-bold">Answer locked in</p>
            <p className="text-lg text-marble/80">Waiting for the result…</p>
          </>
        )}
        {state.phase === "question" && state.notice && <p className="alert">{state.notice}</p>}
        {state.phase === "result" && <p className="text-lg text-marble/80">Next question coming up…</p>}
      </div>
    </main>
  );
}

/** RESULT: right / wrong / out of time, Points earned, Streak and running Score, in that order of size. */
function ResultBanner({ result: { correct, points, streak, score }, answered }: { result: Result; answered: boolean }) {
  return (
    <section
      className={`flex flex-col items-center gap-1 rounded-xl p-5 text-center ${correct ? "bg-success text-marble" : "bg-danger text-black"}`}
    >
      <h2 className="flex items-center gap-3 text-4xl font-bold">
        {(correct || answered) && <span aria-hidden>{correct ? "✓" : "✕"}</span>}
        {correct ? "Correct" : answered ? "Wrong" : "Time's up"}
      </h2>
      <p className="text-6xl font-bold tabular-nums">+{formatNumber(points)}</p>
      <p className="text-lg">points</p>
      <p className="mt-2 flex gap-6 text-lg font-bold tabular-nums">
        <span>Streak {streak}</span>
        <span>Score {formatNumber(score)}</span>
      </p>
    </section>
  );
}

function Stat({
  label,
  big = false,
  className = "",
  children,
}: {
  label: string;
  big?: boolean;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col">
      <dt className="text-base text-marble/80">{label}</dt>
      <dd className={`${big ? "text-6xl" : "text-4xl"} font-bold tabular-nums ${className}`}>{children}</dd>
    </div>
  );
}

/**
 * What the Player shows staff to claim the day's prize (docs/adr/0003): their name and Best Score, which is their
 * email's, not just this Game's, and the reminder to keep it on screen. Staff match both to the live board. The name,
 * Best Score and reminder wait for BEST_SCORE, a moment after GAME_OVER. `rank` is Solo's place on the Day Leaderboard; a Battle shows its place above the card.
 */
function PrizeProof({ score, rank = null, best }: { score: number | null; rank?: number | null; best?: BestScore }) {
  return (
    <section className="panel flex w-full max-w-sm flex-col gap-4">
      <header>
        <p className="text-lg text-marble/80">Game over</p>
        {best && <h1 className="text-3xl font-bold">{best.name}</h1>}
      </header>
      {score !== null && (
        <dl>
          <Stat label="Your score" big>
            {formatNumber(score)}
          </Stat>
        </dl>
      )}
      <dl className="grid grid-cols-2 gap-4">
        {rank !== null && <Stat label="Rank today">#{formatNumber(rank)}</Stat>}
        {best && best.score !== null && (
          <Stat label="Best today" className="text-yellow">
            {formatNumber(best.score)}
          </Stat>
        )}
      </dl>
      {best && <p className="text-lg font-bold">Keep this screen open to claim your prize</p>}
    </section>
  );
}

const PLACES = ["1st", "2nd", "3rd", "4th"];

/**
 * The end of a Battle on the phone (#9): the Player's own place, held back until the big screen has finished
 * revealing it — both run the same schedule off the one GAME_OVER. Only their own place, so a 4th place can't read
 * out the winner, then the prize proof. A phone that lost its `player:<gameId>` has no row to wait for: it says so at
 * once, and still shows the proof, which comes on its own queue.
 */
function MyPlace({ podium, playerId, best }: { podium: Standing[]; playerId: string | null; best?: BestScore }) {
  const place = podium.findIndex((entry) => entry.playerId === playerId) + 1; // 0: this phone lost track of its Player
  const you = podium[place - 1];
  const [revealed, setRevealed] = useState(false);
  useEffect(() => {
    if (!you) return;
    const timer = setTimeout(() => setRevealed(true), podiumRevealedMs(place));
    return () => clearTimeout(timer);
  }, [place, you]);

  if (!you) return <PrizeProof score={null} best={best} />;
  if (!revealed) {
    return <p className="text-2xl font-bold">Look at the big screen…</p>;
  }
  return (
    <>
      <p className="reveal text-center">
        <span className="block text-6xl font-bold text-yellow">{PLACES[place - 1]}</span>
        <span className="text-lg text-marble/80">of {podium.length} players</span>
      </p>
      <PrizeProof score={you.score} best={best} />
    </>
  );
}
