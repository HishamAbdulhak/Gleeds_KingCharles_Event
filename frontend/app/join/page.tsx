"use client";

import Link from "next/link";
import { JoinForm } from "@/components/JoinForm";
import { joinBattle } from "@/lib/game";

/** The server's 409 codes (BattleController.join) in the visitor's words; a 404 already reads as one. */
const REFUSALS: Record<string, string> = {
  LOBBY_FULL: "That Battle is full — four Players are already in. Ask the Host for the next one.",
  GAME_STARTED: "That Battle has already started. Ask the Host for the next one.",
};

/** Battle join (spec story 15): the PIN from the big screen plus the join form. */
export default function Join() {
  return (
    <JoinForm
      title="Join a Battle"
      lead="Enter the Battle PIN from the big screen."
      submitLabel="Join"
      join={(req, form) =>
        joinBattle(String(form.get("pin")), req).catch((err: Error) => {
          throw new Error(REFUSALS[err.message] ?? err.message);
        })
      }
      footer={
        <Link href="/play" className="link text-center text-lg">
          No PIN? Play solo
        </Link>
      }
    >
      <label className="flex flex-col gap-2 text-lg font-bold">
        Battle PIN
        <input
          name="pin"
          inputMode="numeric"
          pattern="[0-9]{6}"
          maxLength={6}
          required
          autoFocus
          autoComplete="off"
          enterKeyHint="next"
          title="6 digits"
          aria-describedby="pin-hint"
          className="field field-lg min-h-20 text-center text-5xl font-bold tabular-nums tracking-[0.3em]"
        />
        <span id="pin-hint" className="text-base font-normal text-marble/80">
          6 digits
        </span>
      </label>
    </JoinForm>
  );
}
