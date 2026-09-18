# King Charles Quiz

Kahoot-style real-time quiz (Spring Boot + STOMP backend, Next.js frontend, Postgres). See `PLAN.md` for architecture, schema, and build order.

## Stack skills

Local only (gitignored), installed with `npx skills add <pkg> -y`: `github/awesome-copilot@java-springboot`, `giuseppe-trisciuoglio/developer-kit@spring-boot-security-jwt`, `giuseppe-trisciuoglio/developer-kit@spring-boot-test-patterns`, `giuseppe-trisciuoglio/developer-kit@spring-boot-rest-api-standards`, `wshobson/agents@postgresql-table-design`, `vercel-labs/agent-skills@vercel-react-best-practices`. Use them for conventions. Where they suggest `@WebMvcTest` / `@MockBean` slice tests, the spec wins: tests go at the HTTP+STOMP boundary against Testcontainers Postgres (see `.scratch/quiz-app/spec.md` → Testing Decisions), plus pure unit tests for the scoring function only.

## Agent skills

### Issue tracker

GitHub Issues on `HishamAbdulhak/Gleeds_KingCharles_Event` via the `gh` CLI; the spec lives at `.scratch/quiz-app/spec.md`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at repo root. See `docs/agents/domain.md`.
