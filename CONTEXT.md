# King Charles Quiz

A real-time trivia app for a Gleeds event, themed on King Charles and UK–Saudi relations. Players play on their phones; a big screen shows the Host view; the whole day competes for one prize.

## Language

**Game**:
One run of questions, in one Mode, that produces scores. Never reused; a new run is a new Game.
_Avoid_: Quiz (as a noun for a run), session, round

**Mode**:
The kind of Game: Solo or Battle. Every Game has exactly one.

**Solo**:
A Mode where one Player plays alone with no Host, lobby, or PIN, reached by scanning the QR code on the Host screen. Questions are still timed.
_Avoid_: Single player, kiosk, quiz mode

**Battle**:
A Mode where 2–4 Players in one lobby answer the same questions at the same time, driven by the Host.
_Avoid_: 1v1, duel, multiplayer

**Player**:
A person who has joined a Game by giving their name and email. One Player belongs to exactly one Game.
_Avoid_: User, participant, contestant, applicant

**Host**:
The big-screen view that drives a Battle and shows the Day Leaderboard. Operated by an Admin.
_Avoid_: Presenter, screen, moderator

**Admin**:
A Gleeds staff login that manages the Question Bank and runs Battles.

**PIN**:
The 6-digit code Players enter to join a Battle lobby.
_Avoid_: Room code, game code

**Question Bank**:
All questions an Admin has entered or imported, from which Games draw.

**Answer**:
A Player's single, final choice for one question. A Player cannot change or repeat an Answer.
_Avoid_: Response, submission, guess

**Points**:
What an Answer earns: full value for a correct Answer at time zero, decaying with response time, multiplied by the Streak. Same formula in both Modes.

**Streak**:
The number of consecutive correct Answers a Player currently has. Multiplies Points; resets to zero on a wrong Answer or timeout.

**Score**:
The sum of a Player's Points within one Game.

**Podium**:
The end-of-Game ranking of every Player in that Game.
_Avoid_: Results, final leaderboard

**Day Leaderboard**:
The ranking of every Player's best Score across all Games since the last Reset, shown on the Host screen. Solo and Battle Games rank on the same board because every Game has the same number of questions. Ties break on faster total response time. The top Score at the end of the day wins the prize.
_Avoid_: Global leaderboard, high scores, event leaderboard

**Reset**:
An Admin action that empties the Day Leaderboard. Earlier Games and Leads are kept; they simply stop counting.
_Avoid_: Clear, wipe, delete scores

**Replay**:
The same email playing another Game. Allowed; only the best Score counts on the Day Leaderboard.

**Question Set**:
The fixed-size random draw from the Question Bank that one Game plays. Every Game in the day has the same size.
_Avoid_: Quiz, round, pack

**Lead**:
The name and email captured when a Player joins. What Gleeds actually keeps.
