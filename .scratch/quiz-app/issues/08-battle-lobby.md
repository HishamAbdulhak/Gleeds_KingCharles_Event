# 08: Battle lobby

**What to build:** A Host taps "New Battle" and the big screen shows a PIN. Visitors go to `/join`, enter the PIN plus name, email and consent, and appear on the big screen's lobby list in real time — up to 4 of them. The Host's Start button is disabled until at least 2 have joined. Starting the Battle (questions, reveal, Podium) is ticket 09.

Spec: `.scratch/quiz-app/spec.md` (Game flow → Battle; User stories 15–17, 22, 26–29). `CONTEXT.md`: Battle, PIN, Host.

**Blocked by:** 05 (Backend game loop and scoring)

**Status:** ready-for-agent

- [ ] `POST /api/games` (Admin) with `{mode:"BATTLE"}` → creates a Game in LOBBY with a unique 6-digit PIN (retry on collision) and a Question Set drawn as in Solo; returns `{gameId, pin}`
- [ ] `POST /api/games/{pin}/join` (public) with name, email, consent: 404 unknown PIN; 409 `LOBBY_FULL` when 4 Players exist; 409 `GAME_STARTED` when status ≠ LOBBY; same email already in this Game → returns that Player's existing ids and session token (reconnect) without creating a row; otherwise creates the Player and returns `{gameId, playerId, sessionToken}`
- [ ] On every join the engine publishes `LOBBY_UPDATE {players:[{id,name}]}` on the Game topic
- [ ] `POST /api/games/{id}/start` (Admin) → 409 with fewer than 2 Players; otherwise 202 and no further behaviour yet (09 wires the loop) — the engine transition to QUESTION may land here if trivial, but the acceptance bar is the guard
- [ ] `/join` page: PIN (6 digits, numeric keyboard), name, email, consent; friendly messages for full / started / unknown; on success routes to `/play/[gameId]`, which shows "You're in — waiting for the Host" with the live lobby names until a `QUESTION_START` arrives
- [ ] `/host` "New Battle" creates the Game and routes to `/host/[gameId]`: PIN in huge type, live list of joined names, Start button disabled below 2 and showing "2–4 players", an "End Battle" link back to `/host`
- [ ] Boundary tests: create → two joins appear in `LOBBY_UPDATE`s → fifth join is 409 → same email rejoin returns the same playerId and token → start with one Player is 409, with two is 202
