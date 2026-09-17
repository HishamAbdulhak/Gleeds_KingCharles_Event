# 09: Battle game loop

**What to build:** From a full lobby the Host taps Start and runs a Battle: every phone and the big screen show each question at the same moment; the Host sees how many have answered and can reveal early; the reveal shows the correct option and how many chose each; a leaderboard appears between questions; the Podium ranks the whole lobby at the end; the big screen returns to the idle Day Leaderboard, which now includes the Battle's Scores.

Spec: `.scratch/quiz-app/spec.md` (Game flow → Battle; STOMP contract; User stories 18–21, 30–36).

**Blocked by:** 07 (Day Leaderboard and Host idle screen), 08 (Battle lobby)

**Status:** ready-for-agent

- [ ] Host REST commands (Admin): `start` (LOBBY → QUESTION 0), `reveal` (force end of the current question), `next` (REVEAL/LEADERBOARD → next QUESTION, or → FINISHED after the last), `end` (→ FINISHED from any state); each is idempotent and returns the new status
- [ ] Battle question flow reuses the 05 engine: same `QUESTION_START`, Answer handler, timer and scoring; no auto-advance — the Host drives it
- [ ] `ANSWER_COUNT {answered, total}` published on the Game topic after every accepted Answer
- [ ] When a question ends (timer or reveal, or all Players answered — end immediately in that case): each Player gets their `RESULT`; the Game topic gets `REVEAL {correctOption, counts:[4]}`
- [ ] `next` after a reveal publishes `LEADERBOARD {players:[{name, score, delta}]}` for the whole lobby, then on the following `next` the next question; after the last question publishes `GAME_OVER {podium:[…all Players ranked…]}`, marks FINISHED, and pushes `/topic/leaderboard`
- [ ] `/topic/game/{id}/host` publishes `HOST_STATE` (per-Player answered/score) and rejects non-Admin subscriptions
- [ ] Host screens on `/host/[gameId]`: question (big text, four options, timer, "3 of 4 answered", Reveal button), reveal (correct option highlighted, per-option bars, Next button), leaderboard (Next button), Podium (top 3 large, rest listed, "Back to leaderboard" → `/host`); End Battle available on every screen
- [ ] Player screens reuse 06 components: question → locked → personal result; between questions "Look at the big screen"; on `GAME_OVER` show own place in the Podium
- [ ] A Player who disconnects is simply absent from later Answers; their Score stands and they still appear on the Podium
- [ ] Boundary tests: two Players, 3-question Battle: start → `QUESTION_START` on both clients → both answer → `ANSWER_COUNT` 1 then 2 → `REVEAL` with counts summing to 2 → `next` → `LEADERBOARD` → … → `GAME_OVER` podium of length 2 → Admin client receives `DAY_LEADERBOARD` containing both emails; a Player STOMP client's subscribe to the Host topic is refused; `reveal` before the deadline ends the question and a later Answer is rejected
