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
| `success` (patina) | correct, answered: fill under marble text | — |
| `success-fg` (patina 50%) | success text on obsidian (saved, points gained) | as a fill |
| `danger` (copper) | wrong / time's up, the urgent timer, Reconnecting…, destructive buttons: fill under **black** text (marble on copper is 3.8:1) | marble text on it |
| `danger-fg` (copper 50%) | error text on obsidian | as a fill |
| `copper`, `titanium`, `yellow`, `patina` | the four answer options (`OPTION_COLOURS` in `AnswerGrid.tsx`) | anywhere else, outside the status roles above |

Proportion, as in the guidelines: obsidian and marble carry each screen; yellow and the secondaries stay under a quarter of it. The answer grid is the exception: four colours, each with its shape (▲ ◆ ● ■), are the game, so colour is never the only signal.

## Type

`font-sans` is Helvetica Neue, then Helvetica, then Arial: the Gleeds primary font, then its system font. Phones without either fall back to their own sans.

- **Titles are marble.** Headlines take a primary colour, so yellow is kept for key data.
- **Sentence case** everywhere, never all caps (Gleeds: the brandmark is lowercase, so are we).
- **Bold is for reading distance, not decoration.** Host text is bold so it carries 5 m. Player titles and numbers are bold. Admin body copy is regular.
- **Numbers** that change or line up (scores, ranks, timer, PIN, counts) are `tabular-nums`.

| Role | Player | Host | Admin |
| --- | --- | --- | --- |
| Screen title | `text-3xl font-bold` | `text-6xl font-bold` | `text-2xl font-bold` |
| Question | `text-2xl font-bold leading-snug` | `text-[clamp(2rem,4vw,4.5rem)] font-bold leading-tight` | — |
| Key data | `text-4xl`–`text-6xl font-bold tabular-nums text-yellow` | PIN `clamp(5rem,11vw,14rem)` yellow; rows `text-5xl` | — |
| Ranks, counters | `text-marble/80` | `text-marble/60` ranks, `/80` counters | table headers `text-marble/60` |
| Body | `text-lg`, secondary `text-marble/80` | `text-2xl`–`text-4xl` | `text-sm`, hints `text-xs text-marble/60` |

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
- **Depth** comes from a `marble/15` hairline, not shadows or lighter surfaces; the guidelines tint toward white, which would wash a panel out. The only overlay is the dialog backdrop (`black/60`).

## Buttons

`.btn` plus one colour plus, off the admin, one size:

| Class | Use |
| --- | --- |
| `btn-primary` | yellow, obsidian text: the one forward action on a screen (Start, Join, Reveal, Next, Save); hover turns marble |
| `btn-secondary` | marble outline: the alternative beside it (End Battle) |
| `btn-danger` | copper, black text: destructive and irreversible (Reset the Day Leaderboard) |
| `btn-lg` | Player: 56 px minimum height, full width in forms |
| `btn-xl` | Host |
| `.link` | yellow underline: navigation and in-row actions (Edit, Play solo); low-emphasis links (Cancel, Log out) stay `text-marble/80 underline` |

One primary per screen. A destructive in-row action is `text-danger-fg underline`.

## Cards

1. **Page**: obsidian.
2. **Panel** (`.panel`): obsidian + `marble/15` hairline, `rounded-xl`, `p-6`. Used for dialogs now, and later for grouping on the admin and the Host.
3. **Tile**: a solid colour that *is* the content: answer options, the result banner (`success` / `danger`), the QR plate (marble).

Pull-out data (a score, a place, the PIN) can take the Gleeds **section line**: a 2 px yellow left rule with `pl-4`. It isn't used yet; it's the next phase's way to make score screens feel premium without adding colour.

## States

| State | Look |
| --- | --- |
| Focus | 3 px yellow outline, 3 px offset, on every focusable element (global `:focus-visible`) |
| Hover | primary turns marble; secondary gets a `marble/10` wash; links turn marble. Tailwind limits hover to pointer devices. |
| Pressed | `scale-[0.98]` on buttons and answer tiles. This is the phone's instant "got it" before the server answers. |
| Selected | `ring-4 ring-marble ring-offset-4 ring-offset-obsidian`. The offset keeps the ring legible on every tile; the unselected tiles dim to `opacity-40`. |
| Disabled | `opacity-40`, `cursor-not-allowed`, no hover or press |
| Correct / wrong | `success` fill with marble text / `danger` fill with black text. The correct tile stays full strength, the rest dim. |
| Urgent | the timer turns `danger` for the last 5 s |
| Offline | `Reconnecting…` bar, `danger`, fixed to the top |

## Motion

- **150 ms ease** for feedback (hover, press, dim). It should feel instant.
- **400 ms ease-out** for entrances (`.reveal`), staggered by the caller (the Podium's `podiumRevealMs`).
- Motion explains a change of state; it never decorates idle screens and never delays input.
- `prefers-reduced-motion`: entrances fade without moving. Timing is unchanged, because the Podium's order *is* the suspense.

## Not yet used

The guidelines' landscape renders and vectors (0.25 pt lines in one palette colour) suit Host backgrounds. They come only as supplied files from the Brand Hub, and none are in the repo yet.
