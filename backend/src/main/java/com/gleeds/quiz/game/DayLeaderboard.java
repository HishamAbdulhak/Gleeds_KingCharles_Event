package com.gleeds.quiz.game;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Day Leaderboard (spec → Day Leaderboard): every email's best Score over the Games created since the last Reset,
 * any Mode, any status. Ties break on the lower total response time in that best Game (an unanswered question counts
 * as its full time limit), then the earlier Game. Also the public read of it: the Host idle screen's first paint
 * (updates arrive on the topic); and the Admin's Reset.
 */
@RestController
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

	private static final RowMapper<Entry> ENTRY = (rs, i) -> new Entry(rs.getInt("rank"), rs.getString("name"),
			rs.getInt("score"));

	private final JdbcTemplate jdbc;
	private final SimpMessagingTemplate messaging;

	DayLeaderboard(JdbcTemplate jdbc, SimpMessagingTemplate messaging) {
		this.jdbc = jdbc;
		this.messaging = messaging;
	}

	/** The top ten: what the Host screen shows, legible from 5 m. */
	@GetMapping("/api/leaderboard")
	public List<Entry> top() {
		return jdbc.query(BOARD + "ORDER BY rank LIMIT 10", ENTRY);
	}

	/** This email's row: its best Score today and that Game's rank; empty when it has no Game since the last Reset. */
	public Optional<Entry> bestOf(String email) {
		return jdbc.query("SELECT * FROM (" + BOARD + ") b WHERE email = lower(?)", ENTRY, email).stream().findFirst();
	}

	/**
	 * Reset (spec stories 43–44): Games created from now on are the only ones that count. Nothing is deleted, so a
	 * mis-click loses no Game. Admin-only via SecurityConfig's /api/admin/** rule.
	 */
	@PostMapping("/api/admin/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void reset() {
		jdbc.update("UPDATE settings SET leaderboard_since = now()");
		GameEngine.afterCommit(() -> push(List.of()));
	}

	/** DAY_LEADERBOARD on the leaderboard topic: the Host idle screen's update. Call it after commit. */
	void push(List<Entry> top) {
		messaging.convertAndSend("/topic/leaderboard", new GameEvent("DAY_LEADERBOARD", new GameEvent.Board(top)));
	}
}
