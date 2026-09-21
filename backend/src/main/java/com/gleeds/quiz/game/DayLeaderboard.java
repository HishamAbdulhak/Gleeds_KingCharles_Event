package com.gleeds.quiz.game;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The Day Leaderboard (spec → Day Leaderboard): every email's best Score over the Games created since the last Reset,
 * any Mode, any status. Ties break on the lower total response time in that best Game (an unanswered question counts
 * as its full time limit), then the earlier Game.
 */
@Service
public class DayLeaderboard {

	/** One row of the board. {@code rank} is 1-based and unique: the tie-break is total. */
	public record Entry(int rank, String name, int score) {
	}

	// ponytail: the whole board is ranked for every read — hundreds of rows for one day; index/paginate if it ever isn't.
	// The email is what the board is keyed on; it never leaves the server.
	private static final String BOARD = """
			WITH best AS (
			  SELECT DISTINCT ON (lower(p.email)) lower(p.email) AS email, p.name, p.score, g.created_at,
			    (SELECT sum(coalesce(a.response_ms, q.time_limit_sec * 1000))
			       FROM game_question gq
			       JOIN question q ON q.id = gq.question_id
			       LEFT JOIN answer a ON a.player_id = p.id AND a.question_id = gq.question_id
			      WHERE gq.game_id = g.id) AS total_ms
			  FROM player p
			  JOIN game g ON g.id = p.game_id
			  WHERE g.created_at >= (SELECT leaderboard_since FROM settings)
			  ORDER BY lower(p.email), p.score DESC, total_ms, g.created_at
			)
			SELECT email, name, score, row_number() OVER (ORDER BY score DESC, total_ms, created_at) AS rank FROM best
			""";

	private final JdbcTemplate jdbc;

	DayLeaderboard(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Entry> top(int n) {
		return jdbc.query(BOARD + "ORDER BY rank LIMIT ?",
				(rs, i) -> new Entry(rs.getInt("rank"), rs.getString("name"), rs.getInt("score")), n);
	}

	/** Rank of this email's best Game, or empty when it has no Game since the last Reset. */
	public Optional<Integer> rankOf(String email) {
		return jdbc.query("SELECT rank FROM (" + BOARD + ") b WHERE email = lower(?)", (rs, i) -> rs.getInt("rank"), email)
				.stream().findFirst();
	}
}
