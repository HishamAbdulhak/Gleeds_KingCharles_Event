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

/** Four big thumb-friendly option buttons. Disables on the first tap; the caller re-enables by passing onSelect again. */
export function AnswerGrid({ options, selected, correctOption, onSelect }: Props) {
  return (
    <div className="grid flex-1 grid-cols-1 gap-3 sm:grid-cols-2">
      {options.map((option, i) => {
        const dim = correctOption !== undefined ? i !== correctOption : selected !== null && i !== selected;
        return (
          <button
            key={i}
            type="button"
            disabled={!onSelect}
            onClick={() => onSelect?.(i)}
            aria-pressed={selected === i}
            className={`flex min-h-16 flex-col items-center justify-center gap-1 rounded-xl p-3 text-lg font-semibold transition duration-150 enabled:active:scale-[0.98] ${OPTION_COLOURS[i]} ${dim ? "opacity-40" : ""} ${selected === i ? "ring-4 ring-marble ring-offset-4 ring-offset-obsidian" : ""}`}
          >
            <span aria-hidden className="text-2xl">
              {OPTION_SHAPES[i]}
            </span>
            {option}
          </button>
        );
      })}
    </div>
  );
}
