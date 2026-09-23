package com.gleeds.quiz;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Boundary test for the Solo game loop (#5): Answers over STOMP, personal RESULTs, server timer, pacing, GAME_OVER.
 * Real Postgres; nothing reaches into the engine.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SoloGameTest {

	static final int QUESTIONS_PER_GAME = 3;
	static final int TIME_LIMIT_SEC = 5;
	static final int CORRECT = 1;

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	StompSession session;

	/** One Solo Player mid-game: the session, the Game topic and the personal queue. */
	record Seat(UUID gameId, UUID playerId, String sessionToken, StompSession session,
			BlockingQueue<Map<String, Object>> topic, BlockingQueue<Map<String, Object>> queue) {

		void answer(int questionIndex, int option) {
			session.send("/app/game/" + gameId + "/answer", Map.of("questionIndex", questionIndex, "option", option));
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> next(BlockingQueue<Map<String, Object>> from, String type) throws InterruptedException {
			var event = from.poll(10, TimeUnit.SECONDS);
			assertThat(event).as("expected %s", type).isNotNull().containsEntry("type", type);
			return (Map<String, Object>) event.get("payload");
		}

		Map<String, Object> onTopic(String type) throws InterruptedException {
			return next(topic, type);
		}

		Map<String, Object> onQueue(String type) throws InterruptedException {
			return next(queue, type);
		}
	}

	@AfterEach
	void disconnect() {
		if (session != null && session.isConnected()) {
			session.disconnect();
		}
	}

	@BeforeEach
	void setUp() {
		Fixtures.questionBank(jdbc, QUESTIONS_PER_GAME, CORRECT, TIME_LIMIT_SEC);
	}

	/** POST /api/solo, connect, subscribe to topic + personal queue, say ready, consume the first QUESTION_START. */
	@SuppressWarnings("unchecked")
	Seat play() throws Exception {
		var started = RestClient.create("http://localhost:" + port).post().uri("/api/solo")
				.body(Map.of("name", "Ada", "email", "ada@example.com")).retrieve().body(Map.class);
		var gameId = UUID.fromString((String) started.get("gameId"));
		session = Stomp.connectAsPlayer(port, (String) started.get("sessionToken"));
		var queue = Stomp.subscribe(session, "/user/queue/player");
		var topic = Stomp.ready(session, gameId.toString());
		var seat = new Seat(gameId, UUID.fromString((String) started.get("playerId")),
				(String) started.get("sessionToken"), session, topic, queue);
		assertThat(seat.onTopic("QUESTION_START")).containsEntry("index", 0).containsEntry("total", QUESTIONS_PER_GAME);
		return seat;
	}

	/** The phone drops off and reopens the page: a new connection with the same Seat, subscribed and ready as before. */
	Seat reconnect(Seat seat) throws Exception {
		seat.session().disconnect();
		session = Stomp.connectAsPlayer(port, seat.sessionToken());
		var queue = Stomp.subscribe(session, "/user/queue/player");
		var topic = Stomp.ready(session, seat.gameId().toString());
		return new Seat(seat.gameId(), seat.playerId(), seat.sessionToken(), session, topic, queue);
	}

	@Test
	void correctFastAnswerIsAckedThenScoredWithStreak1() throws Exception {
		var seat = play();

		seat.answer(0, CORRECT);

		assertThat(seat.onQueue("ANSWER_ACK")).containsEntry("accepted", true);
		var result = seat.onQueue("RESULT");
		assertThat(result).containsEntry("correct", true).containsEntry("streak", 1).containsEntry("correctOption", CORRECT);
		assertThat((int) result.get("points")).isBetween(500, 1000);
		assertThat(result.get("score")).isEqualTo(result.get("points"));

		assertThat(jdbc.queryForMap("SELECT score, streak FROM player WHERE id = ?", seat.playerId()))
				.containsEntry("score", result.get("points")).containsEntry("streak", 1);
		assertThat(jdbc.queryForMap("SELECT selected_option, correct, points FROM answer WHERE player_id = ?", seat.playerId()))
				.containsEntry("selected_option", CORRECT).containsEntry("correct", true).containsEntry("points", result.get("points"));
		assertThat(jdbc.queryForObject("SELECT response_ms FROM answer WHERE player_id = ?", Integer.class, seat.playerId()))
				.isBetween(0, TIME_LIMIT_SEC * 1000);
	}

	@Test
	void secondAnswerToTheSameQuestionIsRefused() throws Exception {
		var seat = play();
		seat.answer(0, CORRECT);
		seat.onQueue("ANSWER_ACK");
		seat.onQueue("RESULT");

		seat.answer(0, 2);

		assertThat(seat.onQueue("ANSWER_ACK")).containsEntry("accepted", false);
		assertThat(seat.queue().poll(1, TimeUnit.SECONDS)).as("no second RESULT").isNull();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM answer WHERE player_id = ?", Integer.class, seat.playerId()))
				.isEqualTo(1);
	}

	@Test
	void answerForAStaleIndexIsRefused() throws Exception {
		var seat = play();

		seat.answer(2, CORRECT);

		assertThat(seat.onQueue("ANSWER_ACK")).containsEntry("accepted", false);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM answer", Integer.class)).isZero();
	}

	@Test
	void answerWithAnOptionOutsideAToDIsRefused() throws Exception {
		var seat = play();

		seat.answer(0, 4);

		assertThat(seat.onQueue("ANSWER_ACK")).containsEntry("accepted", false);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM answer", Integer.class)).isZero();
	}

	/** Spec test 3: the whole Solo run. Fast correct Answers → Streak 1, 2, 3 with multipliers 1.1 and 1.2 → GAME_OVER. */
	@Test
	void fullRunWithFastCorrectAnswersPacesToTheNextQuestionAndEndsWithGameOver() throws Exception {
		var seat = play();
		int score = 0;
		int[][] pointsRange = {{500, 1000}, {1001, 1100}, {1101, 1200}};   // ×1.0, ×1.1, ×1.2 on a fast base
		for (int i = 0; i < QUESTIONS_PER_GAME; i++) {
			if (i > 0) {
				var before = System.nanoTime();
				assertThat(seat.onTopic("QUESTION_START")).containsEntry("index", i);
				assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before)).as("~3 s pacing").isBetween(2000L, 4500L);
			}
			seat.answer(i, CORRECT);
			assertThat(seat.onQueue("ANSWER_ACK")).containsEntry("accepted", true);
			var result = seat.onQueue("RESULT");
			assertThat(result).containsEntry("correct", true).containsEntry("streak", i + 1);
			assertThat((int) result.get("points")).as("points of answer %d", i + 1).isBetween(pointsRange[i][0], pointsRange[i][1]);
			score += (int) result.get("points");
			assertThat(result).containsEntry("score", score);
		}

		var over = seat.onTopic("GAME_OVER");
		assertThat(over).containsEntry("score", score);
		assertThat(seat.onQueue("BEST_SCORE")).as("the prize proof: this email's first Game is its best today")
				.isEqualTo(Map.of("name", "Ada", "score", score));

		assertThat(jdbc.queryForMap("SELECT status, score FROM game g JOIN player p ON p.game_id = g.id WHERE g.id = ?", seat.gameId()))
				.containsEntry("status", "FINISHED").containsEntry("score", score);
		assertThat(jdbc.queryForObject("SELECT ended_at FROM game WHERE id = ?", Object.class, seat.gameId())).isNotNull();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM answer WHERE game_id = ?", Integer.class, seat.gameId()))
				.isEqualTo(QUESTIONS_PER_GAME);
	}

	/** Spec test 5: the server ends an unanswered question; a late Answer is refused and the timeout RESULT shows Streak 0. */
	@Test
	void unansweredQuestionTimesOutWithStreakResetAndALateAnswerIsRefused() throws Exception {
		var seat = play();
		seat.answer(0, CORRECT);
		seat.onQueue("ANSWER_ACK");
		int score = (int) seat.onQueue("RESULT").get("score");
		assertThat(seat.onTopic("QUESTION_START")).containsEntry("index", 1);

		var asked = System.nanoTime();
		var timeout = seat.onQueue("RESULT");   // no Answer sent
		assertThat(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - asked)).isBetween((long) TIME_LIMIT_SEC - 1, (long) TIME_LIMIT_SEC + 2);
		assertThat(timeout).containsEntry("correct", false).containsEntry("points", 0).containsEntry("streak", 0)
				.containsEntry("score", score).containsEntry("correctOption", CORRECT);

		seat.answer(1, CORRECT);
		assertThat(seat.onQueue("ANSWER_ACK")).containsEntry("accepted", false);

		assertThat(jdbc.queryForMap("SELECT score, streak FROM player WHERE id = ?", seat.playerId()))
				.containsEntry("score", score).containsEntry("streak", 0);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM answer WHERE player_id = ?", Integer.class, seat.playerId()))
				.isEqualTo(1);
		assertThat(seat.onTopic("QUESTION_START")).as("the run carries on after a timeout").containsEntry("index", 2);
	}

	/** Ticket #11's boundary test: mid-question, the reopened page gets the same question on the same clock. */
	@Test
	@SuppressWarnings("unchecked")
	void reconnectingMidQuestionSyncsTheOpenQuestionWithItsOriginalStart() throws Exception {
		var seat = play();
		var startedAt = jdbc.queryForObject("SELECT question_started_at FROM game WHERE id = ?", java.sql.Timestamp.class,
				seat.gameId()).toInstant().toString();

		var back = reconnect(seat);

		var sync = back.onQueue("SYNC");
		assertThat(sync).containsEntry("status", "QUESTION").containsEntry("questionIndex", 0)
				.containsEntry("startedAt", startedAt).containsEntry("timeLimitSec", TIME_LIMIT_SEC)
				.containsEntry("answered", false).containsEntry("score", 0).containsEntry("streak", 0);
		assertThat((Map<String, Object>) sync.get("question")).containsEntry("index", 0).containsEntry("startedAt", startedAt)
				.containsKeys("text", "options");
		back.answer(0, CORRECT);
		assertThat(back.onQueue("ANSWER_ACK")).as("the synced question is still open").containsEntry("accepted", true);
	}

	/** Ticket #11: after answering, the reopened page has no question to show again, only the RESULT it already earned. */
	@Test
	void reconnectingAfterAnsweringSyncsNoQuestionAndResendsTheResult() throws Exception {
		var seat = play();
		seat.answer(0, CORRECT);
		seat.onQueue("ANSWER_ACK");
		var result = seat.onQueue("RESULT");

		var back = reconnect(seat);   // inside the ~3 s before the next question

		assertThat(back.onQueue("SYNC")).containsEntry("status", "QUESTION").containsEntry("questionIndex", 0)
				.containsEntry("answered", true).containsEntry("score", result.get("score")).containsEntry("streak", 1)
				.doesNotContainKey("question");
		assertThat(back.onQueue("RESULT")).isEqualTo(result);
		assertThat(back.onTopic("QUESTION_START")).as("the run carries on on the new connection").containsEntry("index", 1);
	}
}
