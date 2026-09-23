"use client";

import Link from "next/link";
import { JoinForm } from "@/components/JoinForm";
import { startSolo } from "@/lib/game";

/** Solo join: the join form, then straight into the Game. */
export default function Play() {
  return (
    <JoinForm
      title="Play"
      submitLabel="Start"
      join={startSolo}
      footer={
        <Link href="/join" className="text-center text-lg text-gold-300 underline">
          Have a PIN?
        </Link>
      }
    />
  );
}
