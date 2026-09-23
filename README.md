# King Charles Quiz

Real-time trivia for a Gleeds event — King Charles & UK–Saudi relations. Solo play from a QR code, 2–4 player Battles run from a big screen, one Day Leaderboard, JWT-protected admin panel.

Docs: [CONTEXT.md](CONTEXT.md) (glossary) · [spec](.scratch/quiz-app/spec.md) · [issues](https://github.com/HishamAbdulhak/Gleeds_KingCharles_Event/issues)

## Stack

- **Backend** `backend/` — Java 21, Spring Boot 4.1, Spring Security (JWT), STOMP over WebSocket, Spring Data JPA, Flyway, PostgreSQL 16
- **Frontend** `frontend/` — Next.js 16 (App Router), TypeScript, Tailwind 4, `@stomp/stompjs`

## Run it

```bash
docker compose up -d                      # Postgres 16 on :5432 (quiz/quiz)
```

```bash
cd backend && JWT_SECRET=change-me-to-32-plus-random-bytes ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD=change-me ./mvnw spring-boot:run   # API on :8080, Flyway applies the schema and seeds the admin
```

```bash
cd frontend && npm install && npm run dev # UI on :3000
```

Checks — what CI runs (backend tests need Docker for Testcontainers; frontend unit tests run on Node's own runner, Node ≥ 22.6):

```bash
./check
```

## Environment

| Variable | Used by | Default |
|---|---|---|
| `DB_URL` | backend | `jdbc:postgresql://localhost:5432/quiz` |
| `DB_USER` / `DB_PASSWORD` | backend | `quiz` / `quiz` |
| `JWT_SECRET` | backend | required, ≥ 32 bytes; signs the 24 h admin JWT |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | backend | required; seeded (bcrypt) by the V2 migration on first start |
| `CORS_ORIGIN` | backend | `http://localhost:3000` |
| `NEXT_PUBLIC_API_URL` | frontend | `http://localhost:8080` |
| `NEXT_PUBLIC_BASE_URL` | frontend | the Host screen's own origin; where the QR code sends phones |

Copy `frontend/.env.example` to `frontend/.env.local`. `.env*` files are gitignored.

## Deploy

The backend and Postgres 16 run on **Railway**, as defined in [`.railway/railway.ts`](.railway/railway.ts): the `api` service builds [`backend/Dockerfile`](backend/Dockerfile) from `main`. The frontend runs on **Vercel**, from `frontend/`. Secrets are Railway or Vercel variables and never go in the repo.

First deploy (once; Railway CLI ≥ 5.42.1, and the Railway GitHub app needs access to this repo):

```bash
railway login
```

```bash
railway init --name king-charles-quiz
```

```bash
npm ci --prefix .railway && railway config plan
```

```bash
railway config apply
```

```bash
railway variable set JWT_SECRET=$(openssl rand -base64 48) --service api
railway variable set ADMIN_EMAIL=… --service api
railway variable set ADMIN_PASSWORD=… --service api
```

The `api` service fails to start until these three are set: the app refuses to boot without them.

```bash
railway domain --service api
```

1. Import the repo on Vercel with **Root Directory** `frontend`. Set `NEXT_PUBLIC_API_URL` to `https://` plus the Railway domain above, and `NEXT_PUBLIC_BASE_URL` to the Vercel production URL (the QR code points there, so a wrong value means a dead QR). Then deploy.
2. `railway variable set CORS_ORIGIN=https://<vercel production host> --service api`. Use the exact origin, with no trailing slash. It gates REST and the `/ws` handshake, so the Vercel preview URLs are refused.

**Redeploy**: push to `main`. Vercel rebuilds the frontend. Railway rebuilds the backend only when `backend/**` changed. That restart ends every live Game (they're held in memory), so don't merge backend changes during the event. An edit to `railway.ts` goes out with `railway config apply`. A change to a `NEXT_PUBLIC_*` variable needs a Vercel redeploy, because those values are baked in at build time.

**Before the event**, rotate what was used in testing, then log the Host screen in that morning (the Admin JWT lasts 24 h):

- `JWT_SECRET`: run `railway variable set JWT_SECRET=$(openssl rand -base64 48) --service api`. It redeploys, and the Admin and the Host screen have to log in again.
- The Admin password: `ADMIN_PASSWORD` only seeds the first boot (the V2 migration), so changing the variable does nothing. Run `railway connect postgres`, then `UPDATE admin_user SET password_hash = crypt('<new>', gen_salt('bf', 10)) WHERE email = '<ADMIN_EMAIL>';`.

**Smoke check**: first log in as Admin, import questions and keep Settings → Questions per Game at or below the active count (an empty Question Bank refuses every Game with a 409). Then, on a real phone over mobile data: scan the QR on the Host screen, play Solo to game-over and see the rank, and watch the Host screen update live.

## Skipped (add when needed)

- Redis/RabbitMQ STOMP relay — only if you scale past one backend node.
- Player accounts / OAuth — the email only tells Players apart; it isn't a login.
- Question types beyond 4-option MCQ — schema change is one `type` column when asked.
