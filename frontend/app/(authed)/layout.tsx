"use client";

import { useRouter } from "next/navigation";
import { ReactNode, useEffect, useSyncExternalStore } from "react";
import { getToken, noSubscribe } from "@/lib/api";

/** Every route in this group needs an admin token; without one it goes to login before anything renders. */
export default function AuthedLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  // false on the server render, the real answer on the client; gates rendering only
  const hasToken = useSyncExternalStore(
    noSubscribe,
    () => getToken() !== null,
    () => false,
  );

  // reads localStorage itself: hasToken is still the server's false in the first effect after hydration
  useEffect(() => {
    if (!getToken()) router.replace("/admin/login");
  }, [router]);

  return hasToken ? children : null;
}
