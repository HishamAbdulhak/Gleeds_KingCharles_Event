"use client";

import { useRouter } from "next/navigation";
import { FormEvent, ReactNode, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { Brandmark } from "@/components/Brandmark";
import { noSubscribe } from "@/lib/api";
import { JoinRequest, Seat, stored } from "@/lib/game";

/**
 * The form every Game starts with (name, email, and what the email is for), for Solo start and Battle join. Native
 * validation gives the inline messages; what `join` throws is shown below. Prefills the name and email this phone
 * already gave ("Play again", a second Battle). On a Seat: stores it and goes to the Player page. `children` are the
 * extra fields (the PIN).
 */
export function JoinForm({
  title,
  lead,
  submitLabel,
  join,
  children,
  footer,
}: {
  title: string;
  /** one line under the title: what this screen is for */
  lead: string;
  submitLabel: string;
  join: (req: JoinRequest, form: FormData) => Promise<Seat>;
  children?: ReactNode;
  footer?: ReactNode;
}) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const errorLine = useRef<HTMLParagraphElement>(null);
  // a wrong PIN, sent from the PIN field with the phone's keyboard up, would otherwise land below the fold
  useEffect(() => {
    if (error) errorLine.current?.scrollIntoView({ block: "nearest" });
  }, [error]);
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
    <main className="flex flex-1 flex-col items-center gap-6 p-6">
      <Brandmark className="m-4 w-25 self-start" />
      {/* key: remount when the stored name and email appear after hydration, so defaultValue takes effect */}
      <form
        key={storedName ? "prefilled" : "blank"}
        onSubmit={onSubmit}
        className="my-auto flex w-full max-w-sm flex-col gap-6"
      >
        <header className="flex flex-col gap-2">
          <p className="text-lg text-marble/80">King Charles quiz</p>
          <h1 className="text-4xl font-bold">{title}</h1>
          <p className="text-lg">{lead}</p>
        </header>
        {children}
        <label className="flex flex-col gap-2 text-lg font-bold">
          Name
          <input
            name="name"
            required
            maxLength={80}
            autoComplete="name"
            defaultValue={storedName}
            className="field field-lg"
          />
        </label>
        <label className="flex flex-col gap-2 text-lg font-bold">
          Email
          <input
            name="email"
            type="email"
            required
            autoComplete="email"
            defaultValue={storedEmail}
            className="field field-lg"
          />
        </label>
        {/* placeholder until Gleeds approves the wording (docs/adr/0003) */}
        <p className="-mt-2 text-base text-marble/80">
          Your email is only used to identify you for today&apos;s prize, and is deleted after the event.
        </p>
        {error && (
          <p ref={errorLine} role="alert" className="alert">
            {error}
          </p>
        )}
        <div className="flex flex-col gap-4">
          <button type="submit" disabled={busy} className="btn btn-primary btn-lg">
            {busy ? "One moment…" : submitLabel}
          </button>
          {footer}
        </div>
      </form>
    </main>
  );
}
