"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState, useSyncExternalStore } from "react";
import { noSubscribe } from "@/lib/api";
import { startSolo, stored } from "@/lib/game";

const inputClass = "rounded bg-cream p-3 text-lg text-royal-900";

/**
 * Solo join: name, email, consent. Native validation gives the inline messages; the server's 400 is shown below.
 * "Play again" lands here with the Lead this phone already gave prefilled.
 */
export default function Play() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const storedName = useSyncExternalStore(
    noSubscribe,
    () => stored.get("lead:name"),
    () => null,
  );
  const storedEmail = useSyncExternalStore(
    noSubscribe,
    () => stored.get("lead:email"),
    () => null,
  );

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const form = new FormData(e.currentTarget);
    const name = String(form.get("name")).trim();
    const email = String(form.get("email")).trim();
    try {
      const { gameId, sessionToken } = await startSolo(name, email, form.get("consent") === "on");
      stored.set(`seat:${gameId}`, sessionToken);
      stored.set("lead:name", name);
      stored.set("lead:email", email);
      router.replace(`/play/${gameId}`);
    } catch (err) {
      setError((err as Error).message || "Could not reach the server.");
      setBusy(false);
    }
  }

  return (
    <main className="flex flex-1 items-center justify-center p-6">
      {/* key: remount when the stored Lead appears after hydration, so defaultValue takes effect */}
      <form
        key={storedName ? "prefilled" : "blank"}
        onSubmit={onSubmit}
        className="flex w-full max-w-sm flex-col gap-4"
      >
        <h1 className="text-3xl font-bold text-gold-500">Play</h1>
        <label className="flex flex-col gap-1 text-sm">
          Name
          <input
            name="name"
            required
            maxLength={80}
            autoComplete="name"
            defaultValue={storedName ?? undefined}
            className={inputClass}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Email
          <input
            name="email"
            type="email"
            required
            autoComplete="email"
            defaultValue={storedEmail ?? undefined}
            className={inputClass}
          />
        </label>
        <label className="flex items-start gap-3 text-sm">
          <input name="consent" type="checkbox" required className="mt-1 size-5 accent-gold-500" />
          <span>I agree that Gleeds may contact me by email. {/* placeholder for Gleeds' legal text */}</span>
        </label>
        {error && (
          <p role="alert" className="text-sm text-red-300">
            {error}
          </p>
        )}
        <button
          type="submit"
          disabled={busy}
          className="rounded bg-gold-500 p-4 text-xl font-bold text-royal-900 disabled:opacity-50"
        >
          {busy ? "Starting…" : "Start"}
        </button>
        <Link href="/join" className="text-center text-sm text-gold-300 underline">
          Have a PIN?
        </Link>
      </form>
    </main>
  );
}
