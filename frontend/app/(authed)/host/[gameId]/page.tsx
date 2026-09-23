"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useReducer, useState } from "react";
import { OPTION_COLOURS, OPTION_SHAPES } from "@/components/AnswerGrid";
import { Timer } from "@/components/Timer";
import { publicUrl } from "@/lib/api";
import { hostCommand, LobbyUpdate, podiumRevealMs, QuestionStart, Reveal, Standing, watchGame } from "@/lib/game";
import { HostScreen, LOBBY, reduceHost } from "@/lib/host";

/** Spec stories 28–29, as BattleController has them. */
const MIN_PLAYERS = 2;
const MAX_PLAYERS = 4;

/** The big button each screen ends with; only the Podium has none, because the Game is already over. */
const ACTIONS = {
  lobby: { label: "Start", command: "start" },
  question: { label: "Reveal", command: "reveal" },
  reveal: { label: "Next", command: "next" },
  standings: { label: "Next", command: "next" },
} as const;

/**
 * The Battle on the big screen (spec stories 26–35): lobby, question, reveal, leaderboard, Podium. Nothing advances
 * by itself — every screen is the Host's tap, and End Battle is on all of them.
 */
export default function HostGame() {
  const { gameId } = useParams<{ gameId: string }>();
  const [screen, dispatch] = useReducer(reduceHost, LOBBY);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => watchGame(gameId, dispatch), [gameId]);

  async function run(command: "start" | "reveal" | "next" | "end") {
    setBusy(true);
    setError(null);
    try {
      await hostCommand(gameId, command); // the screen follows the events the command publishes
    } catch (err) {
      setError((err as Error).message); // e.g. 409 below two Players
    } finally {
      setBusy(false);
    }
  }

  const action = screen.phase === "podium" ? null : ACTIONS[screen.phase];
  // the lobby's Start is the one command with a bar to clear (spec stories 28–29), and says so until it is
  const tooFew = screen.phase === "lobby" && screen.players.length < MIN_PLAYERS;

  return (
    <main className="flex flex-1 flex-col gap-8 p-10">
      <div className="flex min-h-0 flex-1 gap-12">
        <Screen screen={screen} />
      </div>
      <footer className="flex items-center justify-end gap-8">
        {error && (
          <p role="alert" className="mr-auto text-xl text-red-300">
            {error}
          </p>
        )}
        {action ? (
          <>
            <button
              type="button"
              onClick={() => run("end")}
              disabled={busy}
              className={`${buttonClass} border-2 border-cream/40 text-cream/80`}
            >
              End Battle
            </button>
            <button
              type="button"
              onClick={() => run(action.command)}
              disabled={busy || tooFew}
              className={`${buttonClass} bg-gold-500 text-royal-900 disabled:opacity-40`}
            >
              {tooFew ? `${MIN_PLAYERS}–${MAX_PLAYERS} players` : action.label}
            </button>
          </>
        ) : (
          <Link href="/host" className={`${buttonClass} bg-gold-500 text-royal-900`}>
            Back to leaderboard
          </Link>
        )}
      </footer>
    </main>
  );
}

const buttonClass = "rounded px-10 py-5 text-3xl font-bold";

function Screen({ screen }: { screen: HostScreen }) {
  switch (screen.phase) {
    case "lobby":
      return <Lobby pin={screen.pin} players={screen.players} />;
    case "question":
      return <Asked question={screen.question} roster={screen.roster} />;
    case "reveal":
      return <Revealed question={screen.question} reveal={screen.reveal} />;
    case "standings":
      return <Standings standings={screen.standings} />;
    case "podium":
      return <Podium podium={screen.podium} />;
  }
}

/** Spec stories 26–27: the PIN legible from 5 m, and Players appearing as they arrive. */
function Lobby({ pin, players }: { pin: string | null; players: LobbyUpdate["players"] }) {
  return (
    <>
      <section className="flex min-w-0 flex-1 flex-col justify-center gap-6 text-center">
        <p className="text-4xl text-cream/80">Go to {publicUrl("/join")} and enter</p>
        {/* six digits at ~0.9 em each with tracking: fills two thirds of the width on any screen */}
        <p className="text-[clamp(5rem,11vw,14rem)] font-bold leading-none tracking-[0.15em] tabular-nums text-gold-500">
          {pin ?? "······"}
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
      </aside>
    </>
  );
}

/** Spec stories 30–31: the question big enough to read from a distance, and how many Players are in. */
function Asked({
  question,
  roster,
}: {
  question: QuestionStart;
  roster: { id: string; name: string; answered: boolean; score: number }[];
}) {
  return (
    <div className="flex min-w-0 flex-1 flex-col gap-8">
      <header className="flex items-center gap-8 text-3xl text-gold-300">
        <span className="tabular-nums">
          {question.index + 1} / {question.total}
        </span>
        <div className="flex-1">
          <Timer question={question} />
        </div>
        <span className="tabular-nums">
          {roster.filter((player) => player.answered).length} of {roster.length} answered
        </span>
      </header>
      <h1 className="text-[clamp(2rem,4vw,4.5rem)] font-bold leading-tight">{question.text}</h1>
      <Options options={question.options} />
      <ul className="flex flex-wrap gap-4 text-2xl">
        {roster.map((player) => (
          <li
            key={player.id}
            className={`rounded-full px-5 py-2 font-bold ${player.answered ? "bg-saudi" : "bg-cream/15 text-cream/70"}`}
          >
            {player.name} <span className="tabular-nums">{player.score}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Spec story 32: the correct option, and how many chose each. */
function Revealed({ question, reveal }: { question: QuestionStart; reveal: Reveal }) {
  return (
    <div className="flex min-w-0 flex-1 flex-col gap-8">
      <h1 className="text-[clamp(2rem,4vw,4.5rem)] font-bold leading-tight">{question.text}</h1>
      <Options options={question.options} reveal={reveal} />
    </div>
  );
}

/** The four options as the room sees them; after the reveal, the correct one stands out and each carries its count. */
function Options({ options, reveal }: { options: string[]; reveal?: Reveal }) {
  const most = Math.max(1, ...(reveal?.counts ?? []));
  return (
    <div className="grid flex-1 grid-cols-2 gap-6">
      {options.map((option, i) => (
        <div
          key={i}
          className={`flex flex-col justify-center gap-3 rounded-xl p-6 text-3xl font-bold text-white ${OPTION_COLOURS[i]} ${reveal && i !== reveal.correctOption ? "opacity-40" : ""}`}
        >
          <span className="flex items-center gap-4">
            <span aria-hidden className="text-4xl">
              {OPTION_SHAPES[i]}
            </span>
            {option}
            {reveal && i === reveal.correctOption && <span className="ml-auto text-4xl">✓</span>}
          </span>
          {reveal && (
            <span className="flex items-center gap-3">
              <span className="h-4 rounded-full bg-white/80" style={{ width: `${(reveal.counts[i] / most) * 70}%` }} />
              <span className="tabular-nums">{reveal.counts[i]}</span>
            </span>
          )}
        </div>
      ))}
    </div>
  );
}

/** Spec story 33: where everyone stands between questions, with what the last question earned. */
function Standings({ standings }: { standings: Standing[] }) {
  return (
    <section className="flex flex-1 flex-col gap-6">
      <h1 className="text-6xl font-bold text-gold-500">Standings</h1>
      <ol className="flex flex-col gap-4">
        {standings.map((entry, i) => (
          <li key={entry.playerId} className="flex items-baseline gap-8 text-5xl font-bold">
            <span className="w-16 text-right tabular-nums text-gold-300">{i + 1}</span>
            <span className="flex-1 truncate">{entry.name}</span>
            <span className="text-3xl text-saudi tabular-nums">+{entry.delta}</span>
            <span className="tabular-nums">{entry.score}</span>
          </li>
        ))}
      </ol>
    </section>
  );
}

const MEDALS = ["🥇", "🥈", "🥉"];
const PODIUM_SIZES = ["text-8xl", "text-7xl", "text-6xl"];

/** The Podium (#9), revealed from the bottom up: 3rd, then 2nd, then 1st, a beat apart. */
function Podium({ podium }: { podium: Standing[] }) {
  return (
    <section className="flex flex-1 flex-col items-center justify-center gap-8">
      <ol className="flex w-full max-w-4xl flex-col gap-6">
        {podium.slice(0, 3).map((entry, i) => (
          <li
            key={entry.playerId}
            style={{ animationDelay: `${podiumRevealMs(i + 1)}ms` }}
            className={`reveal flex items-baseline gap-8 font-bold ${PODIUM_SIZES[i]}`}
          >
            <span aria-hidden>{MEDALS[i]}</span>
            <span className="flex-1 truncate">{entry.name}</span>
            <span className="tabular-nums text-gold-300">{entry.score}</span>
          </li>
        ))}
      </ol>
      <ol className="flex w-full max-w-4xl flex-col gap-3 text-3xl">
        {podium.slice(3).map((entry, i) => (
          <li key={entry.playerId} className="flex items-baseline gap-8">
            <span className="w-16 text-right tabular-nums text-cream/60">{i + 4}</span>
            <span className="flex-1 truncate">{entry.name}</span>
            <span className="tabular-nums">{entry.score}</span>
          </li>
        ))}
      </ol>
    </section>
  );
}
