# 02: Admin login

**What to build:** An Admin opens `/admin/login`, enters the seeded email and password, and is taken to the admin area with a session that lasts the day. Anyone hitting an admin endpoint without a valid token is refused.

Spec: `.scratch/quiz-app/spec.md` (Admin API, Security).

**Blocked by:** 01 (Scaffold, full schema, and test harness)

**Status:** ready-for-agent

- [ ] A Flyway migration seeds one Admin from `ADMIN_EMAIL` / `ADMIN_PASSWORD` env vars (bcrypt hashed at startup if the migration can't read env; either way, no plaintext in the repo)
- [ ] `POST /api/admin/login` with email + password returns a JWT valid for 24 h; wrong credentials return 401
- [ ] A Bearer-JWT filter populates the security context with role ADMIN; every path under `/api/admin/**` requires it; `/api/health` stays public
- [ ] `/admin/login` page posts the form, stores the token in localStorage, redirects to `/admin`; `/admin` shows a bare shell with a logout link and redirects to login when no token is present
- [ ] A small API client on the frontend attaches the token to every admin request
- [ ] Boundary tests: login 200 with a token; login 401 on bad password; a placeholder admin endpoint (e.g. `GET /api/admin/me`) is 401 without a token and 200 with one
