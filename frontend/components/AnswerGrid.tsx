// Kahoot-style: one colour and shape per option so a Player can aim by colour, not by reading. The big screen
// shows the same four, so the phone and the room are talking about the same option. The colours are Gleeds yellow and
// the three secondaries; each text colour is ≥ 4.5:1 on its background (#11): marble fails on copper and yellow.
export const OPTION_COLOURS = [
  "bg-copper text-black",
  "bg-titanium text-marble",
  "bg-yellow text-obsidian",
  "bg-patina text-marble",
];
export const OPTION_SHAPES = ["▲", "◆", "●", "■"];

type Props = {
  options: string[];
  /** The Player's locked Answer, if any. */
  selected: number | null;
  /** Known only once RESULT has arrived; highlights that option and dims the rest. */
  correctOption?: number;
  /** Absent while the question is closed to Answers (locked, result). */
  onSelect?: (option: number) => void;
};

/**
 * Four big thumb-friendly option rows. Disables on the first tap; the caller re-enables by passing onSelect again.
 * Each state has a cue besides colour: the Player's tile keeps a ring and says "Locked" until RESULT, which marks the
 * correct tile ✓ and a wrong pick ✕ (a timeout has no pick, so only the ✓). `scroll-mt-20` keeps a focused tile clear of
 * the phone's sticky timer.
 */
export function AnswerGrid({ options, selected, correctOption, onSelect }: Props) {
  const revealed = correctOption !== undefined;
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
      {options.map((option, i) => {
        const mine = selected === i;
        const opacity = revealed
          ? i === correctOption
            ? ""
            : mine
              ? "opacity-70"
              : "opacity-40"
          : (selected === null && !onSelect) || (selected !== null && !mine) // offline, or another tile is locked
            ? "opacity-40"
            : "";
        return (
          <button
            key={i}
            type="button"
            disabled={!onSelect}
            onClick={() => onSelect?.(i)}
            aria-pressed={mine}
            className={`flex min-h-20 items-center gap-4 scroll-mt-20 rounded-xl px-5 py-4 text-left text-xl font-bold transition duration-150 enabled:active:scale-[0.97] disabled:cursor-not-allowed ${OPTION_COLOURS[i]} ${opacity} ${mine ? "ring-4 ring-marble ring-offset-4 ring-offset-obsidian" : ""}`}
          >
            <span aria-hidden className="w-8 shrink-0 text-center text-3xl">
              {OPTION_SHAPES[i]}
            </span>
            <span className="min-w-0 flex-1 wrap-anywhere">{option}</span>
            <Badge revealed={revealed} correct={i === correctOption} mine={mine} />
          </button>
        );
      })}
    </div>
  );
}

/** The ✓ / ✕ / Locked mark; the big screen's Reveal puts the same ✓ on its correct tile. */
export const BADGE = "flex shrink-0 items-center justify-center rounded-full bg-obsidian font-bold text-marble";

function Badge({ revealed, correct, mine }: { revealed: boolean; correct: boolean; mine: boolean }) {
  if (revealed && (correct || mine)) {
    return (
      <span className={`${BADGE} size-10 text-2xl`}>
        <span aria-hidden>{correct ? "✓" : "✕"}</span>
        <span className="sr-only">
          {correct ? (mine ? "Correct answer, your answer" : "Correct answer") : "Your answer, wrong"}
        </span>
      </span>
    );
  }
  if (!revealed && mine) {
    return <span className={`${BADGE} px-3 py-1 text-base`}>Locked</span>;
  }
  return null;
}
