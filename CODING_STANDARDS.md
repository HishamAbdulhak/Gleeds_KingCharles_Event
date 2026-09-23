# Coding standards

Read at review. The stack skills (`.agents/skills/<name>/SKILL.md`) are the baseline; this file records where this repo overrides them and the few rules that have already bitten.

## Spec overrides the skills

- Tests sit at the HTTP + STOMP boundary on Testcontainers Postgres (`spec.md` → Testing Decisions); the scoring function is the one pure unit test. `./check` greps out slice tests and mock beans.
- Frontend (added with #6, beyond the spec's "tsc/lint only"): pure state logic — the player reducer — gets a unit test on Node's own runner (`npm test`, no framework); components are covered by tsc, lint and a manual phone-viewport check.
- `GameEngine` holds live state in memory (spec → Architecture). "Services are stateless" does not apply to it.
- `WsAuthInterceptor` refuses by sending a STOMP ERROR frame, not by throwing (`docs/adr/0001`).
- Raw WebSocket with the simple broker: no SockJS, heartbeats or external broker.
- Packages are by feature (`game`, `question`, `admin`) with entities beside their controllers; cross-cutting Spring config (security, WebSocket, the STOMP interceptor) lives in `config/` even when it depends on a feature package.
- Controllers may be `@Transactional` and call repositories directly; a service class needs a second caller.
- Paths use glossary vocabulary (`/api/solo`), not forced plurals.
- Ponytail governs review: a value used once stays a literal with a comment; extract on the second use. A race goes unguarded only when the next event corrects it on the screen: check the rendered state, not the event stream (a duplicate `RESULT` reaches `reducePlayer` in phase `result` and blanks the phone's result screen).
- A request field with one legal value is ignored, not validated: the client and the tests still send the shape the issue names; the server reads only what it uses.

## Rules that have bitten

- Collections load through `@EntityGraph` or `JOIN FETCH` (`./check` greps out `EAGER`).
- A switch over a wire event keeps a `default` arm with `satisfies never`: tsc checks the declared union, but the wire can send an event outside it, and a reducer that falls off its switch returns `undefined` and white-screens the page (`./check` greps for it beside `case "GAME_OVER"`).
- PgJDBC hands `SMALLINT` back as `Integer`, not `Short`; JdbcTemplate assertions compare against `int`.
- Every method that modifies rows is `@Transactional`; STOMP publishes happen in `afterCommit`, so a client never sees a row the database doesn't. `GameEngine` uses a `TransactionTemplate` instead: its timer callbacks are internal calls, which a `@Transactional` proxy never sees.
- `201 Created` carries a `Location` header only when a `GET` for that resource exists.
- A domain term used in code is in `CONTEXT.md`; add the glossary entry with the code that introduces it.
- A change to the STOMP wire contract ships with an ADR in `docs/adr/` recording the decision and its compatibility impact; an amended spec line alone is not enough (`docs/adr/0002`).
- A branch closes one issue. Repo-wide cleanup gets its own branch so the feature's spec review stays clean.
- The simple broker honours Ant wildcards, so `WsAuthInterceptor` allow-lists destinations per principal. A new topic ships with one boundary test: the wrong principal subscribing to `/topic/*` gets the ERROR frame.
- A STOMP SUBSCRIBE can lose to a REST command issued on another connection. A boundary test waits for each client's first message on its subscription (a Battle phone's own `LOBBY_UPDATE`) before sending a command that depends on it, and skips the lobby chatter an early joiner also receives (`BattleGameTest.afterLobby`). Without the barrier the suite is flaky, not wrong.
- An ordering test seeds every lower-priority sort key to disagree with the key under test, so the test goes red without that key.
