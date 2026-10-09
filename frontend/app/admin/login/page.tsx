"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useRef, useState } from "react";
import { Brandmark } from "@/components/Brandmark";
import { login } from "@/lib/api";

type FieldErrors = { email?: string; password?: string };

export default function AdminLogin() {
  const router = useRouter();
  const password = useRef<HTMLInputElement>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const email = form.elements.namedItem("email") as HTMLInputElement;
    const errors: FieldErrors = {
      email: !email.value.trim()
        ? "Enter your email address."
        : email.validity.typeMismatch
          ? "Enter a valid email address, like name@gleeds.com."
          : undefined,
      password: password.current!.value ? undefined : "Enter your password.",
    };
    setFieldErrors(errors);
    setError(null);
    if (errors.email || errors.password) {
      (errors.email ? email : password.current!).focus();
      return;
    }

    setBusy(true);
    try {
      await login(email.value, password.current!.value);
      router.replace("/admin");
    } catch (err) {
      const status = (err as { status?: number }).status;
      setError(
        status === 401
          ? "Wrong email or password."
          : status === undefined
            ? "Couldn't reach the server. Check your connection and try again."
            : `Sign-in failed (error ${status}). Try again in a moment.`,
      );
      setBusy(false);
      if (status === 401)
        requestAnimationFrame(() => {
          // after the fieldset re-enables
          password.current?.focus();
          password.current?.select();
        });
    }
  }

  return (
    <main className="flex flex-1 flex-col items-center gap-8 p-4 sm:p-8">
      <Brandmark className="m-6 w-25 self-start sm:m-2" />
      <form onSubmit={onSubmit} noValidate aria-busy={busy} className="my-auto w-full max-w-sm">
        <fieldset disabled={busy} className="flex flex-col gap-4">
          <h1 className="text-2xl">Admin login</h1>
          <label className="flex flex-col gap-1 text-sm">
            Email
            <input
              name="email"
              type="email"
              autoComplete="username"
              aria-invalid={!!fieldErrors.email}
              aria-describedby={fieldErrors.email ? "email-error" : undefined}
              className="field"
            />
            {fieldErrors.email ? (
              <span id="email-error" className="text-danger-fg">
                {fieldErrors.email}
              </span>
            ) : null}
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Password
            <input
              ref={password}
              name="password"
              type="password"
              autoComplete="current-password"
              aria-invalid={!!fieldErrors.password}
              aria-describedby={fieldErrors.password ? "password-error" : undefined}
              className="field"
            />
            {fieldErrors.password ? (
              <span id="password-error" className="text-danger-fg">
                {fieldErrors.password}
              </span>
            ) : null}
          </label>
          {error ? (
            <p role="alert" className="text-sm text-danger-fg">
              {error}
            </p>
          ) : null}
          <button type="submit" className="btn btn-primary">
            {busy ? "Signing in…" : "Sign in"}
          </button>
        </fieldset>
      </form>
    </main>
  );
}
