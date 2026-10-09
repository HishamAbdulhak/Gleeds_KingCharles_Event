# Design system

The tokens and classes live in `frontend/app/globals.css`. This file says when to use which. Two sources feed it:

- **The event theme** (spec story 50): royal purple, gold, Saudi green. It sets the palette.
- **The Gleeds brand guidelines** (July 2024, v1). They set the typeface, sentence case, restraint, the WCAG contrast floor (4.5:1 body, 3:1 large or bold) and the section-line device. The guidelines' own primary colours (marble `#F5F5F5`, obsidian `#3C3C3C`) are set aside for this event. To switch, change `--color-royal-*` to obsidian and nothing else moves.

Three surfaces, three densities:

| Surface | Where | Viewed from | Rule |
| --- | --- | --- | --- |
| Player | `/play`, `/join`, `/play/[gameId]` | a phone in the hand, 360 px wide | one task per screen, thumb-sized targets, immediate feedback |
| Host | `/host`, `/host/[gameId]` | a 1920×1080 screen, 5 m away | readability over density; nothing under `text-2xl` |
| Admin | `/admin/**` | a desktop | dense is fine; `text-sm` body |

## Colour

| Token | Use | Never |
| --- | --- | --- |
| `royal-900` | page background, text on gold or yellow | — |
| `royal-800` | `.panel`: dialogs and anything raised above the page | as a page background |
| `gold-500` | the one accent: primary action, screen titles, the PIN, the timer | more than one gold block competing on a screen |
| `gold-300` | gold *text* on royal: ranks, secondary numbers, links, the focus ring | as a fill behind text |
| `cream` | text; `/80` secondary, `/60` tertiary, `/40` borders, `/10`–`/20` tracks and dividers | — |
| `saudi` | success fill (Correct!, answered), cream text on it | as text on royal (too dark) |
| `danger` | danger fill with cream text: Wrong / Time's up, the urgent timer, Reconnecting…, destructive buttons | — |
| `danger-fg` | error text on royal | as a fill |

Proportion, as in the Gleeds guide: royal and cream carry each screen; gold, green and red stay under a quarter of it. The four answer colours (`OPTION_COLOURS` in `AnswerGrid.tsx`) are the exception. They are the game, and each has its shape (▲ ◆ ● ■), so colour is never the only signal.

## Type

`font-sans` is Helvetica Neue, then Helvetica, then Arial: the Gleeds primary font, then its system font. Phones without either fall back to their own sans.

- **Sentence case** everywhere, never all caps (Gleeds: the brandmark is lowercase, so are we).
- **Bold is for reading distance, not decoration.** Host text is bold so it carries 5 m. Player titles and numbers are bold. Admin body copy is regular.
- **Numbers** that change or line up (scores, ranks, timer, PIN, counts) are `tabular-nums`.

| Role | Player | Host | Admin |
| --- | --- | --- | --- |
| Screen title | `text-3xl font-bold text-gold-500` | `text-6xl font-bold text-gold-500` | `text-2xl font-bold text-gold-500` |
| Question | `text-2xl font-bold leading-snug` | `text-[clamp(2rem,4vw,4.5rem)] font-bold leading-tight` | — |
| Data (score, place, PIN) | `text-4xl`–`text-6xl font-bold tabular-nums` | `text-5xl` rows, PIN `clamp(5rem,11vw,14rem)` | — |
| Body | `text-lg`, secondary `text-cream/80` | `text-2xl`–`text-4xl` | `text-sm`, hints `text-xs text-cream/60` |

## Space, radius, depth

- **Spacing** is Tailwind's 4 px scale. Page gutter: Player `p-4`/`p-6`, Admin `p-8`, Host `p-10`/`p-12`. Gaps between stacked blocks: Player `gap-4`/`gap-6`, Host `gap-8`, Admin `gap-6`.
- **Radius**: `rounded-lg` (8 px) on controls (buttons, fields), `rounded-xl` (12 px) on tiles and panels (answer tiles, result banner, QR plate, dialogs), `rounded-full` on pills and tracks (timer, roster chips).
- **Depth** comes from surface colour plus a `cream/10` hairline, not shadows. The dark room and the screen glare flatten shadows anyway. The only overlay is the dialog backdrop (`black/60`).

## Buttons

`.btn` plus one colour plus, off the admin, one size:

| Class | Use |
| --- | --- |
| `btn-primary` | the one forward action on a screen (Start, Join, Reveal, Next, Save) |
| `btn-secondary` | the alternative beside it (End Battle) |
| `btn-danger` | destructive and irreversible (Reset the Day Leaderboard) |
| `btn-lg` | Player: 56 px minimum height, full-width in forms |
| `btn-xl` | Host |
| `.link` | navigation and in-row actions (Edit, Play solo); low-emphasis links (Cancel, Log out) stay `text-cream/80 underline` |

One primary per screen. A destructive in-row action is `text-danger-fg underline`.

## Cards

1. **Page**: `royal-900`.
2. **Panel** (`.panel`): `royal-800` + hairline, `rounded-xl`, `p-6`. For dialogs now, and for grouping on admin and the Host in later phases.
3. **Tile**: a solid colour that *is* the content: answer options, the result banner (`saudi` / `danger`), the QR plate (`cream`).

Pull-out data (a score, a place, the PIN) can take the Gleeds **section line**: a 2 px `gold-500` left rule with `pl-4`. It isn't used yet; it's the next phase's way to make the score screens feel premium without adding colour.

## States

| State | Look |
| --- | --- |
| Focus | 3 px `gold-300` outline, 3 px offset, on every focusable element (global `:focus-visible`) |
| Hover | primary lightens to `gold-300`; secondary gets a `cream/10` wash; links go `gold-500`. Hover is gated to pointer devices by Tailwind. |
| Pressed | `scale-[0.98]` on buttons and answer tiles. This is the phone's instant "got it" before the server answers. |
| Selected | `ring-4 ring-cream ring-offset-4 ring-offset-royal-900`. The offset keeps the ring legible on the yellow tile. The unselected tiles dim to `opacity-40`. |
| Disabled | `opacity-40`, `cursor-not-allowed`, no hover or press |
| Correct / wrong | `saudi` / `danger` fill with cream text. The correct tile stays full strength, the rest dim. |
| Urgent | the timer turns `danger` for the last 5 s |
| Offline | `Reconnecting…` bar, `danger`, fixed to the top |

## Motion

- **150 ms ease** for feedback (hover, press, dim). It should feel instant.
- **400 ms ease-out** for entrances (`.reveal`), staggered by the caller (the Podium's `podiumRevealMs`).
- Motion explains a change of state; it never decorates idle screens and never delays input.
- `prefers-reduced-motion`: entrances fade without moving. Timing is unchanged, because the Podium's order *is* the suspense.

## Brand assets

The Gleeds brandmark only appears as supplied artwork. It is never retyped or recoloured, comes in white on our dark backgrounds, sits top-left, is at least 100 px wide, and keeps clear space equal to its height. The repo has no brandmark file yet, so no screen shows one. The landscape vectors (0.25 pt lines, one colour) are the guidelines' decoration for screen backgrounds, also only from supplied files.
