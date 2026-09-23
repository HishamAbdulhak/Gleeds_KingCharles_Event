# 0003: The email identifies a Player; it is not a Lead

Status: accepted (2026-09-23, issue #34)

## Context

The spec was written with lead capture as the stand's purpose: name, email and marketing consent at join, and a deduplicated Leads CSV for Gleeds' marketing. Gleeds has no use for a follow-up list from this game, and prizes are handed out on the day. The email still does work nothing else does. The Day Leaderboard keys on it, so a Replay counts once, with its best Score. And a second join with the same email in a Game returns the same Seat.

## Decision

- Players still give a name and an email, but the email is only an identifier. There is no Lead, no Leads export and no marketing consent.
- The join form shows a one-line notice instead of a consent checkbox: the email identifies the Player for today's prize and is deleted after the event. The wording is a placeholder until Gleeds approves it.
- The email never leaves the server, Admins included. The prize winner proves it with their phone. The final screen shows their name and their best Score today, which is the Score under their email, not just that Game's. Staff match both to the top of the live Day Leaderboard. A name alone would not do: anyone can read the winning name off the big screen and join under it, but their best Score today is their own.
- Retention: the event database is wiped once prizes are handed out. There is no purge feature.

## Considered options

- **An Admin view with the top Players' emails, so staff check the winner's email.** This survives a closed tab, but it would be the first time an email left the server. Not chosen: the phone is the proof.
- **An Admin purge button.** Not needed for a one-day event whose database is dropped afterwards.
- **A reworded checkbox for the prize terms.** Consent is no longer the basis for holding the email, so there is nothing to agree to; a notice is what the Player is owed.

## Consequences

- #10 loses the Leads export (`leads.csv`, `/admin/leads`). Reset keeps past Games; there are no Leads to keep.
- `consent` leaves the join request, and `consented_at` leaves `player`.
- The final screens change: both Modes show the Player's name and best Score today, which the Battle phone does not show at all yet. The best Score comes from the same email-keyed query as the Solo rank.
- A winner who closes the tab loses the screen they would show, and with no email visible to staff there is no fallback. The stand should say so ("keep this screen open to claim your prize"). A winner who replays still has a proof when the new Game ends, because the best Score belongs to the email, not to one Game.
- Anyone who knows the winner's email can join under it and show the same proof. That is the accepted limit of a proof without accounts.
- The rank on a final screen is frozen when that Game ends. At prize time, the live board is authoritative.
- The wipe is a manual step for whoever runs the event; nothing in the app enforces it.
