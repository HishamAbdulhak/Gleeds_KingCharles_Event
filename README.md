# King Charles Quiz

Real-time trivia for a Gleeds event — King Charles & UK–Saudi relations. Solo play from a QR code, 2–4 player Battles run from a big screen, one Day Leaderboard, lead capture, JWT-protected admin panel.

Docs: [PLAN.md](PLAN.md) · [CONTEXT.md](CONTEXT.md) (glossary) · [spec](.scratch/quiz-app/spec.md) · [issues](https://github.com/HishamAbdulhak/Gleeds_KingCharles_Event/issues)

## Stack

- **Backend** `backend/` — Java 21, Spring Boot 4.1, Spring Security (JWT), STOMP over WebSocket, Spring Data JPA, Flyway, PostgreSQL 16
- **Frontend** `frontend/` — Next.js 16 (App Router), TypeScript, Tailwind 4, `@stomp/stompjs`

## Run it

```bash
docker compose up -d                      # Postgres 16 on :5432 (quiz/quiz)
```

```bash
cd backend && ./mvnw spring-boot:run      # API on :8080, Flyway applies the schema
```

```bash
cd frontend && npm install && npm run dev # UI on :3000
```

Tests (backend needs Docker for Testcontainers):

```bash
cd backend && ./mvnw test
```

## Environment

| Variable | Used by | Default |
|---|---|---|
| `DB_URL` | backend | `jdbc:postgresql://localhost:5432/quiz` |
| `DB_USER` / `DB_PASSWORD` | backend | `quiz` / `quiz` |
| `JWT_SECRET` | backend | required, ≥ 32 bytes (wired in #2) |
| `CORS_ORIGIN` | backend | `http://localhost:3000` (wired in #2) |
| `NEXT_PUBLIC_API_URL` | frontend | `http://localhost:8080` |

Copy `frontend/.env.example` to `frontend/.env.local`. `.env*` files are gitignored.
