# Design system

The tokens and classes live in `frontend/app/globals.css`; this file says when to use which. Everything follows the Gleeds brand guidelines (July 2024, v1): palette, typeface, sentence case, restraint, the WCAG contrast floor (4.5:1 body, 3:1 large or bold) and the brandmark rules (`docs/adr/0005`).

Three surfaces, three densities:

| Surface | Where | Viewed from | Rule |
| --- | --- | --- | --- |
| Player | `/play`, `/join`, `/play/[gameId]` | a phone in the hand, 360 px wide | one task per screen, thumb-sized targets, immediate feedback |
| Host | `/host`, `/host/[gameId]` | a 1920×1080 screen, 5 m away | readability over density; nothing under `text-2xl` |
| Admin | `/admin/**` | a desktop | dense is fine; `text-sm` body |

## Colour

The guidelines' palette only. A tint is the colour mixed with white (50% = halfway to white), as the guidelines print it.

| Token | Use | Never |
| --- | --- | --- |
| `obsidian` `#3C3C3C` | page and panels; text on yellow and marble | — |
| `marble` `#F5F5F5` | text; `/80` secondary, `/60` tertiary (4.8:1), `/10`–`/40` borders, tracks, chips; form fields | — |
| `yellow` `#F7C400` | Gleeds yellow, the one accent, used sparingly: primary action, key data (PIN, timer, place, best score, podium scores), links, the focus ring | tints; titles; more than a few yellow things on one screen |
| `success` (patina) | correct: fill under marble text | "answered" (that is marble: the phone's Locked ring, the Host's filled name chip), so green never reads as right before the Reveal |
| `success-fg` (patina 50%) | success text on obsidian (saved, points gained, "Battle ready") | as a fill |
| `danger` (copper) | wrong / time's up, the urgent timer, Reconnecting…, destructive buttons: fill under **black** text (marble on copper is 3.8:1); the border of an invalid Player field | marble text on it |
| `danger-fg` (copper 50%) | error text on obsidian | as a fill |
| `copper`, `titanium`, `yellow`, `patina` | the four answer options (`OPTION_COLOURS` in `AnswerGrid.tsx`) | anywhere else, outside the status roles above |

Proportion, as in the guidelines: obsidian and marble carry each screen; yellow and the secondaries stay under a quarter of it. The answer grid is the exception: four colours, each with its shape (▲ ◆ ● ■), are the game, so colour is never the only signal.

## Type

`font-sans` is Helvetica Neue, then Helvetica, then Arial: the Gleeds primary font, then its system font. Phones without either fall back to their own sans.

- **Titles are marble.** Headlines take a primary colour, so yellow is kept for key data.
- **Sentence case** everywhere, never all caps (Gleeds: the brandmark is lowercase, so are we).
- **Bold is for reading distance, not decoration.** Host text is bold so it carries 5 m. Player titles and numbers are bold. Admin body copy is regular.
- **Numbers** that change or line up (scores, ranks, timer, PIN, counts) are `tabular-nums`. Scores are grouped the British way (`formatNumber` in `lib/game.ts`: 12,345) on the phone and the big screen alike.

| Role | Player | Host | Admin |
| --- | --- | --- | --- |
| Screen title | `text-4xl font-bold` | `text-6xl font-bold`; Reveal's is the `text-4xl` "Correct answer" label, because the tile under it is the headline | `text-2xl font-bold` |
| Question | `text-2xl font-bold leading-snug` | `text-[clamp(2rem,4vw,4.5rem)] font-bold leading-tight` | — |
| Key data | `text-4xl`–`text-6xl font-bold tabular-nums`: yellow for the one figure that matters most on the screen (timer, Battle place, Best today), marble for the rest (Your score, Rank today, Result points) | PIN `clamp(5rem,11vw,14rem)` yellow; leaderboard rows `text-5xl`; Standings rows by place: 1st `text-6xl`, 2nd–3rd `text-5xl`, the rest `text-4xl` | — |
| Ranks, counters | `text-marble/80` | `text-marble/60` ranks, `/80` counters | table headers `text-marble/60` |
| Body | `text-lg`, secondary `text-marble/80`; hints and stat labels `text-base text-marble/80` | `text-2xl`–`text-4xl` | `text-sm`, hints `text-xs text-marble/60` |

## Brandmark

`<Brandmark />` (`components/Brandmark.tsx`) is the Gleeds brandmark with its paths copied unaltered from the guidelines. Never retype, redraw or recolour it.

- White on our obsidian. The guidelines allow only black, obsidian or white.
- Top left. Centre it only where it is the focus.
- At least 100 px wide (`w-25`). The Host uses `w-32`.
- Clear space all round of at least its own height (0.37 × width). The caller's padding provides it: 40 px for `w-25`, 48 px for `w-32`.
- Shown on the join forms (`/play`, `/join`), `/admin/login`, `/host` and `/host/[gameId]`. It stays off the in-game phone screens, where the question needs the room.

## Space, radius, depth

- **Spacing** is Tailwind's 4 px scale. Page gutter: Player `p-4`/`p-6`, Admin `p-8`, Host `p-12`. Gaps between stacked blocks: Player `gap-4`/`gap-6`, Host `gap-8`, Admin `gap-6`.
- **Radius**: `rounded-lg` (8 px) on controls (buttons, fields), `rounded-xl` (12 px) on tiles and panels (answer tiles, result banner, QR plate, dialogs), `rounded-full` on pills and tracks (timer, roster chips).
- **Depth** comes from a `marble/15` hairline, not shadows or lighter surfaces; the guidelines tint toward white, which would wash a panel out. The only overlay is the dialog backdrop (`black/60`). Two Host exceptions are solid shapes rather than panels: the lobby's name rows (`marble/10`) and the Podium plinths (`marble/15`), which need a body to read as places from 5 m.

## Buttons

`.btn` plus one colour plus, off the admin, one size:

| Class | Use |
| --- | --- |
| `btn-primary` | yellow, obsidian text: the one forward action on a screen (Start, Join, Reveal, Next, Save); hover turns marble |
| `btn-secondary` | marble outline: the alternative beside it (End Battle), or a staff action on a screen whose call to action isn't a button (Host a Battle, under the QR tile) |
| `btn-danger` | copper, black text: destructive and irreversible (Reset the Day Leaderboard) |
| `btn-lg` | Player: 56 px minimum height, full width in forms |
| `btn-xl` | Host |
| `.link` | yellow underline: navigation and in-row actions (Edit, Play solo); low-emphasis links (Cancel, Log out) stay `text-marble/80 underline` |

One primary per screen. A destructive in-row action is `text-danger-fg underline`.

## Cards

1. **Page**: obsidian.
2. **Panel** (`.panel`): obsidian + `marble/15` hairline, `rounded-xl`, `p-6`. Used for dialogs, and on the phone for the Game-over card, the error screen and the reopened "Answer locked in" status.
3. **Tile**: a solid colour that *is* the content: answer options, the result banner (`success` / `danger`), the QR plate (marble). The phone's answer tile is a row: `min-h-20 px-5 py-4 text-xl font-bold`, the shape in a fixed `w-8` column, `gap-4` between tiles, one column below `sm` and two above.

No section lines (a coloured rule along a panel's or row's leading edge): the user found them unprofessional, on the phone and on the big screen. Emphasis comes from size, weight and yellow on key data.

## States

| State | Look |
| --- | --- |
| Focus | 3 px yellow outline, 3 px offset, on every focusable element (global `:focus-visible`) |
| Hover | primary turns marble; secondary gets a `marble/10` wash; links turn marble. Tailwind limits hover to pointer devices. |
| Pressed | `scale-[0.98]` on buttons, `scale-[0.97]` on answer tiles. This is the phone's instant "got it" before the server answers. |
| Answered | On the phone, Selected below. On the Host's question screen, a Player's name chip fills marble (obsidian text) once they answer; a chip still waiting is a `marble/30` outline. |
| Selected | `ring-4 ring-marble ring-offset-4 ring-offset-obsidian`. The offset keeps the ring legible on every tile; the unselected tiles dim to `opacity-40`. On the phone one tap is the Answer, so Selected is Locked: the tile also carries an obsidian "Locked" pill, and the status line says "Answer locked in". |
| Disabled | `opacity-40`, `cursor-not-allowed`, no hover or press |
| Correct / wrong | `success` fill with marble text / `danger` fill with black text. The correct tile stays full strength, the rest dim. On the Host's Reveal the correct option keeps its own colour and takes the Selected ring plus the phone's obsidian ✓ badge (`BADGE` in `AnswerGrid.tsx`) and the "Correct answer" label, so it never rests on colour alone; no Player selects on the big screen, so the ring can't be misread. On the phone the Result banner is the fill; in the grid the correct tile gets an obsidian ✓ badge and a wrong pick keeps its ring, an ✕ badge and `opacity-70`. A timeout has no pick: only the ✓. |
| Lobby | "Waiting for N more player(s)" in `marble/80`, then "✓ Battle ready" in `success-fg` once Start Battle opens, on the Host and every phone at once; the line is keyed on readiness so the change enters (`.reveal`). Counts read "2 of 4 players", like "Question 1 of 10" and "2 of 3 answered". |
| Place | Words and medals, the same on both screens: 🥇 🥈 🥉 and "1st"–"4th" (`PLACES`, `MEDALS` in `lib/game.ts`). The Host's plinths carry the ordinal; the phone shows its own medal over its yellow ordinal. Standings keep bare numbers, since they are a table. |
| Waiting | The phone's waits are one screen: a `text-4xl` "Look at the big screen" over a `marble/80` line saying what is coming ("The standings are up", "Your place is coming up…"). A Result says "Next question coming up…", or "That was the last question" after the last one. |
| Urgent | the timer turns `danger` for the last 5 s; on the phone its pill also pulses (`motion-safe:animate-pulse`), the big screen stays still |
| Offline | `Reconnecting…` bar, `danger`, fixed to the top. The phone adds "Stay on this page. It reconnects by itself." and dims its answer tiles, which can't be tapped until it's back |
| Invalid field | `.field-lg` takes a 2 px `danger` border once the visitor has left it invalid (`user-invalid`); the browser's own message says why. Errors in words are `.alert` (`danger-fg` text); a form error also carries `role="alert"` |

## Motion

- **150 ms ease** for feedback (hover, press, dim). It should feel instant.
- **200 ms fade** (`.screen-in`) as each Host screen replaces the last; short enough that the next tap never waits on it.
- **400 ms ease-out** for entrances (`.reveal`), staggered by the caller: the Podium's `podiumRevealMs`, 80 ms between Standings rows, and a Player's row in the lobby as they join.
- Motion explains a change of state; it never decorates idle screens and never delays input.
- `prefers-reduced-motion`: entrances fade without moving. Timing is unchanged, because the Podium's order *is* the suspense.

## Not yet used

The guidelines' landscape renders and vectors (0.25 pt lines in one palette colour) suit Host backgrounds. They come only as supplied files from the Brand Hub, and none are in the repo yet.
