# 0007: Every Game draws a fixed Category mix

Status: accepted (2026-10-13)

## Context

A Game drew `questions_per_game` random active questions with no regard to category, and category was free text. The client's question bank covers three themes (King Charles, UK–Saudi relations, Gleeds), and the event wants every Game to cover all three in the same proportion. A purely random draw can give one Player a Game made almost entirely of Gleeds questions while the next Player gets none. The live Bank also carries older labels for the UK–Saudi theme: `UK`, `Saudi Arabia`, `UK-Saudi Relations` and `UK-Saudi`.

## Decision

- **Category** is required and is exactly one of `King Charles`, `UK–Saudi` (en dash) and `Gleeds`. Input is matched case-insensitively and trimmed, with a plain hyphen accepted for the dash. The canonical name is what gets stored. `Category.java` is the one definition, mirrored by `CATEGORIES` in `frontend/lib/admin.ts`. The database enforces the names with a CHECK.
- **Settings** hold one per-Game number per Category: `king_charles_per_game`, `uk_saudi_per_game` and `gleeds_per_game`. The defaults are 4/3/3, each must be 0 or more, and their sum must be at least 1. The sum is the Question Set size, and `questions_per_game` is dropped.
- **Draw**: one query per Category, `LIMIT` its number of random active questions, then the combined list is shuffled so the themes interleave. If any Category is short, the Game is refused with a 409 naming every short Category. Solo start and Battle create report it the same way the old size check did.
- **Migration (V4)**: the legacy UK–Saudi labels are mapped to `UK–Saudi`. Any other label, or no label at all, gets the placeholder `Gleeds` and is deactivated, so the migration can't fail on prod data nobody anticipated and the CHECK stays exactly three names. A deactivated row is never drawn, and the Admin can re-file it or delete it.

## Consequences

- Every Game still has the same total length, so Scores, Standings and the Day Leaderboard stay comparable. Scoring is unchanged.
- An import or a CRUD request with any other category is refused: per row for an import, 400 for CRUD. A sheet without a `category` column is refused outright.
- The Admin has to keep each Category's active count at or above its per-Game number. The dashboard, Settings and the Question Bank page all warn when a Category falls short.
- `MigrationTest` is the one test below the HTTP boundary: it runs Flyway on a scratch database to V3, inserts the live Bank's legacy labels, then migrates to V4. No HTTP call can produce a pre-V4 row, and a migration that fails on prod stops the deploy.
- Not on the wire: the STOMP contract is unchanged. `/api/admin/settings` changes shape from `{questionsPerGame}` to `{kingCharlesPerGame, ukSaudiPerGame, gleedsPerGame}`, and both ends ship together.

## Considered options

- **A Category table the Admin can edit.** Not chosen: the event has three fixed themes, and a table would add CRUD, migrations of the mix and orphaned settings for a list that won't change.
- **A Postgres enum type.** Not chosen: `TEXT` plus a `CHECK` is easier to change if a fourth theme ever appears.
- **Failing the migration on an unknown label.** Not chosen: a failed migration stops the prod deploy on the event day. Parking the row costs nothing and loses nothing.
