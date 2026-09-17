# 11: Reconnect and mobile polish

**What to build:** A Player whose phone drops off Wi-Fi mid-question reopens the page and lands exactly where the Game is — same question with the correct remaining time, or the waiting/result state — in both Modes. A quick pass makes every Player and Host screen legible and tappable in the real room.

Spec: `.scratch/quiz-app/spec.md` (Game flow → Reconnect; User stories 13, 23, 50).

**Blocked by:** 09 (Battle game loop)

**Status:** ready-for-agent

- [ ] On STOMP CONNECT with a valid session token, the engine sends `SYNC {status, questionIndex, question?, startedAt?, timeLimitSec?, answered, score, streak}` on the Player's personal queue; `question` is omitted unless status is QUESTION and the Player hasn't answered
- [ ] Player page restores state from `SYNC`: question with the remaining time, "Locked in" if already answered, lobby list if LOBBY, game-over if FINISHED
- [ ] Reconnecting Admin on `/host/[gameId]` gets the same `SYNC` plus `HOST_STATE` and restores the correct Host screen
- [ ] `@stomp/stompjs` auto-reconnect enabled with backoff; a visible "Reconnecting…" banner while disconnected
- [ ] A Player who reconnects after a Battle question they missed sees the timeout result, not a stale question
- [ ] Mobile pass on Player screens: no horizontal scroll at 360 px wide, option buttons ≥ 64 px tall with ≥ 8 px gaps, text ≥ 18 px, contrast ≥ 4.5:1 on all four option colours
- [ ] Host pass at 1080p from 5 m: question text ≥ 48 px, options ≥ 36 px, timer visible from every screen
- [ ] Boundary test: mid-question the client disconnects and reconnects with the same token → receives `SYNC` with the current index and a `startedAt` equal to the original; a reconnect after answering has `answered: true` and no `question`
