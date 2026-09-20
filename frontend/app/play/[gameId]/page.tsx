"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState, useSyncExternalStore } from "react";
import { connectToGame, GameEvent, loadSeat, QuestionStart } from "@/lib/game";

const noSubscribe = () => () => {};

// Kahoot-style: one colour per option so a Player can aim by colour, not by reading
const OPTION_COLOURS = ["bg-red-600", "bg-blue-600", "bg-yellow-500", "bg-saudi"];
const OPTION_SHAPES = ["▲", "◆", "●", "■"];

/** Whole seconds left on the current question, from the server's startedAt + timeLimitSec, ticking on the phone. */
function useCountdown(question: QuestionStart | null) {
  const [secondsLeft, setSecondsLeft] = useState(0);
  useEffect(() => {
    if (!question) return;
    const deadline = Date.parse(question.startedAt) + question.timeLimitSec * 1000;
    const tick = () => setSecondsLeft(Math.max(0, Math.ceil((deadline - Date.now()) / 1000)));
    tick();
    const timer = setInterval(tick, 250); // setState with the same digit is a no-op render
    return () => clearInterval(timer);
  }, [question]);
  return secondsLeft;
}

export default function PlayGame() {
  const { gameId } = useParams<{ gameId: string }>();
  const [question, setQuestion] = useState<QuestionStart | null>(null);
  const [error, setError] = useState<string | null>(null);
  const secondsLeft = useCountdown(question);
  // undefined on the server render, null when this phone never started this Game
  const seat = useSyncExternalStore(noSubscribe, () => loadSeat(gameId), () => undefined);

  useEffect(() => {
    if (!seat) return;
    const onEvent = (event: GameEvent) => {
      if (event.type === "QUESTION_START") setQuestion(event.payload);
    };
    // first error wins: a refused CONNECT is followed by the socket closing
    return connectToGame(gameId, seat, onEvent, (m) => setError((e) => e ?? m));
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

  if (!question) {
    return <main className="flex flex-1 items-center justify-center p-6 text-lg text-cream/80">Get ready…</main>;
  }

  return (
    <main className="flex flex-1 flex-col gap-4 p-4">
      <header className="flex items-center justify-between text-gold-300">
        <span className="text-sm">Question {question.index + 1}</span>
        <span
          aria-live="polite"
          className={`rounded-full px-4 py-1 text-2xl font-bold tabular-nums ${secondsLeft <= 5 ? "bg-red-600 text-cream" : "bg-gold-500 text-royal-900"}`}
        >
          {secondsLeft}
        </span>
      </header>
      <h1 className="text-2xl font-bold leading-snug">{question.text}</h1>
      {/* answering arrives in ticket 05; the buttons are laid out and disabled so the screen is final */}
      <div className="grid flex-1 grid-cols-2 gap-3">
        {question.options.map((option, i) => (
          <button
            key={i}
            type="button"
            disabled
            className={`flex min-h-28 flex-col items-center justify-center gap-1 rounded-lg p-3 text-lg font-semibold text-white disabled:opacity-80 ${OPTION_COLOURS[i]}`}
          >
            <span aria-hidden className="text-2xl">
              {OPTION_SHAPES[i]}
            </span>
            {option}
          </button>
        ))}
      </div>
    </main>
  );
}
