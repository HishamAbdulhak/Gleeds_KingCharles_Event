package com.gleeds.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Boundary test for the Day Leaderboard (#7) and its Reset (#10): the public read, and its ranking rules. Finished
 * Games are seeded as rows (playing them over STOMP is SoloGameTest's job); the assertions are on what the HTTP client
 * sees.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class DayLeaderboardTest {

	static final int TIME_LIMIT_SEC = 20;
	static final ParameterizedTypeReference<List<Map<String, Object>>> ROWS = new ParameterizedTypeReference<>() {
	};

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	RestClient client;
	List<Long> questionIds;

	@BeforeEach
	void setUp() {
		jdbc.execute("TRUNCATE game, question CASCADE");
		jdbc.update("UPDATE settings SET leaderboard_since = 'epoch'");
		questionIds = List.of(question("Q1"), question("Q2"));
		client = RestClient.create("http://localhost:" + port);
	}

	long question(String text) {
		return jdbc.queryForObject("INSERT INTO question (text, option_a, option_b, option_c, option_d, correct_option, "
				+ "time_limit_sec) VALUES (?, 'a', 'b', 'c', 'd', 1, ?) RETURNING id", Long.class, text, TIME_LIMIT_SEC);
	}

	/** A finished Solo Game whose Question Set is both questions. */
	UUID game(Instant createdAt) {
		var id = UUID.randomUUID();
		jdbc.update("INSERT INTO game (id, mode, status, created_at) VALUES (?, 'SOLO', 'FINISHED', ?)", id,
				java.sql.Timestamp.from(createdAt));
		for (int i = 0; i < questionIds.size(); i++) {
			jdbc.update("INSERT INTO game_question (game_id, question_id, position) VALUES (?, ?, ?)", id, questionIds.get(i), i);
		}
		return id;
	}

	UUID player(UUID gameId, String name, String email, int score) {
		var id = UUID.randomUUID();
		jdbc.update("INSERT INTO player (id, game_id, name, email, session_token, score) "
				+ "VALUES (?, ?, ?, ?, ?, ?)", id, gameId, name, email, UUID.randomUUID(), score);
		return id;
	}

	void answer(UUID gameId, UUID playerId, long questionId, int responseMs) {
		jdbc.update("INSERT INTO answer (game_id, player_id, question_id, selected_option, correct, response_ms, points) "
				+ "VALUES (?, ?, ?, 1, true, ?, 0)", gameId, playerId, questionId, responseMs);
	}

	List<Map<String, Object>> board() {
		return client.get().uri("/api/leaderboard").retrieve().body(ROWS);
	}

	/** Replay (spec test 6): the same email twice, listed once with the higher Score; the email itself is not exposed. */
	@Test
	void sameEmailInTwoGamesIsListedOnceWithItsBestScore() {
		player(game(Instant.now()), "Ada", "ada@example.com", 1200);
		player(game(Instant.now()), "Ada again", "Ada@Example.com", 1800);
		player(game(Instant.now()), "Bob", "bob@example.com", 1500);

		assertThat(board()).containsExactly(
				Map.of("rank", 1, "name", "Ada again", "score", 1800),
				Map.of("rank", 2, "name", "Bob", "score", 1500));
	}

	/**
	 * Spec story 48. Bob played first and was faster on the one question he answered, but his unanswered one counts
	 * as all 20 s; only the total response time puts Ada ahead.
	 */
	@Test
	void equalScoresOrderByLowerTotalResponseTimeWithUnansweredCountingAsTheFullLimit() {
		var bobsGame = game(Instant.now().minusSeconds(60));
		var bob = player(bobsGame, "Bob", "bob@example.com", 1500);
		answer(bobsGame, bob, questionIds.get(0), 500);   // + 20 000 ms for the question he let time out
		var adasGame = game(Instant.now());
		var ada = player(adasGame, "Ada", "ada@example.com", 1500);
		answer(adasGame, ada, questionIds.get(0), 3000);
		answer(adasGame, ada, questionIds.get(1), 3000);   // 6 000 ms in total

		assertThat(board()).extracting("rank", "name").containsExactly(tuple(1, "Ada"), tuple(2, "Bob"));
	}

	@Test
	void gamesFromBeforeTheLastResetDoNotCount() {
		player(game(Instant.now().minusSeconds(60)), "Ada", "ada@example.com", 9000);
		player(game(Instant.now()), "Bob", "bob@example.com", 100);
		jdbc.update("UPDATE settings SET leaderboard_since = now() - interval '30 seconds'");

		assertThat(board()).extracting("name").containsExactly("Bob");
	}

	// --- STOMP: the board is pushed when a Game finishes ---

	/** How a Solo Game ended for its phone: GAME_OVER on the Game topic, then BEST_SCORE on the personal queue. */
	record Ending(Map<String, Object> gameOver, Map<String, Object> bestScore) {
	}

	/** Plays a one-question Solo Game for {@code email} to the end, answering right. */
	@SuppressWarnings("unchecked")
	Ending playSolo(String name, String email) throws Exception {
		var started = client.post().uri("/api/solo").body(Map.of("name", name, "email", email)).retrieve().body(Map.class);
		var gameId = (String) started.get("gameId");
		var session = Stomp.connectAsPlayer(port, (String) started.get("sessionToken"));
		var queue = Stomp.subscribe(session, "/user/queue/player");
		var topic = Stomp.ready(session, gameId);
		Stomp.next(topic, "QUESTION_START");
		session.send("/app/game/" + gameId + "/answer", Map.of("questionIndex", 0, "option", 1));
		var over = Stomp.next(topic, "GAME_OVER");
		Stomp.next(queue, "ANSWER_ACK");
		Stomp.next(queue, "RESULT");
		var best = Stomp.next(queue, "BEST_SCORE");
		session.disconnect();
		return new Ending(over, best);
	}

	/** docs/adr/0003: the phone's proof is the email's best Score today, so a worse Replay still shows the earlier one. */
	@Test
	void aReplayThatScoresLowerStillReportsTheEarlierBestUnderTheNewName() throws Exception {
		jdbc.update("UPDATE settings SET questions_per_game = 1");
		player(game(Instant.now().minusSeconds(60)), "Ada", "ada@example.com", 9000);   // more than one question can earn

		var ending = playSolo("Ada Lovelace", "ADA@example.com");

		assertThat((int) ending.gameOver().get("score")).isBetween(1, 1000);
		assertThat(ending.bestScore()).isEqualTo(Map.of("name", "Ada Lovelace", "score", 9000));
	}

	/** Bob played earlier with a lower Score, so only the Score (never Game order) can put Ada first. */
	@Test
	@SuppressWarnings("unchecked")
	void adminReceivesTheBoardWhenAGameFinishesAndTheGameOverCarriesTheRank() throws Exception {
		jdbc.update("UPDATE settings SET questions_per_game = 1");
		player(game(Instant.now().minusSeconds(60)), "Bob", "bob@example.com", 100);
		var admin = Stomp.connectAsAdmin(port);
		var pushed = Stomp.subscribe(admin, "/topic/leaderboard");

		var over = playSolo("Ada", "ada@example.com").gameOver();

		assertThat(over).containsEntry("rank", 1);
		var event = pushed.poll(5, TimeUnit.SECONDS);
		assertThat(event).isNotNull().containsEntry("type", "DAY_LEADERBOARD");
		var top = (List<Map<String, Object>>) ((Map<String, Object>) event.get("payload")).get("top");
		assertThat(top).extracting("rank", "name").containsExactly(tuple(1, "Ada"), tuple(2, "Bob"));
		assertThat((int) top.get(0).get("score")).isEqualTo((int) over.get("score"));
		admin.disconnect();
	}

	/**
	 * Spec test 7 and stories 43–44. Ada's Game doubles as the barrier: its board proves the Admin's SUBSCRIBE landed
	 * before the Reset is sent on another connection.
	 */
	@Test
	void resetEmptiesTheBoardAndPushesItEmptyButKeepsEveryGame() throws Exception {
		jdbc.update("UPDATE settings SET questions_per_game = 1");
		var bobsGame = game(Instant.now().minusSeconds(60));
		player(bobsGame, "Bob", "bob@example.com", 100);
		var admin = Stomp.connectAsAdmin(port);
		var pushed = Stomp.subscribe(admin, "/topic/leaderboard");
		playSolo("Ada", "ada@example.com");
		assertThat((List<?>) Stomp.next(pushed, "DAY_LEADERBOARD").get("top")).hasSize(2);

		var reset = client.post().uri("/api/admin/reset").header("Authorization", "Bearer " + Fixtures.adminToken(port))
				.retrieve().toBodilessEntity();

		assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(board()).isEmpty();
		assertThat(Stomp.next(pushed, "DAY_LEADERBOARD")).isEqualTo(Map.of("top", List.of()));

		playSolo("Cleo", "cleo@example.com");

		assertThat(board()).extracting("rank", "name").containsExactly(tuple(1, "Cleo"));
		assertThat(jdbc.queryForList("SELECT p.name FROM player p JOIN game g ON g.id = p.game_id ORDER BY g.created_at",
				String.class)).containsExactly("Bob", "Ada", "Cleo");
		admin.disconnect();
	}

	@Test
	void resetWithoutATokenIs401AndLeavesTheBoard() {
		player(game(Instant.now()), "Ada", "ada@example.com", 1200);

		var status = client.post().uri("/api/admin/reset").exchange((req, res) -> res.getStatusCode());

		assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(board()).extracting("name").containsExactly("Ada");
	}

	@Test
	void playerTokenCannotSubscribeToTheLeaderboardTopic() throws Exception {
		jdbc.update("UPDATE settings SET questions_per_game = 1");
		var started = client.post().uri("/api/solo").body(Map.of("name", "Ada", "email", "ada@example.com"))
				.retrieve().body(Map.class);
		var errors = new Stomp.ErrorFrames();
		var session = Stomp.connectAsPlayer(port, (String) started.get("sessionToken"), errors);

		Stomp.subscribe(session, "/topic/leaderboard");

		assertThat(errors.error.get(5, TimeUnit.SECONDS)).contains("/topic/leaderboard");
	}

	@Test
	void playerTokenCannotSubscribeToTheLeaderboardTopicByWildcard() throws Exception {
		jdbc.update("UPDATE settings SET questions_per_game = 1");
		var started = client.post().uri("/api/solo").body(Map.of("name", "Ada", "email", "ada@example.com"))
				.retrieve().body(Map.class);
		var errors = new Stomp.ErrorFrames();
		var session = Stomp.connectAsPlayer(port, (String) started.get("sessionToken"), errors);

		Stomp.subscribe(session, "/topic/*");

		assertThat(errors.error.get(5, TimeUnit.SECONDS)).contains("/topic/*");
	}
}
