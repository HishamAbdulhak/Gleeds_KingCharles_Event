-- Vocabulary: see CONTEXT.md. Schema: .scratch/quiz-app/spec.md → Domain model.

CREATE TABLE admin_user (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email         TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX admin_user_email_uidx ON admin_user (lower(email));

CREATE TABLE question (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  text           TEXT NOT NULL,
  option_a       TEXT NOT NULL,
  option_b       TEXT NOT NULL,
  option_c       TEXT NOT NULL,
  option_d       TEXT NOT NULL,
  correct_option SMALLINT NOT NULL CHECK (correct_option BETWEEN 0 AND 3),
  time_limit_sec INT NOT NULL DEFAULT 20 CHECK (time_limit_sec BETWEEN 5 AND 120),
  category       TEXT,
  active         BOOLEAN NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- exactly one row; Reset bumps leaderboard_since, nothing is deleted
CREATE TABLE settings (
  id                 BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (id),
  questions_per_game INT NOT NULL DEFAULT 10 CHECK (questions_per_game > 0),
  leaderboard_since  TIMESTAMPTZ NOT NULL DEFAULT 'epoch'
);
INSERT INTO settings DEFAULT VALUES;

CREATE TABLE game (
  id                     UUID PRIMARY KEY,
  mode                   TEXT NOT NULL CHECK (mode IN ('SOLO', 'BATTLE')),
  pin                    TEXT UNIQUE CHECK (pin ~ '^[0-9]{6}$'),
  status                 TEXT NOT NULL DEFAULT 'LOBBY'
                         CHECK (status IN ('LOBBY', 'QUESTION', 'REVEAL', 'LEADERBOARD', 'FINISHED')),
  current_question_index INT NOT NULL DEFAULT -1,
  question_started_at    TIMESTAMPTZ,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at               TIMESTAMPTZ,
  CHECK ((mode = 'BATTLE') = (pin IS NOT NULL))
);

-- the Question Set: snapshot of the random draw, so later edits to the Bank don't rewrite history
CREATE TABLE game_question (
  game_id     UUID   NOT NULL REFERENCES game(id) ON DELETE CASCADE,
  question_id BIGINT NOT NULL REFERENCES question(id) ON DELETE RESTRICT,
  position    INT    NOT NULL,
  PRIMARY KEY (game_id, position)
);
CREATE INDEX game_question_question_idx ON game_question (question_id);

CREATE TABLE player (
  id            UUID PRIMARY KEY,
  game_id       UUID NOT NULL REFERENCES game(id) ON DELETE CASCADE,
  name          TEXT NOT NULL,
  email         TEXT NOT NULL,
  consented_at  TIMESTAMPTZ NOT NULL,
  session_token UUID NOT NULL UNIQUE,
  score         INT  NOT NULL DEFAULT 0,
  streak        INT  NOT NULL DEFAULT 0,
  joined_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- one seat per email per Game, case-insensitive (same email = reconnect, not a second Player)
CREATE UNIQUE INDEX player_game_email_uidx ON player (game_id, lower(email));
CREATE INDEX player_email_idx ON player (lower(email));   -- Day Leaderboard / Leads group by email

CREATE TABLE answer (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  game_id         UUID   NOT NULL REFERENCES game(id) ON DELETE CASCADE,
  player_id       UUID   NOT NULL REFERENCES player(id) ON DELETE CASCADE,
  question_id     BIGINT NOT NULL REFERENCES question(id) ON DELETE RESTRICT,
  selected_option SMALLINT NOT NULL CHECK (selected_option BETWEEN 0 AND 3),
  correct         BOOLEAN NOT NULL,
  response_ms     INT NOT NULL CHECK (response_ms >= 0),
  points          INT NOT NULL CHECK (points >= 0),
  submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (player_id, question_id)
);
CREATE INDEX answer_game_idx ON answer (game_id);
CREATE INDEX answer_question_idx ON answer (question_id);
