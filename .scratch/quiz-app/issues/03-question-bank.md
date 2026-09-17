# 03: Question Bank CRUD and CSV/XLSX import

**What to build:** A logged-in Admin sees the Question Bank in a table, can add / edit / deactivate / delete a question, and can upload the client's CSV or XLSX and be told how many rows imported and exactly which rows failed and why.

Spec: `.scratch/quiz-app/spec.md` (Admin API → Question CRUD; import header `text,a,b,c,d,correct,time_limit,category`, `correct` as A–D, last two optional).

**Blocked by:** 02 (Admin login)

**Status:** ready-for-agent

- [ ] Question entity + repository matching the `question` table
- [ ] `GET/POST/PUT/DELETE /api/admin/questions` with bean validation (four non-blank options, correct 0–3, time limit 5–120); DELETE of a question referenced by a `game_question` row deactivates instead of deleting
- [ ] `POST /api/admin/questions/import` accepts multipart CSV or XLSX, parses the header row case-insensitively, converts `correct` A–D to 0–3, defaults time limit to 20, inserts valid rows in one transaction, and returns `{imported, errors: [{row, message}]}`
- [ ] `/admin/questions` page: table with text, correct answer, time limit, category, active; add/edit modal; deactivate and delete buttons; file upload with the import summary shown inline
- [ ] Boundary test: import a 4-row CSV where one row has an invalid `correct` value → 3 questions exist, response lists the bad row number
- [ ] Boundary test: same sheet as XLSX imports identically
