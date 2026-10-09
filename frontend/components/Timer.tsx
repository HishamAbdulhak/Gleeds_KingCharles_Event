"use client";

import { useEffect, useState } from "react";
import type { QuestionStart } from "@/lib/game";

/**
 * Countdown for the open question: a shrinking bar and whole seconds, red for the last five. Ticks from the server's
 * startedAt + timeLimitSec, so reopening the page mid-question shows the true remainder. `big` for the Host screen,
 * read from across the room; the phone, glanced at in the hand, also pulses for the last five.
 */
export function Timer({ question, big = false }: { question: QuestionStart; big?: boolean }) {
  const deadline = Date.parse(question.startedAt) + question.timeLimitSec * 1000;
  // the true remainder from the first frame, not a red 0 until the first tick
  const [msLeft, setMsLeft] = useState(() => Math.max(0, deadline - Date.now()));
  useEffect(() => {
    const tick = () => setMsLeft(Math.max(0, deadline - Date.now()));
    tick();
    const timer = setInterval(tick, 100);
    return () => clearInterval(timer);
  }, [deadline]);
  const seconds = Math.ceil(msLeft / 1000);
  const urgent = seconds <= 5;
  return (
    <div className="flex items-center gap-3">
      <div className={`${big ? "h-6" : "h-4"} flex-1 overflow-hidden rounded-full bg-marble/20`} aria-hidden>
        <div
          className={`h-full ${urgent ? "bg-danger" : "bg-yellow"}`}
          style={{ width: `${(msLeft / (question.timeLimitSec * 1000)) * 100}%` }}
        />
      </div>
      <span
        role="timer"
        className={`rounded-full px-3 py-1 text-center font-bold tabular-nums ${big ? "min-w-14 text-6xl" : "min-w-16 text-3xl"} ${urgent ? "bg-danger text-black" : "bg-yellow text-obsidian"} ${urgent && !big ? "motion-safe:animate-pulse" : ""}`}
      >
        {seconds}
        <span className="sr-only"> seconds left</span>
      </span>
    </div>
  );
}
