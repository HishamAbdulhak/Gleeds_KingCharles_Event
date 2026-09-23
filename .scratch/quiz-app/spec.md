# Spec: King Charles Quiz — Solo & Battle with Day Leaderboard

Status: ready-for-agent (implementation tickets: GitHub issues #1–#11)

Vocabulary: see `CONTEXT.md`.

## Problem Statement

Gleeds is running a one-day stand at a King Charles / UK–Saudi themed event. They want visitors to stop, play a themed trivia game on their phone, and compete for a single end-of-day prize, handed out on the day. Each visitor gives a name and an email; the email only tells one visitor from another across Games, and is deleted after the event (`docs/adr/0003`). Visitors arrive alone or in small groups, so the stand needs both a walk-up solo experience with no staff involvement and a staff-run head-to-head experience for groups. The trivia content comes from the client as a spreadsheet, so staff need to load it without a developer. Nothing like this exists yet; the directory is empty.

## Solution

A web app with three surfaces:

- **Player** (phone): scan the QR code on the big screen, enter name + email, and play a **Solo** Game of 10 timed questions; or enter a **PIN** to join a **Battle** lobby of 2–4 Players run from the big screen.
- **Host** (big screen): when idle, shows the **Day Leaderboard** and the QR code; when a Battle is running, shows the lobby, each question, the reveal, the leaderboard between questions, and the **Podium**.
- **Admin** (staff, JWT-protected): manage the **Question Bank** (CRUD + CSV/XLSX import), set questions-per-Game, and **Reset** the Day Leaderboard.

Both Modes use identical scoring (speed decay + Streak), and every Game draws the same number of random questions, so Solo and Battle Scores rank on one Day Leaderboard. Best Score per email wins.

## User Stories

### Solo Player

1. As a visitor, I want to scan a QR code on the big screen and land on a join page, so that I can start playing without staff help.
2. As a visitor, I want to enter my name and email and be told what the email is for, so that I can play and every Game I play counts as mine.
3. As a visitor, I want the form to reject an invalid email before I start, so that I don't lose my Score to a typo.
4. As a Solo Player, I want to tap Start and immediately see the first question with four options and a countdown, so that play feels instant.
5. As a Solo Player, I want the countdown to be visible and accurate, so that I know how much time I have.
6. As a Solo Player, I want to tap one option and have it lock immediately, so that I can't accidentally change it.
7. As a Solo Player, I want to see whether I was right, the Points I earned, my Streak, and my running Score after each question, so that I stay engaged.
8. As a Solo Player, I want the next question to appear automatically a few seconds after the result, so that I don't have to tap "next".
9. As a Solo Player, I want a question I don't answer in time to count as wrong and reset my Streak, so that the timer matters.
10. As a Solo Player, I want a final screen with my name, my Score, my best Score today and my rank on the Day Leaderboard, so that I know if I'm in prize contention and can show it to claim the prize.
11. As a Solo Player, I want to play again with the same email, so that I can try to beat my Score.
12. As a Solo Player replaying, I want a different random Question Set, so that replaying is a real game.
13. As a Solo Player whose phone loses connection mid-game, I want to reopen the page and continue where I was, so that a flaky network doesn't ruin my run.
14. As a Solo Player, I want the answer buttons to be large and thumb-reachable, so that speed is about knowledge, not dexterity.

### Battle Player

15. As a visitor in a group, I want to enter the PIN shown on the big screen plus my name and email, so that I can join a Battle.
16. As a Battle Player, I want to see "You're in" and the names of others in the lobby, so that I know I joined the right Game.
17. As a Battle Player, I want to be told the lobby is full if a fifth person tries to join, so that I understand why I couldn't get in.
18. As a Battle Player, I want each question to appear on my phone at the same moment it appears on the big screen, so that nobody has a head start.
19. As a Battle Player, I want to answer once, see my personal result on my phone, and see the reveal and leaderboard on the big screen, so that the group shares the moment.
20. As a Battle Player, I want my Streak to carry across the Battle's questions, so that consistency is rewarded.
21. As a Battle Player, I want the Podium at the end to show every Player in the lobby, and my phone to show my place, my name and my best Score today, so that everyone sees where they finished and a day's winner can claim the prize.
22. As a Battle Player who joins with an email already in this lobby, I want to be reconnected to my existing seat rather than duplicated, so that a page refresh doesn't create a second me.
23. As a Battle Player who disconnects, I want the Battle to carry on and my Score to stand, so that I don't spoil the game for others.

### Host (Admin at the big screen)

24. As a Host, I want an idle screen that shows the Day Leaderboard and a QR code to the Solo join page, so that the stand attracts walk-ups with no staff action.
25. As a Host, I want the Day Leaderboard to update live as Games finish, so that the screen is always current.
26. As a Host, I want to tap "New Battle" and see a PIN, so that a group can join.
27. As a Host, I want to see Players appear in the lobby in real time, so that I know when to start.
28. As a Host, I want the Start button disabled until at least 2 Players have joined, so that a Battle can't start with one person.
29. As a Host, I want the lobby capped at 4 Players, so that Battles stay quick and readable on screen.
30. As a Host, I want each question shown big with the four options and a countdown, so that the group can read it from a distance.
31. As a Host, I want to see how many Players have answered, so that I can end the question early when everyone's in.
32. As a Host, I want a reveal screen showing the correct option and how many chose each, so that the group gets the payoff.
33. As a Host, I want a leaderboard between questions and a Podium at the end, so that the competition is visible.
34. As a Host, I want "Next" to be a single tap and "End Battle" always available, so that I can pace or abort the Game.
35. As a Host, I want the screen to return to the idle Day Leaderboard after a Battle, so that I don't have to navigate.
36. As a Host, I want the Host view to reject anyone who is not an Admin, so that a visitor can't open it on their phone and see answers.

### Admin (staff, back office)

37. As an Admin, I want to log in with email and password and get a session that lasts the day, so that I'm not re-authenticating between Battles.
38. As an Admin, I want to see all questions in the Question Bank in a table, so that I can check what the client sent.
39. As an Admin, I want to add, edit, deactivate and delete a question, so that I can fix typos or drop a bad one.
40. As an Admin, I want to upload the client's CSV or XLSX and see how many rows imported and which rows failed and why, so that I can fix the sheet and re-upload.
41. As an Admin, I want the importer to accept the correct answer as a letter (A–D), so that the client's sheet works as written.
42. As an Admin, I want to set how many questions each Game draws, so that I can shorten Games if the queue is long.
43. As an Admin, I want a Reset button that empties the Day Leaderboard, so that a test run before doors open doesn't win the prize.
44. As an Admin, I want Reset to keep all past Games, so that a mis-click never loses data.
45. ~~As an Admin, I want to download Leads as a CSV with name, email, consent time, best Score and first-seen time, deduplicated by email, so that marketing gets one clean list.~~ Dropped: there are no Leads (`docs/adr/0003`).
46. As an Admin, I want every admin endpoint to reject requests without a valid token, so that the Question Bank and settings are private.

### Gleeds (organiser)

47. As Gleeds, I want one Day Leaderboard across Solo and Battle, so that there is one prize and one winner.
48. As Gleeds, I want ties broken by faster total response time, so that the winner is unambiguous.
49. As Gleeds, I want every Game to have the same number of questions, so that Scores are comparable.
50. As Gleeds, I want the app to look on-theme (royal purple/gold with Saudi green accents), so that it fits the stand.

## Implementation Decisions

### Architecture

- Backend: Spring Boot 3, Java 21, Spring Security (stateless JWT), Spring WebSocket with STOMP over a raw WebSocket endpoint and the built-in simple broker. Single instance; all live Game state in memory, persisted to Postgres as it happens. (Deliberate: one server for one day. A broker relay is the upgrade if that ever changes.)
- Frontend: Next.js App Router, TypeScript, Tailwind, `@stomp/stompjs`. QR code rendered client-side from the public base URL.
- Database: PostgreSQL 16 via Spring Data JPA, schema managed by Flyway.
- Host commands and all Admin operations are plain REST with a Bearer JWT. Only the latency-sensitive Player Answer travels over STOMP.

### Domain model / schema

- **admin_user**: id, email (unique), bcrypt password hash. Seeded by migration from environment variables.
- **question**: text, four option texts, correct option index 0–3, time limit in seconds (5–120, default 20), optional category, active flag.
- **settings**: exactly one row: `questions_per_game` (default 10) and `leaderboard_since` (timestamp; default epoch). Reset sets `leaderboard_since` to now.
- **game**: id (UUID), `mode` (SOLO | BATTLE), `pin` (6 chars, unique, null for Solo), `status` (LOBBY | QUESTION | REVEAL | LEADERBOARD | FINISHED), current question index, question started-at, created-at, ended-at. Solo Games skip LOBBY/LEADERBOARD and go QUESTION → REVEAL → QUESTION … → FINISHED automatically.
- **game_question**: (game, position) → question. The Question Set is drawn at Game creation: `questions_per_game` random active questions. Snapshotting the draw keeps history stable if a question is later edited or deactivated.
- **player**: id (UUID), game, name, email, `session_token` (UUID, unique; used for STOMP auth and reconnect), score, streak, joined-at. Unique (game, email): a second join with the same email in the same Game returns the existing seat and token (reconnect), not an error.
- **answer**: game, player, question, selected option, correct flag, response ms, points, submitted-at. Unique (player, question) enforces one Answer at the database level as well as in memory.
- No `battle` table. A Battle is a Game with mode BATTLE.
- The email identifies a Player across Games and never leaves the server, Admins included; the event database is wiped once prizes are handed out (`docs/adr/0003`).

### Scoring (pure function, both Modes)

- `base = correct ? round(1000 × (1 − (response_ms / time_limit_ms) / 2)) : 0` — 1000 at instant, 500 at the buzzer.
- `streak = correct ? streak + 1 : 0`; a timeout counts as wrong.
- `multiplier = 1 + 0.1 × min(streak − 1, 5)` — 1.0× on the first correct, up to 1.5×.
- `points = round(base × multiplier)`.
- Response time is measured on the server: monotonic clock at receipt minus monotonic clock at question start. Client timestamps are ignored. Answers arriving after the deadline, for a non-current question, or from a Player who already answered are rejected with a negative ack.

### Day Leaderboard

- Query: for each lowercased email, the maximum `score` over Games with `created_at ≥ leaderboard_since`, any mode, any status (an abandoned Solo Game counts with whatever it earned). Tie-break: lower total response time in that best Game, where unanswered questions count as the full time limit; then earlier Game creation.
- Exposed as a public REST read (top N plus a lookup of one email's rank for the Solo end screen) and pushed to a dedicated STOMP topic whenever a Game finishes or a Reset happens, so the Host idle screen never polls.

### Game flow

- **Solo**: a public REST call with name and email creates the Game + Player + Question Set and returns ids and session token. The Player connects over STOMP and subscribes to the Game topic; the engine immediately sends the first question. On each Answer or timeout the engine sends the personal result, waits ~3 s, then sends the next question; after the last, it marks FINISHED, sends game-over with the Player's Day Leaderboard rank, and pushes the leaderboard topic.
- **Battle**: Admin REST call creates a Game with mode BATTLE and a PIN. Public join by PIN with name and email; refused with a conflict when the lobby already has 4 Players or the Game has left LOBBY. Start is refused with fewer than 2 Players. Host REST commands: start, reveal (force early end), next, end. Server-side timer auto-reveals at the deadline. Reveal broadcasts correct option + per-option counts; next either broadcasts the leaderboard-then-question or, after the last question, the Podium and FINISHED.
- Reconnect: a client that reconnects with its session token receives a sync message describing the current state (phase, current question if any, remaining time, own score/streak) on its personal queue.

### STOMP contract

- Endpoint `/ws`; app prefix `/app`; broker prefixes `/topic`, `/queue`; user prefix `/user`.
- CONNECT is authenticated by a channel interceptor: an Admin Bearer JWT, or a Player session token header. Subscriptions to a Game's topic are limited to that Game's Players and Admins; the Host-only topic and the leaderboard topic require Admin.
- Client → server: one Answer destination per Game carrying question index and selected option.
- Server → client, all as `{type, payload}`: Game topic (`LOBBY_UPDATE`, `QUESTION_START` without the correct option, `REVEAL`, `LEADERBOARD`, `GAME_OVER`), Host topic (`HOST_STATE`, which carries who is in on the open question — story 31's count is the Host's, so it is not on the Game topic), personal queue (`ANSWER_ACK`, `RESULT`, `SYNC`), leaderboard topic (`DAY_LEADERBOARD`).

### Admin API

- Login returns a JWT valid for 24 h.
- Question CRUD; import accepts CSV or XLSX with header `text,a,b,c,d,correct,time_limit,category` (correct as A–D; time limit and category optional), inserts valid rows, and returns per-row errors for the rest.
- Settings read/update (questions per Game); Reset.
- Security: everything under the admin and host-command paths requires the ADMIN role; Solo start, Battle join, the leaderboard read and the WebSocket endpoint are public.

### Frontend routes

- `/play` — Solo join (name, email, and a one-line notice of what the email is for) and a "Have a PIN?" link.
- `/join` — Battle join (PIN, name, email, and the same notice).
- `/play/[gameId]` — Player view for both Modes (question, result, game-over).
- `/host` — Host idle (Day Leaderboard + QR, "New Battle"); `/host/[gameId]` — Battle lobby / question / reveal / leaderboard / Podium.
- `/admin/login`, `/admin/questions`, `/admin/settings` (questions per Game + Reset).

## Testing Decisions

- **Seam: the HTTP + STOMP boundary against a real Postgres.** A Spring Boot test on a random port with a Testcontainers Postgres, driving the app exactly as the frontend does: REST calls with a JWT and a STOMP client for Answers, asserting on received messages and database rows. No mocks of the engine, repositories or security. This is the single seam; it covers auth, WebSocket authorisation, timing rejection, the unique constraints, the leaderboard SQL and Flyway migrations in one place.
- **Plus one pure unit test** for the scoring function, since it is the formula the prize depends on and is trivially testable in isolation.
- Good tests here assert externally observable outcomes only: the message a client received, the HTTP status, the row that exists. They never reach into engine state.
- Tests to write at the seam:
  1. Admin login; unauthenticated admin call is 401; Player token cannot subscribe to the Host topic.
  2. CSV import of a small sheet with one bad row: N−1 questions created, one error reported with its row number.
  3. Solo run end to end: start → first question received → correct fast Answer scores between 500 and 1000 → a second Answer to the same question is rejected → run to game-over → the Player appears on the Day Leaderboard.
  4. Battle: create → two join → a fifth join is refused → start refused with one Player, allowed with two → both answer → reveal shows correct option and counts → next → … → Podium lists both; same email joining the same lobby twice gets the same seat.
  5. Late Answer (after deadline) rejected and Streak reset; Streak multiplier visible in the second consecutive correct result.
  6. Replay: same email, second Game, higher Score → Day Leaderboard shows the higher one only.
  7. Reset: Games before Reset disappear from the board; the Games themselves are kept.
- Scoring unit test: instant correct = 1000, buzzer correct = 500, wrong = 0, multiplier 1.0/1.1/…/1.5 across a Streak, timeout resets Streak.
- Prior art: none (empty repo). Use Spring's `WebSocketStompClient` with a `StringMessageConverter`/Jackson converter and Testcontainers' `@ServiceConnection` for the Postgres container.
- No frontend tests in this spec beyond `tsc`/lint passing.

## Out of Scope

- Bilingual / Arabic / RTL content.
- Question images or media; question types other than four-option single-answer.
- Self-forming Battles without a Host; more than 4 Players per Battle.
- Multi-instance deployment, external STOMP broker, Redis.
- Separate per-Mode leaderboards; a persistent Event entity (one-day event, Reset suffices).
- Admin self-service password reset; multiple admin roles.
- Sound effects, animations beyond basic transitions, accessibility audit (basic contrast and touch-target sizing are in scope).
- Analytics, rate limiting, CAPTCHA on the join form.

## Further Notes

- The join form's notice wording is a placeholder for Gleeds' legal text.
- The Host idle screen is the marketing surface of the stand: prioritise legibility from 5 m over information density.
