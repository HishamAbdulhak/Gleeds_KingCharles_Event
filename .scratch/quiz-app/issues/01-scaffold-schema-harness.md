# 01: Scaffold, full schema, and test harness

**What to build:** A developer can clone the repo, run one command for Postgres, start the backend and see Flyway apply the complete schema from the spec, run the test suite and watch one boundary test go green against a Testcontainers Postgres, and start the frontend to see a themed placeholder page. Everything later slices need to exist is in place; nothing user-facing works yet.

Spec: `.scratch/quiz-app/spec.md` (Implementation Decisions → Architecture, Domain model / schema). Vocabulary: `CONTEXT.md`. Layout: `PLAN.md` §1.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `docker compose up -d` starts Postgres 16 with a dev database, user and password matching the backend's default config
- [ ] Spring Boot 3 / Java 21 / Maven backend with web, websocket, security, data-jpa, postgresql, flyway, validation, jjwt, commons-csv, poi-ooxml, testcontainers (postgresql) dependencies
- [ ] Flyway V1 creates every table in the spec: admin_user, question, settings (single row inserted), game (with `mode`, nullable `pin`), game_question, player (with `consented_at`, `session_token`), answer — with the unique constraints and checks the spec names; no `battle` table
- [ ] A public `GET /api/health` returns 200
- [ ] Security is wired so the app boots with a stateless config that permits `/api/health` (admin rules come in ticket 02)
- [ ] One boundary test boots the app on a random port against a Testcontainers Postgres (`@ServiceConnection`) and asserts `/api/health` is 200 and the Flyway migration ran
- [ ] `./mvnw test` passes
- [ ] Next.js (App Router, TypeScript, Tailwind) frontend with `@stomp/stompjs` installed, a root layout carrying the royal purple / gold / Saudi green theme, and a placeholder home page; `npm run build` passes
- [ ] README lists the three commands (compose, backend, frontend) and the env vars (`DB_URL`, `JWT_SECRET`, `CORS_ORIGIN`, `NEXT_PUBLIC_API_URL`)
