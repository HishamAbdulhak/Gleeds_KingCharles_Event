# King Charles Quiz — Project Plan

> **Superseded in part by `.scratch/quiz-app/spec.md`** (2026-09-17): Battle is a 2–4 Player multi-question Mode, not a 1v1 finale; Solo Mode and the Day Leaderboard were added; the `battle` table is dropped. Directory layout and step order below still apply; where schema or STOMP sections conflict, the spec wins.

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

## 2. PostgreSQL schema (`V1__init.sql`)

```sql
CREATE TABLE admin_user (
  id            BIGSERIAL PRIMARY KEY,
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,                 -- bcrypt
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE question (
  id             BIGSERIAL PRIMARY KEY,
  text           TEXT NOT NULL,
  option_a       TEXT NOT NULL,
  option_b       TEXT NOT NULL,
  option_c       TEXT NOT NULL,
  option_d       TEXT NOT NULL,
  correct_option SMALLINT NOT NULL CHECK (correct_option BETWEEN 0 AND 3),
  time_limit_sec INT NOT NULL DEFAULT 20 CHECK (time_limit_sec BETWEEN 5 AND 120),
  category       TEXT,                         -- 'ROYAL' | 'UK_SAUDI' | free text
  image_url      TEXT,
  active         BOOLEAN NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE game (
  id                     UUID PRIMARY KEY,
  pin                    CHAR(6) NOT NULL UNIQUE,
  status                 TEXT NOT NULL DEFAULT 'LOBBY'
                         CHECK (status IN ('LOBBY','QUESTION','REVEAL','LEADERBOARD','BATTLE','FINISHED')),
  current_question_index INT NOT NULL DEFAULT -1,
  question_started_at    TIMESTAMPTZ,
  created_by             BIGINT NOT NULL REFERENCES admin_user(id),
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at               TIMESTAMPTZ
);

-- snapshot of question order per game (questions can be edited later without breaking history)
CREATE TABLE game_question (
  game_id     UUID   NOT NULL REFERENCES game(id) ON DELETE CASCADE,
  question_id BIGINT NOT NULL REFERENCES question(id),
  position    INT    NOT NULL,
  PRIMARY KEY (game_id, position)
);

CREATE TABLE player (
  id            UUID PRIMARY KEY,
  game_id       UUID NOT NULL REFERENCES game(id) ON DELETE CASCADE,
  name          TEXT NOT NULL,
  email         TEXT NOT NULL,                 -- lead capture
  session_token UUID NOT NULL UNIQUE,          -- WS auth + reconnect
  score         INT  NOT NULL DEFAULT 0,
  streak        INT  NOT NULL DEFAULT 0,
  joined_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (game_id, email)                      -- one seat per email per game
);
CREATE INDEX player_email_idx ON player (lower(email));

CREATE TABLE answer (
  id              BIGSERIAL PRIMARY KEY,
  game_id         UUID   NOT NULL REFERENCES game(id) ON DELETE CASCADE,
  player_id       UUID   NOT NULL REFERENCES player(id) ON DELETE CASCADE,
  question_id     BIGINT NOT NULL REFERENCES question(id),
  selected_option SMALLINT NOT NULL CHECK (selected_option BETWEEN 0 AND 3),
  correct         BOOLEAN NOT NULL,
  response_ms     INT NOT NULL,                -- server receive time − question_started_at
  points          INT NOT NULL,
  submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (player_id, question_id)              -- DB-enforced single answer
);

CREATE TABLE battle (
  id            UUID PRIMARY KEY,
  game_id       UUID   NOT NULL REFERENCES game(id) ON DELETE CASCADE,
  question_id   BIGINT NOT NULL REFERENCES question(id),
  player_a_id   UUID   NOT NULL REFERENCES player(id),
  player_b_id   UUID   NOT NULL REFERENCES player(id),
  started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  winner_id     UUID REFERENCES player(id),    -- NULL until resolved
  winner_ns     BIGINT,                        -- System.nanoTime() delta, for the reveal screen
  resolved_at   TIMESTAMPTZ,
  CHECK (player_a_id <> player_b_id)
);
```

Lead export = `SELECT DISTINCT ON (lower(email)) name, email, MAX(score), MIN(joined_at) FROM player ...` → CSV.

---

## 3. WebSocket / STOMP architecture

**Endpoint:** `/ws` (raw WebSocket; no SockJS). Prefixes: app `/app`, broker `/topic` + `/queue`, user prefix `/user`. Spring simple broker.

**Auth on CONNECT** (`WsAuthInterceptor`): header `Authorization: Bearer <admin JWT>` → role ADMIN, or `X-Player-Token: <session_token>` → principal = playerId. Interceptor rejects SUBSCRIBE to `/topic/game/{id}/host` unless ADMIN, and to `/topic/game/{id}` unless the principal belongs to that game.

**Host commands are plain REST** (JWT bearer, idempotent, easy to retry) — only the latency-sensitive player answer goes over STOMP:

| REST (admin) | Effect |
|---|---|
| `POST /api/games` `{questionIds[]}` | create game, returns `{id, pin}` |
| `POST /api/games/{id}/start` | LOBBY → QUESTION(0) |
| `POST /api/games/{id}/next` | REVEAL/LEADERBOARD → next QUESTION, or FINISHED |
| `POST /api/games/{id}/reveal` | force early end of current question |
| `POST /api/games/{id}/battle` `{playerAId, playerBId, questionId}` | BATTLE |
| `POST /api/games/{id}/end` | FINISHED |
| `POST /api/games/{pin}/join` `{name,email}` (public) | returns `{gameId, playerId, sessionToken}` |

**Client → Server (`/app/...`)**

| Destination | Body | Who |
|---|---|---|
| `/app/game/{gameId}/answer` | `{questionIndex, option}` | player |
| `/app/game/{gameId}/battle/{battleId}/answer` | `{option}` | player (only the two combatants accepted) |

**Server → Client** — every message is `{type, payload}`:

| Destination | Types |
|---|---|
| `/topic/game/{gameId}` (everyone) | `LOBBY_UPDATE {players[]}`, `QUESTION_START {index, text, options[4], timeLimitSec, startedAt}` (no correct option), `ANSWER_COUNT {answered, total}`, `REVEAL {correctOption, distribution[4]}`, `LEADERBOARD {top[]}`, `BATTLE_START {battleId, playerA, playerB, question}`, `BATTLE_RESULT {winnerId, winnerMs}`, `GAME_OVER {podium[]}` |
| `/topic/game/{gameId}/host` (admin only) | `HOST_STATE` — full player list with per-question answer status |
| `/user/queue/player` (per player) | `ANSWER_ACK`, `RESULT {correct, points, streak, score, rank}`, `BATTLE_INVITE {battleId}` |

Timer is server-authoritative: `GameEngine` schedules auto-REVEAL at `startedAt + timeLimitSec` via `ScheduledExecutorService`; clients only *render* the countdown from `startedAt`.

---

## 4. Scoring & race-condition design

**Answer timing:** `response_ms = nanoTime-at-receipt − nanoTime-at-question-start` measured on the server. Client timestamps are ignored. Answers after the deadline or for a non-current question are dropped with an ack `{accepted:false}`.

**Points** (`ScoringService`, pure function, unit-tested):
```
base   = correct ? round(1000 * (1 − (response_ms / time_limit_ms) / 2)) : 0   // 1000 → 500 linear decay
streak = correct ? streak + 1 : 0
mult   = 1 + 0.1 * min(streak − 1, 5)                                          // 1.0x … 1.5x, kicks in on 2nd consecutive correct
points = round(base * mult)
```

**Duplicate answers:** in-memory `ConcurrentHashMap<playerId, Answer>` `putIfAbsent` per question, backed by `UNIQUE (player_id, question_id)`.

**Battle resolution** (`BattleService`): both answers arrive on separate WS threads. Winner = first *correct* answer by server receipt order, decided by one atomic statement:
```sql
UPDATE battle SET winner_id = :p, winner_ns = :ns, resolved_at = now()
WHERE id = :b AND winner_id IS NULL
```
Returns 1 → this player won; 0 → already resolved. Postgres row lock serialises the two writers; no application lock needed. Wrong answers never issue the UPDATE. If both are wrong by the timeout, `BATTLE_RESULT {winnerId:null}`. Winner gets a flat 1000 (× no streak).

---

## 5. Chronological implementation order

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

## 6. Verification
- `docker compose up -d && ./mvnw spring-boot:run` — Flyway applies V1 cleanly.
- `./mvnw test` — scoring, battle race, CSV import tests green.
- Manual E2E: admin logs in → imports CSV → creates game → host page shows PIN → two phones join → play 3 questions → host triggers a battle → podium → leads CSV contains both emails.
- Race check: open two player tabs, answer simultaneously in a battle via devtools `Promise.all` → exactly one `BATTLE_RESULT.winnerId`.

## Skipped (add when needed)
- Redis/RabbitMQ STOMP relay — only if you scale past one backend node.
- Player accounts / OAuth — email is a lead field, not a login.
- Question types beyond 4-option MCQ — schema change is one `type` column when asked.
