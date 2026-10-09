"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useReducer, useState } from "react";
import { OPTION_COLOURS, OPTION_SHAPES } from "@/components/AnswerGrid";
import { Brandmark } from "@/components/Brandmark";
import { Reconnecting } from "@/components/Reconnecting";
import { Timer } from "@/components/Timer";
import { publicUrl } from "@/lib/api";
import {
  hostCommand,
  HostState,
  LobbyUpdate,
  podiumRevealMs,
  QuestionStart,
  REVEALED_PLACES,
  Reveal,
  Standing,
  watchGame,
} from "@/lib/game";
import { HostScreen, LOBBY, reduceHost } from "@/lib/host";

/** Spec stories 28–29, as BattleController has them. */
const MIN_PLAYERS = 2;
const MAX_PLAYERS = 4;

/** The big button each screen ends with; only the Podium has none, because the Game is already over. */
const ACTIONS = {
  lobby: { label: "Start Battle", command: "start" },
  question: { label: "Reveal", command: "reveal" },
  reveal: { label: "Next", command: "next" },
  standings: { label: "Next", command: "next" },
} as const;

/**
 * The Battle on the big screen (spec stories 26–35): lobby, question, reveal, leaderboard, Podium. Nothing advances
 * by itself — every screen is the Host's tap, and End Battle is on all of them but the Podium, which the Game has
 * already ended on.
 */
export default function HostGame() {
  const { gameId } = useParams<{ gameId: string }>();
  const [screen, dispatch] = useReducer(reduceHost, LOBBY);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [online, setOnline] = useState<boolean | null>(null);

  useEffect(() => watchGame(gameId, dispatch, setOnline), [gameId]);

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
  // Start Battle is the one command with a bar to clear (spec stories 28–29); the lobby says how far off it is
  const tooFew = screen.phase === "lobby" && screen.players.length < MIN_PLAYERS;

  return (
    <main className="flex flex-1 flex-col gap-8 p-12">
      <Reconnecting online={online} />
      <Brandmark className="mb-4 w-32" />
      {/* a new key per screen (and per question) replays the fade, so each change of state enters rather than snaps */}
      <div
        key={screen.phase === "question" ? `question-${screen.question.index}` : screen.phase}
        className="screen-in flex min-h-0 flex-1 gap-12"
      >
        <Screen screen={screen} />
      </div>
      <footer className="flex items-center justify-end gap-8">
        {error && (
          <p role="alert" className="mr-auto text-xl text-danger-fg">
            {error}
          </p>
        )}
        {action ? (
          <>
            <button type="button" onClick={() => run("end")} disabled={busy} className="btn btn-secondary btn-xl">
              End Battle
            </button>
            <button
              type="button"
              onClick={() => run(action.command)}
              disabled={busy || tooFew}
              className="btn btn-primary btn-xl"
            >
              {action.label}
            </button>
          </>
        ) : (
          <Link href="/host" className="btn btn-primary btn-xl">
            Back to leaderboard
          </Link>
        )}
      </footer>
    </main>
  );
}

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

/** Spec stories 26–29: the PIN legible from 5 m, Players appearing as they arrive, and whether Start Battle is open. */
function Lobby({ pin, players }: { pin: string | null; players: LobbyUpdate["players"] }) {
  const missing = MIN_PLAYERS - players.length;
  return (
    <>
      <section className="flex min-w-0 flex-1 flex-col justify-center gap-6 text-center">
        <p className="text-3xl text-marble/80">Join at {publicUrl("/join")} with the Battle PIN</p>
        {/* six digits at ~0.9 em each with tracking: fills two thirds of the width on any screen */}
        <p className="text-[clamp(5rem,11vw,14rem)] font-bold leading-none tracking-[0.15em] tabular-nums text-yellow">
          {pin ?? "······"}
        </p>
      </section>
      <aside className="panel flex w-1/3 flex-col gap-6">
        <h1 className="text-5xl font-bold tabular-nums">
          {players.length} / {MAX_PLAYERS} players
        </h1>
        <p role="status" className={`text-3xl font-bold ${missing > 0 ? "text-marble/80" : "text-success-fg"}`}>
          {missing > 0 ? `Waiting for ${missing} more player${missing === 1 ? "" : "s"}` : "Ready to start"}
        </p>
        <ol className="flex flex-col gap-3 text-4xl font-bold">
          {/* keyed by id, so only a newcomer runs the entrance */}
          {players.map((p) => (
            <li key={p.id} className="reveal truncate rounded-xl bg-marble/10 px-6 py-4">
              {p.name}
            </li>
          ))}
          {Array.from({ length: MAX_PLAYERS - players.length }, (_, i) => (
            <li key={`open-${i}`} aria-hidden className="min-h-18 rounded-xl border-2 border-dashed border-marble/20" />
          ))}
        </ol>
      </aside>
    </>
  );
}

/** Spec stories 30–31: which question, the question itself, the time left, the options and how many are in. No scores. */
function Asked({ question, roster }: { question: QuestionStart; roster: HostState["players"] }) {
  return (
    <div className="flex min-w-0 flex-1 flex-col gap-6">
      <p className="text-3xl font-bold tabular-nums text-marble/80">
        Question {question.index + 1} of {question.total}
      </p>
      <h1 className="text-[clamp(2rem,4vw,4.5rem)] font-bold leading-tight">{question.text}</h1>
      <Timer question={question} big />
      <div className="grid flex-1 grid-cols-2 gap-6">
        {question.options.map((option, i) => (
          <div key={i} className={`flex items-center gap-4 rounded-xl p-6 text-4xl font-bold ${OPTION_COLOURS[i]}`}>
            <span aria-hidden>{OPTION_SHAPES[i]}</span>
            {option}
          </div>
        ))}
      </div>
      <div className="flex items-center gap-8">
        <p className="text-4xl font-bold tabular-nums">
          {roster.filter((player) => player.answered).length} of {roster.length} answered
        </p>
        <ul className="flex flex-wrap gap-4 text-2xl font-bold">
          {roster.map((player) => (
            <li
              key={player.id}
              className={`rounded-full px-5 py-2 transition duration-150 ${player.answered ? "bg-success" : "bg-marble/15 text-marble/70"}`}
            >
              {player.name}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

/** Spec story 32: the correct option, unmistakable without its colour (label, ring, size, ✓), then how many chose each. */
function Revealed({ question, reveal }: { question: QuestionStart; reveal: Reveal }) {
  const correct = reveal.correctOption;
  const most = Math.max(1, ...reveal.counts);
  return (
    <div className="flex min-w-0 flex-1 flex-col gap-6">
      <p className="line-clamp-2 text-3xl text-marble/80">{question.text}</p>
      <section className="flex flex-col gap-4">
        <h1 className="text-3xl font-bold">✓ Correct answer</h1>
        <p
          className={`flex items-center gap-6 rounded-xl p-8 text-6xl font-bold ring-4 ring-marble ring-offset-4 ring-offset-obsidian ${OPTION_COLOURS[correct]}`}
        >
          <span aria-hidden>{OPTION_SHAPES[correct]}</span>
          <span className="flex-1">{question.options[correct]}</span>
          <span aria-hidden>✓</span>
        </p>
      </section>
      <ol className="flex flex-1 flex-col justify-center gap-4 text-3xl font-bold">
        {question.options.map((option, i) => (
          <li key={i} className={`flex items-center gap-6 ${i === correct ? "" : "opacity-40"}`}>
            <span
              aria-hidden
              className={`flex size-14 shrink-0 items-center justify-center rounded-lg ${OPTION_COLOURS[i]}`}
            >
              {OPTION_SHAPES[i]}
            </span>
            <span className="w-2/5 truncate">
              {option}
              {i === correct && " ✓"}
            </span>
            <span className="flex-1" aria-hidden>
              <span
                className={`block h-8 rounded-full ${OPTION_COLOURS[i]}`}
                style={{ width: `${(reveal.counts[i] / most) * 100}%` }}
              />
            </span>
            <span className="w-16 text-right tabular-nums">{reveal.counts[i]}</span>
          </li>
        ))}
      </ol>
      <p className="text-3xl tabular-nums text-marble/80">
        {reveal.counts.reduce((sum, n) => sum + n, 0)} answered · {reveal.counts[correct]} correct
      </p>
    </div>
  );
}

/** Spec story 33: where everyone stands between questions, with what the last question earned; 1st gets the section line. */
function Standings({ standings }: { standings: Standing[] }) {
  return (
    <section className="flex flex-1 flex-col gap-6">
      <h1 className="text-6xl font-bold">Standings</h1>
      <div aria-hidden className="flex gap-8 px-8 text-2xl text-marble/60">
        <span className="flex-1" />
        <span className="w-48 text-right">Points</span>
        <span className="w-56 text-right">Score</span>
      </div>
      <ol className="flex flex-col gap-4">
        {standings.map((entry, i) => (
          <li
            key={entry.playerId}
            style={{ animationDelay: `${i * 80}ms` }}
            className={`reveal flex items-baseline gap-8 rounded-xl border border-marble/15 px-8 py-5 font-bold ${i === 0 ? "border-l-4 border-l-yellow text-6xl" : i < 3 ? "text-5xl" : "text-4xl"}`}
          >
            <span className={`w-16 text-right tabular-nums ${i === 0 ? "text-yellow" : "text-marble/60"}`}>
              {i + 1}
            </span>
            <span className="flex-1 truncate">{entry.name}</span>
            <span className="w-48 text-right text-4xl text-success-fg tabular-nums">+{entry.points}</span>
            <span className="w-56 text-right tabular-nums">{entry.score}</span>
          </li>
        ))}
      </ol>
    </section>
  );
}

const MEDALS = ["🥇", "🥈", "🥉"];
/** By place: 2nd, 1st, 3rd from the left as a podium stands, while the DOM keeps 1st first for screen readers. */
const PODIUM_STEPS = [
  { order: "order-2", plinth: "h-72 border-t-4 border-yellow", name: "text-6xl" },
  { order: "order-1", plinth: "h-52", name: "text-5xl" },
  { order: "order-3", plinth: "h-36", name: "text-5xl" },
];

/** The Podium (#9), revealed from the bottom up: 3rd, then 2nd, then 1st, a beat apart. */
function Podium({ podium }: { podium: Standing[] }) {
  return (
    <section className="flex flex-1 flex-col items-center justify-end gap-8">
      {/* empty when End Battle closed the lobby before anyone played */}
      <h1 className="text-6xl font-bold">{podium.length > 0 ? "Final podium" : "Battle ended"}</h1>
      <ol className="flex w-full max-w-6xl items-end justify-center gap-8">
        {podium.slice(0, REVEALED_PLACES).map((entry, i) => (
          <li
            key={entry.playerId}
            style={{ animationDelay: `${podiumRevealMs(i + 1)}ms` }}
            className={`reveal flex w-1/3 flex-col items-center gap-4 font-bold ${PODIUM_STEPS[i].order}`}
          >
            <span aria-hidden className="text-7xl">
              {MEDALS[i]}
            </span>
            <span className={`max-w-full truncate ${PODIUM_STEPS[i].name}`}>{entry.name}</span>
            <span className="text-5xl tabular-nums text-yellow">{entry.score}</span>
            <span
              className={`flex w-full justify-center rounded-t-xl bg-marble/15 pt-6 text-6xl tabular-nums text-marble/60 ${PODIUM_STEPS[i].plinth}`}
            >
              {i + 1}
            </span>
          </li>
        ))}
      </ol>
      <ol className="flex w-full max-w-xl flex-col gap-3 text-3xl">
        {podium.slice(REVEALED_PLACES).map((entry, i) => (
          <li key={entry.playerId} className="flex items-baseline gap-8">
            <span className="w-16 text-right tabular-nums text-marble/60">{i + REVEALED_PLACES + 1}</span>
            <span className="flex-1 truncate">{entry.name}</span>
            <span className="tabular-nums">{entry.score}</span>
          </li>
        ))}
      </ol>
    </section>
  );
}
