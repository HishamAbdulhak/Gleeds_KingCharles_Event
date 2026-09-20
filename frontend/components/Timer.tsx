"use client";

import { useEffect, useState } from "react";
import type { QuestionStart } from "@/lib/game";

/** Milliseconds left on the question, from the server's startedAt + timeLimitSec: reopening the page mid-question shows the true remainder. */
function useMsLeft(question: QuestionStart) {
  const [msLeft, setMsLeft] = useState(0);
  useEffect(() => {
    const deadline = Date.parse(question.startedAt) + question.timeLimitSec * 1000;
    const tick = () => setMsLeft(Math.max(0, deadline - Date.now()));
    tick();
    const timer = setInterval(tick, 100);
    return () => clearInterval(timer);
  }, [question]);
  return msLeft;
}

/** Countdown for the open question: a shrinking bar and whole seconds, red for the last five. */
export function Timer({ question }: { question: QuestionStart }) {
  const msLeft = useMsLeft(question);
  const seconds = Math.ceil(msLeft / 1000);
  const urgent = seconds <= 5;
  return (
    <div className="flex items-center gap-3">
      <div className="h-3 flex-1 overflow-hidden rounded-full bg-cream/20" aria-hidden>
        <div
          className={`h-full ${urgent ? "bg-red-500" : "bg-gold-500"}`}
          style={{ width: `${(msLeft / (question.timeLimitSec * 1000)) * 100}%` }}
        />
      </div>
      <span
        aria-live="polite"
        className={`min-w-14 rounded-full px-3 py-1 text-center text-2xl font-bold tabular-nums ${urgent ? "bg-red-600 text-cream" : "bg-gold-500 text-royal-900"}`}
      >
        {seconds}
      </span>
    </div>
  );
}
