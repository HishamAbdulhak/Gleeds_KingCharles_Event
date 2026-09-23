# Checking a Game in the browser

Servers: `README.md` → Run it. Admin credentials are whatever `ADMIN_EMAIL` / `ADMIN_PASSWORD` the backend was started with.

1. **Short Games**: `docker compose exec -T db psql -U quiz -d quiz -c "update settings set questions_per_game = 1"` — a Solo run is then ready → one answer → 3 s → game-over. Put the setting back afterwards.
2. **Admin pages** (`/admin/**`, `/host`): log in at `/admin/login`; the token is `localStorage.adminToken` on the frontend origin.
3. **A Solo Player**: `curl -s -X POST localhost:8080/api/solo -H 'Content-Type: application/json' -d '{"name":"Zed","email":"zed@example.com","consent":true}'` returns `gameId` + `sessionToken`. In a second tab on `/play`, set `sessionStorage['seat:<gameId>'] = '<sessionToken>'` (the Seat), then open `/play/<gameId>`; the page connects, says ready and shows the first question.
4. **Live Host updates**: keep `/host` in the first tab while the second plays; the board changes on `GAME_OVER` without a reload.
5. **A Battle lobby**: on `/host` tap New Battle; the PIN is on `/host/<gameId>`. Join from `/join` in a second tab, or `curl -s -X POST localhost:8080/api/games/<pin>/join -H 'Content-Type: application/json' -d '{"name":"Bob","email":"bob@example.com","consent":true}'` (the response's `playerId` also goes in `sessionStorage['player:<gameId>']`, which is how the phone finds itself on the Podium); the Host list and the phone's lobby update as each one lands, and a reload of either re-syncs (`ready` → `LOBBY_UPDATE`).
6. **A Battle**: Start enables at 2 Players. From there the Host drives it: the question screen counts "1 of 2 answered" as the phones tap, Reveal ends it early (or the timer does), Next shows the leaderboard, Next again the following question, and after the last one the Podium — 3rd, 2nd, 1st a beat apart, each phone showing its own place at the same moment. End Battle is on every screen. Each command answers with the Game's new status, so `curl -s -X POST localhost:8080/api/games/<gameId>/next -H "Authorization: Bearer <admin JWT>"` drives it without the screen.

A refused STOMP CONNECT / SUBSCRIBE reaches the page as the server's ERROR frame: the Player page shows its message; `/host` logs out. Backend logs: `preview_logs` on the backend server id.
