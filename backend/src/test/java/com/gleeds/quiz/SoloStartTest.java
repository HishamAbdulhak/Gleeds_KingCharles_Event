package com.gleeds.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Boundary test: Solo start over HTTP, then the first question over STOMP. Real Postgres. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SoloStartTest {

	static final int QUESTIONS_PER_GAME = 3;

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	RestClient client;

	@BeforeEach
	void setUp() {
		Fixtures.questionBank(jdbc, QUESTIONS_PER_GAME, 1, 15);
		client = RestClient.create("http://localhost:" + port);
	}

	static Map<String, Object> joinForm() {
		return new HashMap<>(Map.of("name", "Ada", "email", "ada@example.com", "consent", true));
	}

	@SuppressWarnings("unchecked")
	Map<String, String> startSolo() {
		return client.post().uri("/api/solo").body(joinForm()).retrieve().body(Map.class);
	}

	@Test
	void startCreatesGamePlayerAndQuestionSet() {
		var response = client.post().uri("/api/solo").body(joinForm()).retrieve().toEntity(Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		var body = response.getBody();
		var gameId = UUID.fromString((String) body.get("gameId"));
		var playerId = UUID.fromString((String) body.get("playerId"));
		assertThat((String) body.get("sessionToken")).isNotBlank();

		assertThat(jdbc.queryForMap("SELECT mode, pin, status FROM game WHERE id = ?", gameId))
				.containsEntry("mode", "SOLO").containsEntry("pin", null).containsEntry("status", "LOBBY");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM game_question WHERE game_id = ?", Integer.class, gameId))
				.isEqualTo(QUESTIONS_PER_GAME);
		assertThat(jdbc.queryForMap("SELECT game_id, name, email, score, streak FROM player WHERE id = ?", playerId))
				.containsEntry("game_id", gameId).containsEntry("name", "Ada").containsEntry("email", "ada@example.com")
				.containsEntry("score", 0).containsEntry("streak", 0);
		assertThat(jdbc.queryForObject("SELECT consented_at FROM player WHERE id = ?", Object.class, playerId)).isNotNull();
	}

	@Test
	void withoutConsentIs400() {
		var form = joinForm();
		form.put("consent", false);
		assertThatThrownBy(() -> client.post().uri("/api/solo").body(form).retrieve().toBodilessEntity())
				.isInstanceOfSatisfying(HttpClientErrorException.BadRequest.class,
						e -> assertThat(e.getResponseBodyAsString()).contains("consent"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM player", Integer.class)).isZero();
	}

	@Test
	void invalidEmailIs400() {
		var form = joinForm();
		form.put("email", "not-an-email");
		assertThatThrownBy(() -> client.post().uri("/api/solo").body(form).retrieve().toBodilessEntity())
				.isInstanceOfSatisfying(HttpClientErrorException.BadRequest.class,
						e -> assertThat(e.getResponseBodyAsString()).contains("email"));
	}

	@Test
	void fewerActiveQuestionsThanNeededIs409() {
		jdbc.update("UPDATE question SET active = false WHERE text = 'Q1'");
		var status = client.post().uri("/api/solo").body(joinForm()).exchange((req, res) -> res.getStatusCode());
		assertThat(status).isEqualTo(HttpStatus.CONFLICT);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM game", Integer.class)).isZero();
	}

	// --- STOMP: the first question ---

	@Test
	@SuppressWarnings("unchecked")
	void readyPlayerReceivesTheFirstQuestionWithoutTheCorrectOption() throws Exception {
		var started = startSolo();
		var session = Stomp.connectAsPlayer(port, started.get("sessionToken"));

		var events = Stomp.ready(session, started.get("gameId"));
		var event = events.poll(5, TimeUnit.SECONDS);

		assertThat(event).isNotNull().containsEntry("type", "QUESTION_START");
		var payload = (Map<String, Object>) event.get("payload");
		assertThat(payload).containsEntry("index", 0).containsEntry("timeLimitSec", 15)
				.doesNotContainKeys("correct", "correctOption");
		assertThat(Instant.parse((String) payload.get("startedAt"))).isBetween(Instant.now().minusSeconds(10), Instant.now());
		assertThat((String) payload.get("text")).isIn("Q1", "Q2", "Q3");
		assertThat((List<String>) payload.get("options")).containsExactly("a", "b", "c", "d");

		var gameId = UUID.fromString(started.get("gameId"));
		assertThat(jdbc.queryForMap("SELECT status, current_question_index FROM game WHERE id = ?", gameId))
				.containsEntry("status", "QUESTION").containsEntry("current_question_index", 0);
		assertThat(jdbc.queryForObject("SELECT question_started_at FROM game WHERE id = ?", Object.class, gameId)).isNotNull();
		session.disconnect();
	}

	@Test
	void playerCannotStartAnotherGameViaAnUppercaseId() throws Exception {
		var mine = startSolo();
		var theirs = startSolo();
		var errors = new Stomp.ErrorFrames();
		var session = Stomp.connectAsPlayer(port, mine.get("sessionToken"), errors);

		session.send("/app/game/" + theirs.get("gameId").toUpperCase() + "/ready", Map.of());

		assertThat(errors.error.get(5, TimeUnit.SECONDS)).isNotNull();
		assertThat(jdbc.queryForObject("SELECT status FROM game WHERE id = ?", String.class,
				UUID.fromString(theirs.get("gameId")))).isEqualTo("LOBBY");
	}

	@Test
	void adminJwtCanWatchAnyGame() throws Exception {
		var started = startSolo();
		var admin = Stomp.connectAsAdmin(port);
		var seenByAdmin = Stomp.subscribe(admin, "/topic/game/" + started.get("gameId"));

		var player = Stomp.connectAsPlayer(port, started.get("sessionToken"));
		Stomp.ready(player, started.get("gameId"));

		assertThat(seenByAdmin.poll(5, TimeUnit.SECONDS)).isNotNull().containsEntry("type", "QUESTION_START");
		admin.disconnect();
		player.disconnect();
	}

	@Test
	void adminReadyDoesNotStartTheGame() throws Exception {
		var started = startSolo();
		var admin = Stomp.connectAsAdmin(port);

		var events = Stomp.ready(admin, started.get("gameId"));

		assertThat(events.poll(1, TimeUnit.SECONDS)).isNull();
		assertThat(jdbc.queryForObject("SELECT status FROM game WHERE id = ?", String.class,
				UUID.fromString(started.get("gameId")))).isEqualTo("LOBBY");
		admin.disconnect();
	}

	@Test
	void bogusSessionTokenIsRefused() {
		assertThatThrownBy(() -> Stomp.connectAsPlayer(port, UUID.randomUUID().toString())).isInstanceOf(ExecutionException.class);
	}

	/** A failing database lookup during CONNECT must still answer with an ERROR frame (ExecutionException), not hang (TimeoutException). */
	@Test
	void databaseFailureDuringConnectIsRefusedNotSilent() {
		var started = startSolo();
		jdbc.execute("ALTER TABLE player RENAME TO player_gone");
		try {
			assertThatThrownBy(() -> Stomp.connectAsPlayer(port, started.get("sessionToken"))).isInstanceOf(ExecutionException.class);
		} finally {
			jdbc.execute("ALTER TABLE player_gone RENAME TO player");
		}
	}

	@Test
	void connectWithoutCredentialsIsRefused() {
		assertThatThrownBy(() -> Stomp.connect(port, Map.of())).isInstanceOf(ExecutionException.class);
	}

	@Test
	void playerCannotSubscribeToAnotherGamesTopic() throws Exception {
		var mine = startSolo();
		var theirs = startSolo();
		var handler = new Stomp.ErrorFrames();
		var session = Stomp.connectAsPlayer(port, mine.get("sessionToken"), handler);

		var events = Stomp.ready(session, theirs.get("gameId"));

		assertThat(handler.error.get(5, TimeUnit.SECONDS)).isNotNull();
		assertThat(events).isEmpty();
		assertThat(jdbc.queryForObject("SELECT status FROM game WHERE id = ?", String.class,
				UUID.fromString(theirs.get("gameId")))).isEqualTo("LOBBY");
	}
}
