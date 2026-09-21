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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.client.RestClient;

/**
 * Boundary test for the Day Leaderboard (#7): the public read, and its ranking rules. Finished Games are seeded as
 * rows (playing them over STOMP is SoloGameTest's job); the assertions are on what the HTTP client sees.
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
		jdbc.update("INSERT INTO player (id, game_id, name, email, consented_at, session_token, score) "
				+ "VALUES (?, ?, ?, ?, now(), ?, ?)", id, gameId, name, email, UUID.randomUUID(), score);
		return id;
	}

	void answer(UUID gameId, UUID playerId, long questionId, int responseMs) {
		jdbc.update("INSERT INTO answer (game_id, player_id, question_id, selected_option, correct, response_ms, points) "
				+ "VALUES (?, ?, ?, 1, true, ?, 0)", gameId, playerId, questionId, responseMs);
	}

	List<Map<String, Object>> board(int top) {
		return client.get().uri("/api/leaderboard?top=" + top).retrieve().body(ROWS);
	}

	/** Replay (spec test 6): the same email twice, listed once with the higher Score; the email itself is not exposed. */
	@Test
	void sameEmailInTwoGamesIsListedOnceWithItsBestScore() {
		player(game(Instant.now()), "Ada", "ada@example.com", 1200);
		player(game(Instant.now()), "Ada again", "Ada@Example.com", 1800);
		player(game(Instant.now()), "Bob", "bob@example.com", 1500);

		assertThat(board(10)).containsExactly(
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

		assertThat(board(10)).extracting("rank", "name").containsExactly(tuple(1, "Ada"), tuple(2, "Bob"));
	}

	@Test
	void gamesFromBeforeTheLastResetDoNotCount() {
		player(game(Instant.now().minusSeconds(60)), "Ada", "ada@example.com", 9000);
		player(game(Instant.now()), "Bob", "bob@example.com", 100);
		jdbc.update("UPDATE settings SET leaderboard_since = now() - interval '30 seconds'");

		assertThat(board(10)).extracting("name").containsExactly("Bob");
	}

	/** The rank is the email's, so a replay that scored worse still reports where its Player stands. */
	@Test
	void rankOfAGameIsItsPlayersEmailsRank() {
		player(game(Instant.now()), "Ada", "ada@example.com", 1800);
		var worseReplay = game(Instant.now());
		player(worseReplay, "Ada", "ada@example.com", 300);
		player(game(Instant.now()), "Bob", "bob@example.com", 1500);
		var carolsGame = game(Instant.now());
		player(carolsGame, "Carol", "carol@example.com", 1000);

		assertThat(client.get().uri("/api/leaderboard/rank?gameId=" + worseReplay).retrieve().body(Map.class))
				.containsEntry("rank", 1);
		assertThat(client.get().uri("/api/leaderboard/rank?gameId=" + carolsGame).retrieve().body(Map.class))
				.containsEntry("rank", 3);
	}

	// --- STOMP: the board is pushed when a Game finishes ---

	@Value("${admin.email}")
	String adminEmail;

	@Value("${admin.password}")
	String adminPassword;

	StompSession adminSession() throws Exception {
		var token = client.post().uri("/api/admin/login").body(Map.of("email", adminEmail, "password", adminPassword))
				.retrieve().body(Map.class).get("token");
		return Stomp.connect(port, Map.of("Authorization", "Bearer " + token), new Stomp.ErrorFrames());
	}

	/** Plays a one-question Solo Game for {@code email} to the end and returns its GAME_OVER payload. */
	@SuppressWarnings("unchecked")
	Map<String, Object> playSolo(String name, String email) throws Exception {
		var started = client.post().uri("/api/solo").body(Map.of("name", name, "email", email, "consent", true))
				.retrieve().body(Map.class);
		var gameId = (String) started.get("gameId");
		var session = Stomp.connectAsPlayer(port, (String) started.get("sessionToken"), new Stomp.ErrorFrames());
		var topic = Stomp.subscribe(session, "/topic/game/" + gameId);
		session.send("/app/game/" + gameId + "/ready", Map.of());
		assertThat(topic.poll(5, TimeUnit.SECONDS)).isNotNull().containsEntry("type", "QUESTION_START");
		session.send("/app/game/" + gameId + "/answer", Map.of("questionIndex", 0, "option", 1));
		var over = topic.poll(10, TimeUnit.SECONDS);
		assertThat(over).isNotNull().containsEntry("type", "GAME_OVER");
		session.disconnect();
		return (Map<String, Object>) over.get("payload");
	}

	@Test
	@SuppressWarnings("unchecked")
	void adminReceivesTheBoardWhenAGameFinishesAndTheGameOverCarriesTheRank() throws Exception {
		jdbc.update("UPDATE settings SET questions_per_game = 1");
		player(game(Instant.now()), "Bob", "bob@example.com", 9000);
		var admin = adminSession();
		var pushed = Stomp.subscribe(admin, "/topic/leaderboard");

		var over = playSolo("Ada", "ada@example.com");

		assertThat(over).containsEntry("rank", 2);
		var event = pushed.poll(5, TimeUnit.SECONDS);
		assertThat(event).isNotNull().containsEntry("type", "DAY_LEADERBOARD");
		var top = (List<Map<String, Object>>) ((Map<String, Object>) event.get("payload")).get("top");
		assertThat(top).extracting("rank", "name").containsExactly(tuple(1, "Bob"), tuple(2, "Ada"));
		assertThat((int) top.get(1).get("score")).isEqualTo((int) over.get("score"));
		admin.disconnect();
	}

	@Test
	void playerTokenCannotSubscribeToTheLeaderboardTopic() throws Exception {
		jdbc.update("UPDATE settings SET questions_per_game = 1");
		var started = client.post().uri("/api/solo").body(Map.of("name", "Ada", "email", "ada@example.com", "consent", true))
				.retrieve().body(Map.class);
		var errors = new Stomp.ErrorFrames();
		var session = Stomp.connectAsPlayer(port, (String) started.get("sessionToken"), errors);

		Stomp.subscribe(session, "/topic/leaderboard");

		assertThat(errors.error.get(5, TimeUnit.SECONDS)).contains("/topic/leaderboard");
	}

	/** The simple broker honours Ant wildcards, so a wildcard subscription must be refused like the literal one. */
	@Test
	void playerTokenCannotSubscribeToTheLeaderboardTopicByWildcard() throws Exception {
		jdbc.update("UPDATE settings SET questions_per_game = 1");
		var started = client.post().uri("/api/solo").body(Map.of("name", "Ada", "email", "ada@example.com", "consent", true))
				.retrieve().body(Map.class);
		var errors = new Stomp.ErrorFrames();
		var session = Stomp.connectAsPlayer(port, (String) started.get("sessionToken"), errors);

		Stomp.subscribe(session, "/topic/*");

		assertThat(errors.error.get(5, TimeUnit.SECONDS)).contains("/topic/*");
	}
}
