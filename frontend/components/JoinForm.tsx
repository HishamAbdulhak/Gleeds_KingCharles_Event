"use client";

import { useRouter } from "next/navigation";
import { FormEvent, ReactNode, useState, useSyncExternalStore } from "react";
import { noSubscribe } from "@/lib/api";
import { JoinRequest, Seat, stored } from "@/lib/game";

export const inputClass = "rounded bg-cream p-3 text-lg text-royal-900";

/**
 * The form every Game starts with (name, email, and what the email is for), for Solo start and Battle join. Native
 * validation gives the inline messages; what `join` throws is shown below. Prefills the name and email this phone
 * already gave ("Play again", a second Battle). On a Seat: stores it and goes to the Player page. `children` are the
 * extra fields (the PIN).
 */
export function JoinForm({
  title,
  submitLabel,
  join,
  children,
  footer,
}: {
  title: string;
  submitLabel: string;
  join: (req: JoinRequest, form: FormData) => Promise<Seat>;
  children?: ReactNode;
  footer?: ReactNode;
}) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const saved = useSyncExternalStore(
    noSubscribe,
    () => stored.get("joinForm"),
    () => null,
  );
  const { name: storedName, email: storedEmail }: Partial<JoinRequest> = saved ? JSON.parse(saved) : {};

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const form = new FormData(e.currentTarget);
    const name = String(form.get("name")).trim();
    const email = String(form.get("email")).trim();
    try {
      const { gameId, playerId, sessionToken } = await join({ name, email }, form);
      stored.set(`seat:${gameId}`, sessionToken);
      stored.set(`player:${gameId}`, playerId); // which row of a Battle's Podium is this phone's (#9)
      stored.set("joinForm", JSON.stringify({ name, email }));
      router.replace(`/play/${gameId}`);
    } catch (err) {
      setError((err as Error).message || "Could not reach the server.");
      setBusy(false);
    }
  }

  return (
    <main className="flex flex-1 items-center justify-center p-6">
      {/* key: remount when the stored name and email appear after hydration, so defaultValue takes effect */}
      <form
        key={storedName ? "prefilled" : "blank"}
        onSubmit={onSubmit}
        className="flex w-full max-w-sm flex-col gap-4"
      >
        <h1 className="text-3xl font-bold text-gold-500">{title}</h1>
        {children}
        <label className="flex flex-col gap-1 text-lg">
          Name
          <input
            name="name"
            required
            maxLength={80}
            autoComplete="name"
            defaultValue={storedName}
            className={inputClass}
          />
        </label>
        <label className="flex flex-col gap-1 text-lg">
          Email
          <input
            name="email"
            type="email"
            required
            autoComplete="email"
            defaultValue={storedEmail}
            className={inputClass}
          />
        </label>
        {/* placeholder until Gleeds approves the wording (docs/adr/0003) */}
        <p className="text-lg text-cream/80">
          Your email is only used to identify you for today&apos;s prize, and is deleted after the event.
        </p>
        {error && (
          <p role="alert" className="text-lg text-red-300">
            {error}
          </p>
        )}
        <button
          type="submit"
          disabled={busy}
          className="rounded bg-gold-500 p-4 text-xl font-bold text-royal-900 disabled:opacity-50"
        >
          {busy ? "One moment…" : submitLabel}
        </button>
        {footer}
      </form>
    </main>
  );
}
