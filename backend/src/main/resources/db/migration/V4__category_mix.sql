-- docs/adr/0007: every question has one of three Categories, and every Game draws a fixed number from each.

-- The live Bank's older labels for the UK–Saudi theme. 'King Charles' and 'Gleeds' rows are already canonical.
UPDATE question SET category = 'UK–Saudi'
WHERE category IN ('UK', 'Saudi Arabia', 'UK-Saudi Relations', 'UK-Saudi');

-- Anything else (a label nobody anticipated, or none) must not stop the deploy, so it's parked rather than refused:
-- inactive, so no Game draws it, under the placeholder 'Gleeds' so the CHECK below holds. The Admin re-files it with
-- Edit, or deletes it.
UPDATE question SET active = false, category = 'Gleeds'
WHERE category IS NULL OR category NOT IN ('King Charles', 'UK–Saudi', 'Gleeds');

ALTER TABLE question
  ALTER COLUMN category SET NOT NULL,
  ADD CONSTRAINT question_category_check CHECK (category IN ('King Charles', 'UK–Saudi', 'Gleeds'));

-- A Game is the sum of the three; the old single size goes.
ALTER TABLE settings
  ADD COLUMN king_charles_per_game INT NOT NULL DEFAULT 4 CHECK (king_charles_per_game >= 0),
  ADD COLUMN uk_saudi_per_game     INT NOT NULL DEFAULT 3 CHECK (uk_saudi_per_game >= 0),
  ADD COLUMN gleeds_per_game       INT NOT NULL DEFAULT 3 CHECK (gleeds_per_game >= 0),
  ADD CONSTRAINT settings_per_game_check CHECK (king_charles_per_game + uk_saudi_per_game + gleeds_per_game > 0),
  DROP COLUMN questions_per_game;
