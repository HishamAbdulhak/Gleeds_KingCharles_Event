"use client";

import Link from "next/link";
import { inputClass, JoinForm } from "@/components/JoinForm";
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
      submitLabel="Join"
      join={(req, form) =>
        joinBattle(String(form.get("pin")), req).catch((err: Error) => {
          throw new Error(REFUSALS[err.message] ?? err.message);
        })
      }
      footer={
        <Link href="/play" className="text-center text-sm text-gold-300 underline">
          No PIN? Play solo
        </Link>
      }
    >
      <label className="flex flex-col gap-1 text-sm">
        PIN
        <input
          name="pin"
          inputMode="numeric"
          pattern="[0-9]{6}"
          maxLength={6}
          required
          autoComplete="off"
          placeholder="6 digits"
          className={`${inputClass} text-center text-3xl tracking-[0.4em]`}
        />
      </label>
    </JoinForm>
  );
}
