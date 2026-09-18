"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { clearToken, getToken, useToken } from "@/lib/api";

export default function AdminHome() {
  const router = useRouter();
  const token = useToken();

  // Read localStorage directly: during hydration `token` is still the server snapshot (null).
  useEffect(() => {
    if (!getToken()) router.replace("/admin/login");
  }, [router]);

  if (!token) return null;

  return (
    <main className="flex flex-1 flex-col gap-6 p-8">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gold-500">Admin</h1>
        <Link href="/admin/login" onClick={clearToken} className="text-sm text-cream/80 underline">
          Log out
        </Link>
      </header>
      <p className="text-cream/80">Question Bank, Settings and Leads will appear here.</p>
    </main>
  );
}
