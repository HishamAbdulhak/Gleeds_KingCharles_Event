# Checking a Game in the browser

Both dev servers come from `.claude/launch.json` (`backend`, `frontend`); the backend entry carries the dev admin credentials. Postgres is `docker compose up -d`.

1. **Short Games**: `docker compose exec -T db psql -U quiz -d quiz -c "update settings set questions_per_game = 1"` — a Solo run is then ready → one answer → 3 s → game-over. Restore to 10 when done.
2. **Admin pages** (`/admin/**`, `/host`): log in at `/admin/login` with the `launch.json` credentials; the token lives in `localStorage.adminToken` for the tab.
3. **A Solo Player**: `curl -s -X POST localhost:8080/api/solo -H 'Content-Type: application/json' -d '{"name":"Zed","email":"zed@example.com","consent":true}'` returns `gameId` + `sessionToken`. In a second tab on `/play`, set `sessionStorage['seat:<gameId>'] = '<sessionToken>'`, then open `/play/<gameId>`; the page connects, says ready and shows the first question.
4. **Live Host updates**: keep `/host` in the first tab while the second plays; the board changes on `GAME_OVER` without a reload.

Server logs: `preview_logs` on the backend server id; STOMP refusals show as ERROR frames in the browser console.
