# Checking a Game in the browser

Servers: `preview_start` with the `frontend` and `backend` entries of `.claude/launch.json` (gitignored; `README.md` → Run it is the same by hand). The admin login is the `-Dadmin.email` / `-Dadmin.password` in that file's `backend` entry, or `ADMIN_EMAIL` / `ADMIN_PASSWORD` for a backend started from the README.

Before a walkthrough:

- **Java changed since the backend started?** `preview_stop` it, then `preview_start` it. The frontend hot-reloads; the backend does not, and `preview_start` reuses a running server, so the walkthrough would exercise the old build.
- **`read_console_messages` keeps its buffer across navigations.** Confirm the current page rendered (`read_page` or a screenshot) before reading a console error as evidence about it.

1. **Short Games**: `docker compose exec -T db psql -U quiz -d quiz -c "update settings set questions_per_game = 1"` — a Solo run is then ready → one answer → 3 s → game-over. When the walkthrough ends, restore the default of 10 (`V1__init.sql`): `docker compose exec -T db psql -U quiz -d quiz -c "update settings set questions_per_game = 10"`.
2. **Admin pages** (`/admin/**`, `/host`): log in at `/admin/login`; the token is `localStorage.adminToken` on the frontend origin.
3. **A Solo Player**: `curl -s -X POST localhost:8080/api/solo -H 'Content-Type: application/json' -d '{"name":"Zed","email":"zed@example.com","consent":true}'` returns `gameId` + `sessionToken`. In a second tab on `/play`, set `sessionStorage['seat:<gameId>'] = '<sessionToken>'` (the Seat), then open `/play/<gameId>`; the page connects, says ready and shows the first question.
4. **Live Host updates**: keep `/host` in the first tab while the second plays; the board changes on `GAME_OVER` without a reload.
5. **A Battle lobby**: on `/host` tap New Battle; the PIN is on `/host/<gameId>`. Join from `/join` in a second tab, or `curl -s -X POST localhost:8080/api/games/<pin>/join -H 'Content-Type: application/json' -d '{"name":"Bob","email":"bob@example.com","consent":true}'` (the response's `playerId` also goes in `sessionStorage['player:<gameId>']`, which is how the phone finds itself on the Podium); the Host list and the phone's lobby update as each one lands, and a reload of either re-syncs (`ready` → `LOBBY_UPDATE`).
6. **A Battle**: Start enables at 2 Players. From there the Host drives it: the question screen counts "1 of 2 answered" as the phones tap, Reveal ends it early (or the timer does), Next shows the Standings, Next again the following question, and after the last one the Podium — 3rd, 2nd, 1st a beat apart, each phone showing its own place just after its row settles on the big screen. End Battle is on every screen but the Podium, which has only Back to leaderboard. Each command answers with the Game's new status, so `curl -s -X POST localhost:8080/api/games/<gameId>/next -H "Authorization: Bearer <admin JWT>"` drives it without the screen.

A refused STOMP CONNECT / SUBSCRIBE reaches the page as the server's ERROR frame: the Player page shows its message; `/host` logs out. Backend logs: `preview_logs` on the backend server id.
