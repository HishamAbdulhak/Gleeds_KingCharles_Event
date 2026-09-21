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
- Ponytail governs review: a value used once stays a literal with a comment; extract on the second use. A race the next event corrects gets no guard.

## Rules that have bitten

- Collections load through `@EntityGraph` or `JOIN FETCH` (`./check` greps out `EAGER`).
- PgJDBC hands `SMALLINT` back as `Integer`, not `Short`; JdbcTemplate assertions compare against `int`.
- Every method that modifies rows is `@Transactional`; STOMP publishes happen in `afterCommit`, so a client never sees a row the database doesn't. `GameEngine` uses a `TransactionTemplate` instead: its timer callbacks are internal calls, which a `@Transactional` proxy never sees.
- `201 Created` carries a `Location` header only when a `GET` for that resource exists.
- A domain term used in code is in `CONTEXT.md`; add the glossary entry with the code that introduces it.
- A branch closes one issue. Repo-wide cleanup gets its own branch so the feature's spec review stays clean.
- The simple broker honours Ant wildcards, so `WsAuthInterceptor` allow-lists destinations per principal. A new topic needs a "wrong principal subscribing to `/topic/*` is refused" boundary test, not an interceptor branch.
- An ordering test seeds every lower-priority sort key to disagree with the key under test, so the test goes red without that key.
