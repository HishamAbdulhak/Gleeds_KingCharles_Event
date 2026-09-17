# 07: Day Leaderboard and Host idle screen

**What to build:** The big screen at `/host` shows the Day Leaderboard — best Score per email across every Game since the last Reset — and a QR code to `/play`, updating live as Games finish. A Solo Player's game-over screen shows their rank on that board.

Spec: `.scratch/quiz-app/spec.md` (Day Leaderboard; User stories 10, 24–25, 47–49). `CONTEXT.md`: Day Leaderboard, Replay, Reset.

**Blocked by:** 06 (Frontend Solo flow)

**Status:** ready-for-agent

- [ ] Leaderboard query: per lowercased email, the maximum `score` over Games with `created_at ≥ settings.leaderboard_since`, any mode, any status; tie-break on lower total response time in that best Game (unanswered questions count as the full time limit), then earlier Game creation; returns name, score, rank
- [ ] `GET /api/leaderboard?top=N` (public) and `GET /api/leaderboard/rank?gameId=…` returning the rank the given Game's Player currently holds
- [ ] Engine publishes `DAY_LEADERBOARD {top}` on `/topic/leaderboard` whenever a Game reaches FINISHED (Reset publishes too, in 10); subscription requires an Admin principal
- [ ] `GAME_OVER` payload now includes `rank`; Solo game-over screen shows "You're #12 today"
- [ ] `/host` page (Admin-only: redirects to `/admin/login` without a token): connects over STOMP with the JWT, fetches the board once, then updates from the topic; large-type top-10 with rank, name, Score; a QR code (client-side generated) to `${NEXT_PUBLIC_BASE_URL}/play` with the URL printed under it; a "New Battle" button (dead until 08)
- [ ] Legible from 5 m: rank and name ≥ 40 px, high contrast, no more than 10 rows
- [ ] Boundary tests: same email plays two Solo Games with different Scores → board lists that email once with the higher Score; two emails with equal Scores order by lower total response time; an Admin STOMP client receives `DAY_LEADERBOARD` when a Game finishes; a Player token cannot subscribe to `/topic/leaderboard`
