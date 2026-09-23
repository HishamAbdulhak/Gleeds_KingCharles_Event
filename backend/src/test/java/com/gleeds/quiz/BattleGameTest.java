package com.gleeds.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.client.RestClient;

/**
 * Boundary test for the Battle game loop (#9): the Host's commands drive question → reveal → leaderboard → Podium,
 * with two phones and the big screen on the same events. Real Postgres; nothing reaches into the engine.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class BattleGameTest {

	static final int QUESTIONS_PER_GAME = 3;
	static final int TIME_LIMIT_SEC = 5;
	static final int CORRECT = 1;
	static final int WRONG = 2;

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	RestClient client;
	String adminToken;
	final List<StompSession> sessions = new ArrayList<>();

	/** One Player's phone: their Seat, the Game topic and their personal queue. */
	record Phone(UUID gameId, UUID playerId, String name, String sessionToken, StompSession session,
			BlockingQueue<Map<String, Object>> topic, BlockingQueue<Map<String, Object>> queue) {

		void answer(int questionIndex, int option) {
			session.send("/app/game/" + gameId + "/answer", Map.of("questionIndex", questionIndex, "option", option));
		}
	}

	@BeforeEach
	void setUp() {
		Fixtures.questionBank(jdbc, QUESTIONS_PER_GAME, CORRECT, TIME_LIMIT_SEC);
		client = RestClient.create("http://localhost:" + port);
		adminToken = Fixtures.adminToken(port);
	}

	@AfterEach
	void disconnect() {
		sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
		sessions.clear();
	}

	// --- driving the Battle the way the three surfaces do ---

	@SuppressWarnings("unchecked")
	UUID createBattle() {
		var created = client.post().uri("/api/games").header("Authorization", "Bearer " + adminToken).retrieve()
				.body(Map.class);
		return UUID.fromString((String) created.get("gameId"));
	}

	/** A Host command; returns the Game's new status. */
	@SuppressWarnings("unchecked")
	String command(UUID gameId, String command) {
		var response = client.post().uri("/api/games/" + gameId + "/" + command)
				.header("Authorization", "Bearer " + adminToken).retrieve().body(Map.class);
		return (String) response.get("status");
	}

	/** Joins by PIN (REST) and then connects the phone, exactly as the join page does. */
	@SuppressWarnings("unchecked")
	Phone join(UUID gameId, String name) throws Exception {
		var pin = jdbc.queryForObject("SELECT pin FROM game WHERE id = ?", String.class, gameId);
		var seat = client.post().uri("/api/games/" + pin + "/join")
				.body(Map.of("name", name, "email", name.toLowerCase() + "@example.com", "consent", true)).retrieve()
				.body(Map.class);
		var session = Stomp.connectAsPlayer(port, (String) seat.get("sessionToken"));
		sessions.add(session);
		var queue = Stomp.subscribe(session, "/user/queue/player");
		var topic = Stomp.ready(session, gameId.toString());
		Stomp.next(topic, "LOBBY_UPDATE");   // this phone's own lobby: proof the subscription is live before the Host starts
		return new Phone(gameId, UUID.fromString((String) seat.get("playerId")), name,
				(String) seat.get("sessionToken"), session, topic, queue);
	}

	/**
	 * The phone's first event of the Battle itself: a phone that joined early also sees every LOBBY_UPDATE the joins
	 * and readies after it published, and how many that is, is not what this test is about.
	 */
	@SuppressWarnings("unchecked")
	Map<String, Object> afterLobby(Phone phone, String type) throws InterruptedException {
		Map<String, Object> event;
		do {
			event = phone.topic().poll(10, TimeUnit.SECONDS);
			assertThat(event).as("expected %s on %s's phone", type, phone.name()).isNotNull();
		} while ("LOBBY_UPDATE".equals(event.get("type")));
		assertThat(event).as("%s's phone", phone.name()).containsEntry("type", type);
		return (Map<String, Object>) event.get("payload");
	}

	/** The big screen: the Game topic, the Host-only topic and the day's board. */
	record BigScreen(BlockingQueue<Map<String, Object>> topic, BlockingQueue<Map<String, Object>> host,
			BlockingQueue<Map<String, Object>> board) {
	}

	/** The Host screen's three subscriptions, then a ready — whose lobby is proof that all three are live. */
	BigScreen bigScreen(UUID gameId) throws Exception {
		var admin = Stomp.connectAsAdmin(port);
		sessions.add(admin);
		var host = Stomp.subscribe(admin, "/topic/game/" + gameId + "/host");
		var board = Stomp.subscribe(admin, "/topic/leaderboard");
		var topic = Stomp.ready(admin, gameId.toString());
		Stomp.next(topic, "LOBBY_UPDATE");
		return new BigScreen(topic, host, board);
	}

	/** Starts the Battle and takes the first question off every phone. */
	void start(UUID gameId, Phone... phones) throws Exception {
		assertThat(command(gameId, "start")).isEqualTo("QUESTION");
		for (var phone : phones) {
			assertThat(afterLobby(phone, "QUESTION_START")).containsEntry("index", 0);
		}
	}

	@Test
	void startPutsTheFirstQuestionOnEveryPhoneAndAnswersWithTheNewStatus() throws Exception {
		var gameId = createBattle();
		var ada = join(gameId, "Ada");
		var bob = join(gameId, "Bob");

		var status = command(gameId, "start");

		assertThat(status).isEqualTo("QUESTION");
		for (var phone : List.of(ada, bob)) {
			assertThat(afterLobby(phone, "QUESTION_START")).containsEntry("index", 0)
					.containsEntry("total", QUESTIONS_PER_GAME).containsKeys("text", "options", "timeLimitSec");
		}
		assertThat(jdbc.queryForMap("SELECT status, current_question_index FROM game WHERE id = ?", gameId))
				.containsEntry("status", "QUESTION").containsEntry("current_question_index", 0);
	}

	/** Ticket #9: the count climbs with every Answer, and the last Answer ends the question without waiting for the timer. */
	@Test
	void everyAnswerCountsUpAndTheLastOneEndsTheQuestionWithTheReveal() throws Exception {
		var gameId = createBattle();
		var ada = join(gameId, "Ada");
		var bob = join(gameId, "Bob");
		var screen = bigScreen(gameId);
		start(gameId, ada, bob);
		assertThat(answered(Stomp.next(screen.host(), "HOST_STATE"))).as("a fresh question: nobody in yet").isZero();

		ada.answer(0, CORRECT);

		assertThat(Stomp.next(ada.queue(), "ANSWER_ACK")).containsEntry("accepted", true);
		assertThat(answered(Stomp.next(screen.host(), "HOST_STATE"))).isEqualTo(1);
		assertThat(ada.queue().poll(500, TimeUnit.MILLISECONDS))
				.as("the RESULT waits for the reveal, so one phone can't show the group the answer").isNull();

		bob.answer(0, WRONG);

		assertThat(Stomp.next(bob.queue(), "ANSWER_ACK")).containsEntry("accepted", true);
		assertThat(answered(Stomp.next(screen.host(), "HOST_STATE"))).isEqualTo(2);
		var result = Stomp.next(ada.queue(), "RESULT");
		assertThat(result).containsEntry("correct", true).containsEntry("streak", 1).containsEntry("correctOption", CORRECT);
		assertThat((int) result.get("points")).isBetween(500, 1000);
		assertThat(Stomp.next(bob.queue(), "RESULT")).containsEntry("correct", false).containsEntry("points", 0)
				.containsEntry("streak", 0);
		assertThat(Stomp.next(ada.topic(), "REVEAL")).containsEntry("correctOption", CORRECT)
				.containsEntry("counts", List.of(0, 1, 1, 0));   // one for CORRECT (1), one for WRONG (2)
		assertThat(jdbc.queryForObject("SELECT status FROM game WHERE id = ?", String.class, gameId)).isEqualTo("REVEAL");
	}

	/** Ada answers right and Bob wrong, which ends the question; returns the REVEAL both phones saw. */
	Map<String, Object> bothAnswer(Phone ada, Phone bob, int index) throws Exception {
		ada.answer(index, CORRECT);
		Stomp.next(ada.queue(), "ANSWER_ACK");   // before Bob answers, so Bob's is the one that closes the question
		bob.answer(index, WRONG);
		Stomp.next(bob.queue(), "ANSWER_ACK");
		for (var phone : List.of(ada, bob)) {
			Stomp.next(phone.queue(), "RESULT");
		}
		Stomp.next(bob.topic(), "REVEAL");
		return Stomp.next(ada.topic(), "REVEAL");
	}

	@SuppressWarnings("unchecked")
	static List<Map<String, Object>> rows(Map<String, Object> payload, String key) {
		return (List<Map<String, Object>>) payload.get(key);
	}

	/** How many of the roster are in on the open question, as HOST_STATE reports them. */
	static long answered(Map<String, Object> hostState) {
		return rows(hostState, "players").stream().filter(row -> Boolean.TRUE.equals(row.get("answered"))).count();
	}

	/** Ticket #9's boundary test: two Players, three questions, the Host driving every step. */
	@Test
	void theHostWalksTheBattleFromTheFirstQuestionToThePodiumAndTheDayLeaderboard() throws Exception {
		var gameId = createBattle();
		var ada = join(gameId, "Ada");
		var bob = join(gameId, "Bob");
		var screen = bigScreen(gameId);
		start(gameId, ada, bob);

		for (int i = 0; i < QUESTIONS_PER_GAME; i++) {
			if (i > 0) {
				assertThat(command(gameId, "next")).as("the leaderboard's Next starts the question").isEqualTo("QUESTION");
				for (var phone : List.of(ada, bob)) {
					assertThat(Stomp.next(phone.topic(), "QUESTION_START")).containsEntry("index", i);
				}
			}
			var reveal = bothAnswer(ada, bob, i);
			assertThat(reveal).containsEntry("correctOption", CORRECT).containsEntry("counts", List.of(0, 1, 1, 0));

			assertThat(command(gameId, "next")).as("the reveal's Next shows the leaderboard").isEqualTo("LEADERBOARD");

			var standings = rows(Stomp.next(ada.topic(), "LEADERBOARD"), "players");
			assertThat(standings).hasSize(2);
			assertThat(standings.get(0)).containsEntry("name", "Ada").containsEntry("playerId", ada.playerId().toString());
			assertThat((int) standings.get(0).get("delta")).as("what question %d earned Ada", i).isPositive();
			assertThat(standings.get(1)).containsEntry("name", "Bob").containsEntry("score", 0).containsEntry("delta", 0);
			Stomp.next(bob.topic(), "LEADERBOARD");
		}

		assertThat(command(gameId, "next")).as("nothing left to play").isEqualTo("FINISHED");

		var podium = rows(Stomp.next(ada.topic(), "GAME_OVER"), "podium");
		assertThat(podium).hasSize(2);
		assertThat(podium.get(0)).containsEntry("name", "Ada");
		assertThat((int) podium.get(0).get("score")).isPositive();
		assertThat(podium.get(1)).containsEntry("name", "Bob").containsEntry("score", 0);
		assertThat(rows(Stomp.next(screen.board(), "DAY_LEADERBOARD"), "top")).as("the Battle's Scores are on the day's board")
				.extracting(row -> row.get("name")).containsExactly("Ada", "Bob");
		assertThat(jdbc.queryForObject("SELECT status FROM game WHERE id = ?", String.class, gameId)).isEqualTo("FINISHED");
		assertThat(jdbc.queryForObject("SELECT ended_at FROM game WHERE id = ?", Object.class, gameId)).isNotNull();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM answer WHERE game_id = ?", Integer.class, gameId))
				.isEqualTo(2 * QUESTIONS_PER_GAME);
	}

	/** Spec story 36 / CODING_STANDARDS: a new topic ships with the wrong principal being refused. */
	@Test
	void aPlayerIsRefusedTheHostTopic() throws Exception {
		var gameId = createBattle();
		var ada = join(gameId, "Ada");
		var errors = new Stomp.ErrorFrames();
		var session = Stomp.connectAsPlayer(port, ada.sessionToken(), errors);
		sessions.add(session);

		Stomp.subscribe(session, "/topic/game/" + gameId + "/host");

		assertThat(errors.error.get(5, TimeUnit.SECONDS)).contains("Not allowed", "/topic/game/" + gameId + "/host");
	}

	/** Ticket #9: the Host can end a question early, and an Answer that arrives after the reveal is refused. */
	@Test
	void theHostsRevealEndsTheQuestionAndALaterAnswerIsRefused() throws Exception {
		var gameId = createBattle();
		var ada = join(gameId, "Ada");
		var bob = join(gameId, "Bob");
		start(gameId, ada, bob);
		ada.answer(0, CORRECT);
		Stomp.next(ada.queue(), "ANSWER_ACK");

		assertThat(command(gameId, "reveal")).isEqualTo("REVEAL");

		assertThat(Stomp.next(ada.queue(), "RESULT")).containsEntry("correct", true);
		assertThat(Stomp.next(bob.queue(), "RESULT")).as("no Answer from Bob: a timeout").containsEntry("correct", false)
				.containsEntry("points", 0).containsEntry("streak", 0);
		assertThat(Stomp.next(ada.topic(), "REVEAL")).containsEntry("counts", List.of(0, 1, 0, 0));

		bob.answer(0, CORRECT);

		assertThat(Stomp.next(bob.queue(), "ANSWER_ACK")).containsEntry("accepted", false);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM answer WHERE game_id = ?", Integer.class, gameId)).isEqualTo(1);
	}

	/** Spec → Game flow → Battle: the server's timer reveals at the deadline, and nothing moves on until the Host says so. */
	@Test
	void anUnansweredQuestionRevealsItselfAtTheDeadlineAndThenWaitsForTheHost() throws Exception {
		var gameId = createBattle();
		var ada = join(gameId, "Ada");
		var bob = join(gameId, "Bob");
		start(gameId, ada, bob);

		assertThat(Stomp.next(ada.topic(), "REVEAL")).containsEntry("counts", List.of(0, 0, 0, 0));

		for (var phone : List.of(ada, bob)) {
			assertThat(Stomp.next(phone.queue(), "RESULT")).containsEntry("correct", false).containsEntry("streak", 0);
		}
		assertThat(ada.topic().poll(4, TimeUnit.SECONDS)).as("no auto-advance: the Host drives a Battle").isNull();
		assertThat(jdbc.queryForObject("SELECT status FROM game WHERE id = ?", String.class, gameId)).isEqualTo("REVEAL");
	}

	/** Ticket #9: the Host-only topic carries who is in on the open question and what everyone has scored. */
	@Test
	void theHostTopicShowsWhoHasAnsweredAndEveryScore() throws Exception {
		var gameId = createBattle();
		var ada = join(gameId, "Ada");
		var bob = join(gameId, "Bob");
		var screen = bigScreen(gameId);
		start(gameId, ada, bob);

		assertThat(rows(Stomp.next(screen.host(), "HOST_STATE"), "players")).hasSize(2)
				.allSatisfy(player -> assertThat(player).containsEntry("answered", false).containsEntry("score", 0));

		ada.answer(0, CORRECT);
		Stomp.next(ada.queue(), "ANSWER_ACK");

		var roster = rows(Stomp.next(screen.host(), "HOST_STATE"), "players");
		assertThat(roster.get(0)).containsEntry("name", "Ada").containsEntry("answered", true)
				.containsEntry("id", ada.playerId().toString());
		assertThat((int) roster.get(0).get("score")).isPositive();
		assertThat(roster.get(1)).containsEntry("name", "Bob").containsEntry("answered", false).containsEntry("score", 0);
	}

	/** Ticket #9: every command is idempotent, and End finishes the Battle wherever it had got to. */
	@Test
	void everyCommandIsIdempotentAndEndFinishesFromAnyState() throws Exception {
		var gameId = createBattle();
		var ada = join(gameId, "Ada");
		var bob = join(gameId, "Bob");
		var screen = bigScreen(gameId);
		start(gameId, ada, bob);

		assertThat(command(gameId, "start")).as("a second Start does not restart the Battle").isEqualTo("QUESTION");
		assertThat(command(gameId, "next")).as("Next during a question waits for the reveal").isEqualTo("QUESTION");
		assertThat(ada.topic().poll(500, TimeUnit.MILLISECONDS)).as("neither published anything").isNull();

		assertThat(command(gameId, "reveal")).isEqualTo("REVEAL");
		assertThat(command(gameId, "reveal")).as("a second Reveal has nothing left to end").isEqualTo("REVEAL");
		for (var phone : List.of(ada, bob)) {
			Stomp.next(phone.queue(), "RESULT");
		}
		Stomp.next(ada.topic(), "REVEAL");

		assertThat(command(gameId, "end")).isEqualTo("FINISHED");
		assertThat(command(gameId, "end")).as("already over").isEqualTo("FINISHED");

		assertThat(rows(Stomp.next(ada.topic(), "GAME_OVER"), "podium")).as("one REVEAL, then the Podium").hasSize(2);
		Stomp.next(screen.board(), "DAY_LEADERBOARD");
		assertThat(ada.topic().poll(500, TimeUnit.MILLISECONDS)).as("one Podium, not two").isNull();
		assertThat(jdbc.queryForObject("SELECT status FROM game WHERE id = ?", String.class, gameId)).isEqualTo("FINISHED");
	}

	/** Ticket #9: a Player who drops out is simply absent from later Answers; their Score stands and the Podium keeps them. */
	@Test
	void aPlayerWhoDisconnectsKeepsTheirScoreAndStaysOnThePodium() throws Exception {
		var gameId = createBattle();
		var ada = join(gameId, "Ada");
		var bob = join(gameId, "Bob");
		start(gameId, ada, bob);
		bothAnswer(ada, bob, 0);
		var scored = jdbc.queryForObject("SELECT score FROM player WHERE id = ?", Integer.class, ada.playerId());
		ada.session().disconnect();

		command(gameId, "next");
		Stomp.next(bob.topic(), "LEADERBOARD");
		assertThat(command(gameId, "next")).isEqualTo("QUESTION");
		Stomp.next(bob.topic(), "QUESTION_START");
		bob.answer(1, CORRECT);
		Stomp.next(bob.queue(), "ANSWER_ACK");

		assertThat(bob.topic().poll(500, TimeUnit.MILLISECONDS))
				.as("one Answer of two does not end the question: the Battle still counts Ada, gone or not").isNull();
		assertThat(command(gameId, "reveal")).isEqualTo("REVEAL");
		Stomp.next(bob.queue(), "RESULT");
		Stomp.next(bob.topic(), "REVEAL");

		assertThat(command(gameId, "end")).isEqualTo("FINISHED");

		var podium = rows(Stomp.next(bob.topic(), "GAME_OVER"), "podium");
		assertThat(podium).hasSize(2);
		assertThat(podium).filteredOn(row -> "Ada".equals(row.get("name"))).singleElement()
				.satisfies(row -> assertThat(row).containsEntry("score", scored));
		assertThat(scored).isPositive();
	}

	/** End works from the lobby too, and a tie on the Podium keeps join order — which the sort must not lose. */
	@Test
	void endFromTheLobbyFinishesWithATiedPodiumInJoinOrder() throws Exception {
		var gameId = createBattle();
		var ada = join(gameId, "Ada");
		var bob = join(gameId, "Bob");
		// join order is joined_at, not insertion order: make the two disagree, so the sort key is what passes this
		jdbc.update("UPDATE player SET joined_at = joined_at - interval '1 minute' WHERE id = ?", bob.playerId());

		assertThat(command(gameId, "end")).isEqualTo("FINISHED");

		var podium = rows(afterLobby(ada, "GAME_OVER"), "podium");
		assertThat(podium).extracting(row -> row.get("name")).containsExactly("Bob", "Ada");
		assertThat(podium).allSatisfy(row -> assertThat(row).containsEntry("score", 0).containsEntry("delta", 0));
	}
}
