# 0006: `BEST_SCORE` carries the rank today

Status: accepted (2026-10-10, issue #50)

## Context

`docs/adr/0003` put `BEST_SCORE {name, score}` on each Player's personal queue, both Modes, right after `GAME_OVER`. The Solo phone also showed "Rank today", but from `GAME_OVER`'s Solo-only `rank`. A Battle's `GAME_OVER` is the shared Podium, so the Battle phone had no rank today, though the prize goes to the top Score of the day.

## Decision

`BEST_SCORE` gains `rank`: the position on the Day Leaderboard of the same entry its `score` comes from (`DayLeaderboard.bestOf(email)`), so both are null together, only when a Reset happened mid-Game. The phone's final card shows "Rank today" from `BEST_SCORE` in both Modes. `GAME_OVER`'s `rank` is still sent; the phone no longer reads it, and removing it is a separate cleanup.

## Consequences

- Compatibility: additive. A phone built before this ignores the extra field. A phone built after it, talking to a backend built before it, gets no `rank` and shows no "Rank today" (the card guards on `!= null`), so the two deploy in either order.
- Solo's "Rank today" now appears with the Best Score, a moment after `GAME_OVER`, instead of with it. The number is the same: both read the same Day Leaderboard row.
- `docs/adr/0003`'s `{name, score}` is now `{name, score, rank}`; the rest of that decision stands.
