// Kahoot-style: one colour and shape per option so a Player can aim by colour, not by reading. The big screen
// shows the same four, so the phone and the room are talking about the same option. Each text colour is ≥ 4.5:1 on its
// background (#11): white fails on yellow.
export const OPTION_COLOURS = [
  "bg-red-600 text-white",
  "bg-blue-600 text-white",
  "bg-yellow-500 text-royal-900",
  "bg-saudi text-white",
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
            className={`flex min-h-16 flex-col items-center justify-center gap-1 rounded-lg p-3 text-lg font-semibold ${OPTION_COLOURS[i]} ${dim ? "opacity-40" : ""} ${selected === i ? "ring-4 ring-cream" : ""}`}
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
