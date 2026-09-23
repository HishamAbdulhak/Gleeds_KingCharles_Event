package com.gleeds.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
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

/**
 * Boundary test for the Battle lobby (#8): create by an Admin, join by PIN, LOBBY_UPDATE on the Game topic, the
 * 2–4 Player guards. Real Postgres; nothing reaches into the engine.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class BattleLobbyTest {

	static final int QUESTIONS_PER_GAME = 3;

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	RestClient client;
	String adminToken;

	@BeforeEach
	void setUp() {
		Fixtures.questionBank(jdbc, QUESTIONS_PER_GAME, 1, 15);
		client = RestClient.create("http://localhost:" + port);
		adminToken = Fixtures.adminToken(port);
	}

	static Map<String, Object> joinForm(String name, String email) {
		return Map.of("name", name, "email", email);
	}

	@SuppressWarnings("unchecked")
	Map<String, String> createBattle() {
		return client.post().uri("/api/games").header("Authorization", "Bearer " + adminToken)
				.body(Map.of("mode", "BATTLE")).retrieve().body(Map.class);
	}

	@SuppressWarnings("unchecked")
	Map<String, String> join(String pin, String name, String email) {
		return client.post().uri("/api/games/" + pin + "/join").body(joinForm(name, email)).retrieve().body(Map.class);
	}

	/** The Host screen's view of the Game topic: every LOBBY_UPDATE, in order. */
	BlockingQueue<Map<String, Object>> watchAsAdmin(String gameId) throws Exception {
		return Stomp.subscribe(Stomp.connectAsAdmin(port), "/topic/game/" + gameId);
	}

	@SuppressWarnings("unchecked")
	static List<String> lobbyNames(Map<String, Object> event) {
		assertThat(event).isNotNull().containsEntry("type", "LOBBY_UPDATE");
		var players = (List<Map<String, Object>>) ((Map<String, Object>) event.get("payload")).get("players");
		assertThat(players).allSatisfy(p -> assertThat(p).containsKeys("id", "name"));
		return players.stream().map(p -> (String) p.get("name")).toList();
	}

	// --- create ---

	@Test
	void adminCreatesABattleInLobbyWithAPinAndAQuestionSet() {
		var response = client.post().uri("/api/games").header("Authorization", "Bearer " + adminToken)
				.body(Map.of("mode", "BATTLE")).retrieve().toEntity(Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		var gameId = UUID.fromString((String) response.getBody().get("gameId"));
		var pin = (String) response.getBody().get("pin");
		assertThat(pin).matches("[0-9]{6}");
		assertThat(jdbc.queryForMap("SELECT mode, pin, status FROM game WHERE id = ?", gameId))
				.containsEntry("mode", "BATTLE").containsEntry("pin", pin).containsEntry("status", "LOBBY");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM game_question WHERE game_id = ?", Integer.class, gameId))
				.isEqualTo(QUESTIONS_PER_GAME);
	}

	@Test
	void createWithoutAdminTokenIs401() {
		var status = client.post().uri("/api/games").body(Map.of("mode", "BATTLE")).exchange((req, res) -> res.getStatusCode());
		assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM game", Integer.class)).isZero();
	}

	// --- join ---

	@Test
	void joinByPinSeatsThePlayerAndEveryJoinPublishesTheLobby() throws Exception {
		var created = createBattle();
		var topic = watchAsAdmin(created.get("gameId"));

		var response = client.post().uri("/api/games/" + created.get("pin") + "/join").body(joinForm("Ada", "ada@example.com"))
				.retrieve().toEntity(Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		var seat = response.getBody();
		assertThat(seat).containsEntry("gameId", created.get("gameId"));
		var playerId = UUID.fromString((String) seat.get("playerId"));
		assertThat((String) seat.get("sessionToken")).isNotBlank();
		assertThat(jdbc.queryForMap("SELECT game_id, name, email, score FROM player WHERE id = ?", playerId))
				.containsEntry("game_id", UUID.fromString(created.get("gameId"))).containsEntry("name", "Ada")
				.containsEntry("email", "ada@example.com").containsEntry("score", 0);
		assertThat(lobbyNames(topic.poll(5, TimeUnit.SECONDS))).containsExactly("Ada");

		join(created.get("pin"), "Bob", "bob@example.com");

		assertThat(lobbyNames(topic.poll(5, TimeUnit.SECONDS))).containsExactly("Ada", "Bob");
	}

	@Test
	void unknownPinIs404() {
		var status = client.post().uri("/api/games/000000/join").body(joinForm("Ada", "ada@example.com"))
				.exchange((req, res) -> res.getStatusCode());
		assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM player", Integer.class)).isZero();
	}

	/** Four Players in the lobby: the cap (spec story 29). Returns Ada's Seat, the first. */
	Map<String, String> fillLobby(Map<String, String> created) {
		var first = join(created.get("pin"), "Ada", "ada@example.com");
		for (var name : List.of("Bob", "Cy", "Di")) {
			join(created.get("pin"), name, name.toLowerCase() + "@example.com");
		}
		return first;
	}

	@Test
	void fifthJoinIsRefusedAsLobbyFull() throws Exception {
		var created = createBattle();
		fillLobby(created);
		var topic = watchAsAdmin(created.get("gameId"));

		assertThatThrownBy(() -> join(created.get("pin"), "Eve", "eve@example.com"))
				.isInstanceOfSatisfying(HttpClientErrorException.Conflict.class,
						e -> assertThat(e.getResponseBodyAsString()).contains("LOBBY_FULL"));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM player", Integer.class)).isEqualTo(4);
		assertThat(topic.poll(1, TimeUnit.SECONDS)).as("no LOBBY_UPDATE for a refused join").isNull();
	}

	/** Spec story 22: a refresh re-joins with the same email and must land on the same Seat, even in a full lobby. */
	@Test
	void sameEmailRejoinsItsSeatInsteadOfBeingRefused() throws Exception {
		var created = createBattle();
		var first = fillLobby(created);
		var topic = watchAsAdmin(created.get("gameId"));

		var response = client.post().uri("/api/games/" + created.get("pin") + "/join")
				.body(joinForm("Ada again", "ADA@example.com")).retrieve().toEntity(Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("playerId", first.get("playerId"))
				.containsEntry("sessionToken", first.get("sessionToken"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM player", Integer.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("SELECT name FROM player WHERE id = ?", String.class,
				UUID.fromString(first.get("playerId")))).as("the Seat keeps its first name").isEqualTo("Ada");
		assertThat(topic.poll(1, TimeUnit.SECONDS)).as("nothing changed, so no LOBBY_UPDATE").isNull();
	}

	@Test
	void joinAfterTheBattleLeftTheLobbyIsRefusedAsGameStarted() {
		var created = createBattle();
		join(created.get("pin"), "Ada", "ada@example.com");
		jdbc.update("UPDATE game SET status = 'QUESTION' WHERE id = ?", UUID.fromString(created.get("gameId")));

		assertThatThrownBy(() -> join(created.get("pin"), "Bob", "bob@example.com"))
				.isInstanceOfSatisfying(HttpClientErrorException.Conflict.class,
						e -> assertThat(e.getResponseBodyAsString()).contains("GAME_STARTED"));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM player", Integer.class)).isEqualTo(1);
	}

	// --- start ---

	HttpStatus start(String gameId, String token) {
		var request = client.post().uri("/api/games/" + gameId + "/start");
		if (token != null) {
			request = request.header("Authorization", "Bearer " + token);
		}
		return (HttpStatus) request.exchange((req, res) -> res.getStatusCode());
	}

	/** Spec story 28: no Battle with one person. The transition to the first question is ticket 09's. */
	@Test
	void startNeedsTwoPlayers() {
		var created = createBattle();
		join(created.get("pin"), "Ada", "ada@example.com");

		assertThat(start(created.get("gameId"), adminToken)).isEqualTo(HttpStatus.CONFLICT);

		join(created.get("pin"), "Bob", "bob@example.com");

		assertThat(start(created.get("gameId"), adminToken)).isEqualTo(HttpStatus.ACCEPTED);
	}

	@Test
	void startWithoutAdminTokenIs401() {
		var created = createBattle();
		fillLobby(created);
		assertThat(start(created.get("gameId"), null)).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void startOfAnUnknownGameIs404() {
		assertThat(start(UUID.randomUUID().toString(), adminToken)).isEqualTo(HttpStatus.NOT_FOUND);
	}

	// --- ready: a late or refreshed screen gets the lobby (docs/adr/0001: reconnect reuses the message) ---

	@Test
	@SuppressWarnings("unchecked")
	void aPlayerWhoSaysReadyInTheLobbyGetsThePlayersInJoinOrderWithThePin() throws Exception {
		var created = createBattle();
		join(created.get("pin"), "Ada", "ada@example.com");
		var bob = join(created.get("pin"), "Bob", "bob@example.com");
		// join order is joined_at, not insertion order: make the two disagree, so the sort key is what passes this
		jdbc.update("UPDATE player SET joined_at = joined_at - interval '1 minute' WHERE id = ?", UUID.fromString(bob.get("playerId")));
		var session = Stomp.connectAsPlayer(port, bob.get("sessionToken"));

		var topic = Stomp.ready(session, created.get("gameId"));

		var event = topic.poll(5, TimeUnit.SECONDS);
		assertThat(lobbyNames(event)).containsExactly("Bob", "Ada");
		assertThat((Map<String, Object>) event.get("payload")).containsEntry("pin", created.get("pin"));
		assertThat(jdbc.queryForObject("SELECT status FROM game WHERE id = ?", String.class,
				UUID.fromString(created.get("gameId")))).as("ready never starts a Battle").isEqualTo("LOBBY");
		session.disconnect();
	}

	@Test
	void aRefreshedHostSaysReadyAndGetsTheLobby() throws Exception {
		var created = createBattle();
		join(created.get("pin"), "Ada", "ada@example.com");
		var admin = Stomp.connectAsAdmin(port);

		var topic = Stomp.ready(admin, created.get("gameId"));

		assertThat(lobbyNames(topic.poll(5, TimeUnit.SECONDS))).containsExactly("Ada");
		admin.disconnect();
	}
}
