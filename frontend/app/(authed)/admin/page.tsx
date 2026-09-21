"use client";

import Link from "next/link";
import { logout } from "@/lib/api";

export default function AdminHome() {
  return (
    <main className="flex flex-1 flex-col gap-6 p-8">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gold-500">Admin</h1>
        <button type="button" onClick={logout} className="text-sm text-cream/80 underline">
          Log out
        </button>
      </header>
      <nav className="flex flex-col gap-2">
        <Link href="/admin/questions" className="text-gold-300 underline">
          Question Bank
        </Link>
        <Link href="/host" className="text-gold-300 underline">
          Host screen
        </Link>
      </nav>
      <p className="text-cream/80">Settings and Leads will appear here.</p>
    </main>
  );
}
