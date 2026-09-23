# 0004: A reconnect is a `ready`, answered with `SYNC` and a replay on the personal queue

Status: accepted (2026-09-23, issue #11)

## Context

Issue #11 asks that "on STOMP CONNECT with a valid session token, the engine sends `SYNC`". At CONNECT the client has not subscribed to anything yet, so a message sent then is lost; docs/adr/0001 already ruled out subscribe events for the same reason. `SYNC` as the issue shapes it (`status`, `questionIndex`, `question?`, `startedAt?`, `timeLimitSec?`, `answered`, `score`, `streak`) also cannot rebuild every screen: a Player whose question closed while they were away needs the `RESULT` they missed, a reloaded big screen needs the reveal or the Standings it was showing, and a phone reopened after the end needs `GAME_OVER` and its `BEST_SCORE`, the prize proof (docs/adr/0003).

## Decision

- The client connects, subscribes to the Game topic and `/user/queue/player`, and says `ready`, on every connect, as docs/adr/0001 already foresaw. On a Game past LOBBY the engine answers on that connection's personal queue with `SYNC`, then the events it missed that `SYNC` can't carry:
  - a Player whose question has closed: their `RESULT`, or a timeout `RESULT` when they never answered;
  - the Host: `QUESTION_START` + `REVEAL` in REVEAL, `LEADERBOARD` in LEADERBOARD, plus `HOST_STATE` on the Host topic while the Battle runs. The Host screen now subscribes to its own personal queue.
  - everyone in FINISHED: `GAME_OVER`, and a Player their `BEST_SCORE`.
- A Battle in LOBBY answers `ready` with `LOBBY_UPDATE`, as before; there is no `SYNC` there, because the lobby list is what the screen needs.
- `SYNC` leaves out `question`, `startedAt` and `timeLimitSec` rather than sending null. `question` goes only while the Player can still answer; the Admin never answers, so a reloaded big screen gets the open question.
- Every personal-queue message goes to one session at a time (session id in the headers, as `@SendToUser(broadcast = false)` does). With `preservePublishOrder`, Spring's ordered channel freezes a message's headers on its first send, so a user destination that resolves to two sessions reaches only one. A phone back from a Wi-Fi drop has two until the server notices the dead one, and an Admin has one per open Host tab.

## Consequences

- Compatibility: additive for Players, who already subscribed to the personal queue and said `ready` on every connect. A phone built before this falls into its reducer's `default` arm on `SYNC` and then applies the replayed events. `GAME_OVER`, `QUESTION_START`, `REVEAL` and `LEADERBOARD` can now also arrive on a personal queue, and the reducers don't care which subscription an event came in on.
- docs/adr/0003's lost-proof case is narrowed: a winner who refreshes gets `GAME_OVER` and `BEST_SCORE` again. Closing the tab still loses the Seat, which lives in `sessionStorage`.
- A Solo Player's reopened final screen shows today's rank as of now, not the rank frozen when the Game ended.
- The countdown resumes from the server's `startedAt` against the phone's clock, as `QUESTION_START` always has; a phone whose clock is off shows the remainder off by the same amount.
