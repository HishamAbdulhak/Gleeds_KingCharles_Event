# 04: Solo start → first question over STOMP

**What to build:** A visitor opens `/play`, enters name, email and ticks consent, taps Start, and within a second sees the first question with four options and a live countdown on their phone. Answering is not yet possible (ticket 05). This ticket lays the WebSocket and Game Engine foundations every later slice reuses.

Spec: `.scratch/quiz-app/spec.md` (Game flow → Solo; STOMP contract; Domain model).

**Blocked by:** 03 (Question Bank CRUD and CSV/XLSX import)

**Status:** ready-for-agent

- [ ] Entities + repositories for settings, game, game_question, player (answer comes in 05)
- [ ] `POST /api/solo` (public) with name, email (validated), consent=true → creates a Game (mode SOLO, no PIN), one Player with a fresh session token and `consented_at`, and a Question Set of `settings.questions_per_game` random active questions; returns `{gameId, playerId, sessionToken}`; 400 without consent or with an invalid email; 409 if the Bank has fewer active questions than needed
- [ ] WebSocket config: endpoint `/ws`, app prefix `/app`, simple broker on `/topic` and `/queue`, user prefix `/user`, CORS from `CORS_ORIGIN`
- [ ] Channel interceptor: CONNECT with an Admin Bearer JWT → ADMIN principal; CONNECT with a Player session-token header → Player principal; anything else rejected. SUBSCRIBE to `/topic/game/{id}` allowed only for that Game's Players or an Admin
- [ ] In-memory Game Engine keyed by game id: on the Player's first subscription to a Solo Game's topic (or an explicit `/app/game/{id}/ready` message — pick one and document it), publishes `QUESTION_START {index, text, options, timeLimitSec, startedAt}` without the correct option and records the monotonic start time; Game status becomes QUESTION
- [ ] All server → client messages use the `{type, payload}` envelope; a shared TypeScript union of event types lives in the frontend
- [ ] `/play` page: name / email / consent form with inline validation and a "Have a PIN?" link (dead link until 08); on success stores ids + token in sessionStorage and routes to `/play/[gameId]`
- [ ] `/play/[gameId]` page: connects over STOMP with the session token, subscribes, renders the question text, four large colour-coded option buttons (disabled for now) and a countdown derived from `startedAt` + `timeLimitSec`
- [ ] Boundary test: `POST /api/solo` → connect a STOMP client with the returned token → subscribe → receive `QUESTION_START` whose payload has no correct option and whose question is one of the Bank's; a client with a bogus token is refused
