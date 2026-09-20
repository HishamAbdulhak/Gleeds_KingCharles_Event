# King Charles Quiz — Project Plan

> **Superseded in part by `.scratch/quiz-app/spec.md`** (2026-09-17): Battle is a 2–4 Player multi-question Mode, not a 1v1 finale; Solo Mode and the Day Leaderboard were added; the `battle` table is dropped. The schema, STOMP contract and Battle resolution sections that conflicted have been removed — the spec is the reference for those. Directory layout, scoring and step order below still apply.

## Context
Greenfield build (directory is empty). Kahoot-style real-time quiz for a Gleeds event, themed "King Charles & UK–Saudi relations". Three surfaces: mobile Player, big-screen Host, JWT-protected Admin. Doubles as lead capture (name + email on join).

**Assumptions (say so if wrong):**
- Host = an authenticated admin. No separate host login; the admin who creates the game runs it.
- Single server instance for the event. In-memory game state + Spring's simple broker. (`ponytail:` no Redis/RabbitMQ relay; add if you ever run >1 backend node.)
- 4 options per question (Kahoot-style), one correct.
- Maven for the backend. Next.js App Router.

---

## 1. Directory structure

```
charles-quiz/
├── docker-compose.yml                  # postgres:16 only (dev)
├── README.md
├── backend/                            # Spring Boot 4.x, Java 21, Maven
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/gleeds/quiz/
│       │   ├── QuizApplication.java
│       │   ├── config/
│       │   │   ├── SecurityConfig.java         # HTTP rules, stateless, CORS
│       │   │   ├── JwtAuthFilter.java          # Bearer → SecurityContext
│       │   │   ├── JwtService.java             # sign/verify (jjwt)
│       │   │   ├── WebSocketConfig.java        # /ws endpoint, /app, /topic, /queue, /user
│       │   │   └── WsAuthInterceptor.java      # STOMP CONNECT: admin JWT or player token; guards /topic/game/{id}/host
│       │   ├── admin/
│       │   │   ├── AdminAuthController.java    # POST /api/admin/login
│       │   │   ├── QuestionController.java     # CRUD /api/admin/questions
│       │   │   ├── QuestionImportService.java  # CSV (commons-csv) + XLSX (POI)
│       │   │   └── LeadController.java         # GET /api/admin/leads.csv
│       │   ├── game/
│       │   │   ├── GameController.java         # REST: create, join, host commands
│       │   │   ├── GameSocketController.java   # @MessageMapping: answer, battle answer
│       │   │   ├── GameEngine.java             # in-memory state machine, timers, broadcasts
│       │   │   ├── ScoringService.java         # decay + streak formula (pure)
│       │   │   ├── BattleService.java          # atomic winner resolution
│       │   │   └── GameEvent.java              # {type, payload} envelope
│       │   ├── model/                          # JPA entities (one per table below)
│       │   ├── repo/                           # Spring Data repositories
│       │   └── dto/
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/V1__init.sql       # Flyway
│       └── test/java/com/gleeds/quiz/
│           ├── ScoringServiceTest.java
│           ├── BattleServiceTest.java          # 2 threads → exactly 1 winner
│           └── QuestionImportServiceTest.java
└── frontend/                           # Next.js 16, TypeScript, Tailwind
    ├── package.json
    ├── app/
    │   ├── layout.tsx                  # theme (royal purple/gold + Saudi green)
    │   ├── page.tsx                    # Join: PIN + name + email
    │   ├── play/[gameId]/page.tsx      # Player (mobile)
    │   ├── host/[gameId]/page.tsx      # Host (big screen)
    │   └── admin/
    │       ├── login/page.tsx
    │       ├── page.tsx                # game list + "New game"
    │       ├── questions/page.tsx      # table + edit modal + import upload
    │       └── leads/page.tsx          # download CSV
    ├── lib/
    │   ├── api.ts                      # fetch wrapper, JWT from localStorage
    │   ├── stomp.ts                    # @stomp/stompjs client + useGameSocket hook
    │   └── types.ts                    # GameEvent union type (mirrors backend)
    └── components/
        ├── Timer.tsx, AnswerGrid.tsx, Leaderboard.tsx, Podium.tsx, BattleArena.tsx
```

---
## 2. Scoring & answer timing

**Answer timing:** `response_ms = nanoTime-at-receipt − nanoTime-at-question-start` measured on the server. Client timestamps are ignored. Answers after the deadline or for a non-current question are dropped with an ack `{accepted:false}`.

**Points** (`ScoringService`, pure function, unit-tested):
```
base   = correct ? round(1000 * (1 − (response_ms / time_limit_ms) / 2)) : 0   // 1000 → 500 linear decay
streak = correct ? streak + 1 : 0
mult   = 1 + 0.1 * min(streak − 1, 5)                                          // 1.0x … 1.5x, kicks in on 2nd consecutive correct
points = round(base * mult)
```

**Duplicate answers:** in-memory `ConcurrentHashMap<playerId, Answer>` `putIfAbsent` per question, backed by `UNIQUE (player_id, question_id)`.


---

## 3. Chronological implementation order

Each step ends in something runnable.

1. **Scaffold** — `docker-compose.yml` (postgres), Spring Initializr (`web, websocket, security, data-jpa, postgresql, flyway, validation`) + `jjwt`, `commons-csv`, `poi-ooxml`; `create-next-app` (TS, Tailwind, App Router) + `@stomp/stompjs`. Backend boots against Docker Postgres.
2. **Schema + entities** — `V1__init.sql` above, JPA entities, repositories. Seed one admin via `V2__seed_admin.sql` (bcrypt hash from env at startup is fine too).
3. **Admin auth** — `JwtService`, `JwtAuthFilter`, `SecurityConfig`: `/api/admin/**` and host commands need `ROLE_ADMIN`; `/api/games/*/join`, `/ws` public. `POST /api/admin/login`. Frontend `admin/login`.
4. **Question bank** — CRUD controller + `QuestionImportService` (CSV columns: `text,a,b,c,d,correct(A-D),time_limit,category`; XLSX same header row). Admin questions page with upload. Test: import a 3-row CSV, assert 3 questions + bad-row error report.
5. **Game create + join** — PIN generator (6 digits, retry on collision), `POST /api/games`, `POST /api/games/{pin}/join` with email validation + `UNIQUE(game_id,email)` → 409. Frontend join page.
6. **WebSocket lobby** — `WebSocketConfig`, `WsAuthInterceptor`, `LOBBY_UPDATE` on join. `useGameSocket` hook. Host lobby page shows PIN + live player list; player sees "You're in".
7. **Game loop** — `GameEngine` state machine (start/next/reveal/leaderboard), server timer, `ScoringService`, `/app/game/{id}/answer` handler, `RESULT` + `REVEAL` + `LEADERBOARD` broadcasts. Player answer grid + result screen; host question/reveal/leaderboard/podium screens. Test: `ScoringServiceTest`.
8. **Battle mode** — `BattleService`, host picks two players from `HOST_STATE`, `BATTLE_START`/`BATTLE_RESULT`, `BattleArena` component. Test: `BattleServiceTest` submits two correct answers from two threads, asserts exactly one winner row.
9. **Leads + polish** — `GET /api/admin/leads.csv`; reconnect (player re-CONNECTs with stored `sessionToken`, gets current state via `HOST_STATE`-like `SYNC` reply); theme, sounds, mobile QA on real phones over LAN.
10. **Deploy** — one VM/container host (backend jar + Postgres) and Vercel/static for Next.js, or both in Docker Compose on the venue laptop. Env: `JWT_SECRET`, `DB_URL`, `CORS_ORIGIN`.

---

## 4. Verification
- `docker compose up -d && ./mvnw spring-boot:run` — Flyway applies V1 cleanly.
- `./mvnw test` — scoring, battle race, CSV import tests green.
- Manual E2E: admin logs in → imports CSV → creates game → host page shows PIN → two phones join → play 3 questions → host triggers a battle → podium → leads CSV contains both emails.
- Race check: open two player tabs, answer simultaneously in a battle via devtools `Promise.all` → exactly one `BATTLE_RESULT.winnerId`.

## Skipped (add when needed)
- Redis/RabbitMQ STOMP relay — only if you scale past one backend node.
- Player accounts / OAuth — email is a lead field, not a login.
- Question types beyond 4-option MCQ — schema change is one `type` column when asked.
