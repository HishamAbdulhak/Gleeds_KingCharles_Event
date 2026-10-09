"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { Brandmark } from "@/components/Brandmark";
import { login } from "@/lib/api";

export default function AdminLogin() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const form = new FormData(e.currentTarget);
    try {
      await login(String(form.get("email")), String(form.get("password")));
      router.replace("/admin");
    } catch (err) {
      setError(
        (err as { status?: number }).status === 401 ? "Wrong email or password." : "Could not reach the server.",
      );
      setBusy(false);
    }
  }

  return (
    <main className="flex flex-1 flex-col items-center gap-8 p-8">
      <Brandmark className="m-2 w-25 self-start" />
      <form onSubmit={onSubmit} className="my-auto flex w-full max-w-sm flex-col gap-4">
        <h1 className="text-2xl font-bold">Admin login</h1>
        <label className="flex flex-col gap-1 text-sm">
          Email
          <input name="email" type="email" required autoComplete="username" className="field" />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Password
          <input name="password" type="password" required autoComplete="current-password" className="field" />
        </label>
        {error && (
          <p role="alert" className="text-sm text-danger-fg">
            {error}
          </p>
        )}
        <button type="submit" disabled={busy} className="btn btn-primary">
          {busy ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </main>
  );
}
