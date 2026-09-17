# 10: Leads export, Reset, and settings

**What to build:** In the admin area a staff member downloads all Leads as one deduplicated CSV, changes how many questions each Game draws, and presses Reset to empty the Day Leaderboard before doors open — without losing a single Lead or past Game.

Spec: `.scratch/quiz-app/spec.md` (Admin API; User stories 42–46). `CONTEXT.md`: Reset, Lead.

**Blocked by:** 07 (Day Leaderboard and Host idle screen)

**Status:** ready-for-agent

- [ ] `GET /api/admin/leads.csv` streams `name,email,consented_at,best_score,first_seen,games_played`, one row per lowercased email across all Games regardless of Reset, sorted by best score descending
- [ ] `GET/PUT /api/admin/settings` for `questionsPerGame` (validated 1–50); new Games draw the updated count; running Games are unaffected
- [ ] `POST /api/admin/reset` sets `settings.leaderboard_since = now()` and publishes an empty `DAY_LEADERBOARD` on `/topic/leaderboard`; nothing is deleted
- [ ] `/admin/settings` page: questions-per-Game input with save, and a Reset button behind a confirm dialog that states Leads are kept
- [ ] `/admin/leads` page: shows the Lead count and a Download CSV button
- [ ] `/admin` shell links to Questions, Settings, Leads and the Host screen
- [ ] Boundary tests: two finished Games → Reset → `GET /api/leaderboard` is empty and the Admin client received an empty `DAY_LEADERBOARD` → a new Game finishes and appears → `leads.csv` still lists all three emails; PUT settings to 3 → next `POST /api/solo` Game has exactly 3 questions
