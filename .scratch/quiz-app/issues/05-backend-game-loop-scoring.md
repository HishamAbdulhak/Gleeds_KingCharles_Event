# 05: Backend game loop and scoring

**What to build:** Over STOMP, a Solo Player can submit one Answer per question and receive a personal `RESULT` with correctness, Points, Streak and running Score; late, duplicate and off-question Answers are refused; an unanswered question times out server-side; after each result the engine sends the next question, and after the last it finishes the Game with `GAME_OVER`. Verifiable entirely with a STOMP test client — no UI in this ticket.

Spec: `.scratch/quiz-app/spec.md` (Scoring; Game flow → Solo; STOMP contract).

**Blocked by:** 04 (Solo start → first question over STOMP)

**Status:** ready-for-agent

- [ ] Answer entity + repository (unique player+question)
- [ ] Pure scoring function: `base = correct ? round(1000 × (1 − (responseMs / timeLimitMs) / 2)) : 0`; `streak = correct ? streak + 1 : 0`; `multiplier = 1 + 0.1 × min(streak − 1, 5)`; `points = round(base × multiplier)`. Unit test: instant correct 1000, buzzer correct 500, wrong 0, multipliers 1.0 → 1.5 across six consecutive correct Answers, timeout resets Streak
- [ ] `/app/game/{id}/answer` handler with `{questionIndex, option}`: response time = monotonic receipt − monotonic question start; rejects (negative `ANSWER_ACK` on `/user/queue/player`) when the index isn't current, the deadline has passed, or the Player already answered (in-memory `putIfAbsent`, DB unique as backstop); otherwise persists the Answer, updates Player score/streak in memory and DB, sends `ANSWER_ACK {accepted:true}` then `RESULT {correct, points, streak, score, correctOption}`
- [ ] Server-side timer per question: at `startedAt + timeLimitSec` the engine ends the question; Players without an Answer get a `RESULT` with correct=false, points 0, Streak reset
- [ ] Solo pacing: ~3 s after the question ends (by Answer or timeout) the engine publishes the next `QUESTION_START`; after the last question it sets status FINISHED, `ended_at`, and publishes `GAME_OVER {score, totalResponseMs}` (rank is added in 07)
- [ ] The 3 s delay and the timer run on a single scheduled executor; no busy threads; Games older than a few hours are evicted from memory
- [ ] Boundary tests: full Solo run of a 3-question Game with correct fast Answers → three `RESULT`s with Points in 500–1000 and Streak 1, 2, 3, second and third showing multipliers 1.1 and 1.2, then `GAME_OVER`; a second Answer to the same question is rejected; an Answer sent after the deadline (use a 5 s question) is rejected and the timeout `RESULT` shows streak 0; an Answer for a stale index is rejected
