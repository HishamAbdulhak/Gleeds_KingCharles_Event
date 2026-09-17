# 06: Frontend Solo flow

**What to build:** A Solo Player plays a whole Game on their phone: taps one of four big option buttons, sees it lock, sees a result screen (right/wrong, Points, Streak, Score), is carried automatically to the next question, and ends on a game-over screen with their final Score and a "Play again" button. Feels instant and thumb-friendly.

Spec: `.scratch/quiz-app/spec.md` (User stories 4–14; Frontend routes).

**Blocked by:** 05 (Backend game loop and scoring)

**Status:** ready-for-agent

- [ ] `AnswerGrid` component: four large colour-coded buttons (≥ 64 px tall, full width on mobile) that send `/app/game/{id}/answer` on tap and disable immediately; a rejected `ANSWER_ACK` re-enables with a brief message
- [ ] `Timer` component driven by `startedAt` + `timeLimitSec` (not a local countdown that drifts), with a visible bar and seconds
- [ ] Result screen on `RESULT`: correct/incorrect state with the correct option highlighted, Points earned, Streak (with a flame or similar when ≥ 2), running Score
- [ ] Waiting state after answering until `RESULT` arrives ("Locked in…")
- [ ] Next `QUESTION_START` replaces the result automatically — no tap needed; question index shown as "3 / 10"
- [ ] Game-over screen on `GAME_OVER`: final Score and a "Play again" button that returns to `/play` with name/email prefilled from sessionStorage (rank is added in 07)
- [ ] Handles the page being reopened mid-Game gracefully: reconnects with the stored token and shows a waiting state until the next message (full `SYNC` comes in 11)
- [ ] The player page never receives or renders the correct option before `RESULT`
- [ ] `npm run build` and lint pass; manual check on a phone-sized viewport for all four screens (question, locked, result, game over)
