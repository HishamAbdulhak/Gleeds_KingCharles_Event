"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ReactNode } from "react";
import { Brandmark } from "@/components/Brandmark";
import { logout } from "@/lib/api";

const NAV = [
  { href: "/admin", label: "Dashboard" },
  { href: "/admin/questions", label: "Question Bank" },
  { href: "/admin/settings", label: "Settings" },
  { href: "/host", label: "Host screen" },
] as const;

/** Every Admin page: the brandmark, one row of navigation with the current page marked, and Log out. */
export default function AdminLayout({ children }: { children: ReactNode }) {
  const path = usePathname();
  return (
    <div className="flex flex-1 flex-col gap-6 p-4 sm:p-8">
      {/* the brandmark's clear space is its own height, 37 px at w-25: m-6 + p-4 on a phone, m-2 + p-8 above, gap-10 beside it */}
      <header className="flex flex-wrap items-center gap-10 text-sm">
        <Brandmark className="m-6 w-25 sm:m-2" />
        <nav aria-label="Admin" className="flex flex-1 flex-wrap gap-x-6 gap-y-2">
          {NAV.map(({ href, label }) =>
            href === path ? (
              <span key={href} aria-current="page">
                {label}
              </span>
            ) : (
              <Link key={href} href={href} className="link">
                {label}
              </Link>
            ),
          )}
        </nav>
        <button
          type="button"
          onClick={logout}
          className="text-marble/80 underline underline-offset-4 transition-colors duration-150 hover:text-marble"
        >
          Log out
        </button>
      </header>
      {children}
    </div>
  );
}
