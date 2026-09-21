# Checking a Game in the browser

Servers: `README.md` → Run it. Admin credentials are whatever `ADMIN_EMAIL` / `ADMIN_PASSWORD` the backend was started with.

1. **Short Games**: `docker compose exec -T db psql -U quiz -d quiz -c "update settings set questions_per_game = 1"` — a Solo run is then ready → one answer → 3 s → game-over. Put the setting back afterwards.
2. **Admin pages** (`/admin/**`, `/host`): log in at `/admin/login`; the token is `localStorage.adminToken` on the frontend origin.
3. **A Solo Player**: `curl -s -X POST localhost:8080/api/solo -H 'Content-Type: application/json' -d '{"name":"Zed","email":"zed@example.com","consent":true}'` returns `gameId` + `sessionToken`. In a second tab on `/play`, set `sessionStorage['seat:<gameId>'] = '<sessionToken>'` (the Seat), then open `/play/<gameId>`; the page connects, says ready and shows the first question.
4. **Live Host updates**: keep `/host` in the first tab while the second plays; the board changes on `GAME_OVER` without a reload.

A refused STOMP CONNECT / SUBSCRIBE reaches the page as the server's ERROR frame: the Player page shows its message; `/host` logs out. Backend logs: `preview_logs` on the backend server id.
