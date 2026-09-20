# Coding standards

Read at review. The stack skills (`.agents/skills/<name>/SKILL.md`) are the baseline; this file records where this repo overrides them and the few rules that have already bitten.

## Spec overrides the skills

- Tests sit at the HTTP + STOMP boundary on Testcontainers Postgres (`spec.md` → Testing Decisions). No `@WebMvcTest`, `@MockBean` or slice tests; the scoring function is the one pure unit test.
- `GameEngine` holds live state in memory (spec → Architecture). "Services are stateless" does not apply to it.
- `WsAuthInterceptor` refuses by sending a STOMP ERROR frame, not by throwing (`docs/adr/0001`).
- Raw WebSocket with the simple broker: no SockJS, heartbeats or external broker.
- `WsAuthInterceptor` lives in `config/` and depends on `game.PlayerRepository` (`PLAN.md` layout).
- Controllers may be `@Transactional` and call repositories directly; a service class needs a second caller.
- Paths use glossary vocabulary (`/api/solo`), not forced plurals.

## Rules that have bitten

- Collections load through `@EntityGraph` or `JOIN FETCH`, never `fetch = EAGER`.
- Every method that modifies rows is `@Transactional`; STOMP publishes happen in `afterCommit`, so a client never sees a row the database doesn't.
- `201 Created` carries a `Location` header only when a `GET` for that resource exists.
- A domain term used in code is in `CONTEXT.md`; add the glossary entry with the code that introduces it.
- A branch closes one issue. Repo-wide cleanup gets its own branch so the feature's spec review stays clean.
