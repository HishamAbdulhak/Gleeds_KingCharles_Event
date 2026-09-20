package com.gleeds.quiz.game;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Live Game state, keyed by Game id. The database is the record; this holds only what a database can't: the monotonic
 * clock reading at question start, which response times are measured against (spec → Scoring).
 */
@Service
public class GameEngine {

	/** Nanos ({@link System#nanoTime()}) at which the current question of each live Game started. */
	private final Map<UUID, Long> questionStartNanos = new ConcurrentHashMap<>();

	private final GameRepository games;
	private final SimpMessagingTemplate messaging;

	GameEngine(GameRepository games, SimpMessagingTemplate messaging) {
		this.games = games;
		this.messaging = messaging;
	}

	/**
	 * A Solo Player is subscribed and ready: publish the first question. Idempotent — a second ready (page refresh)
	 * is ignored; reconnect sync is a later ticket.
	 */
	public void startSolo(UUID gameId) {
		var game = games.findById(gameId).orElseThrow();
		if (game.getMode() != Game.Mode.SOLO || game.getStatus() != Game.Status.LOBBY) {
			return;
		}
		startQuestion(game, 0);
	}

	/** Saves before publishing, so a client never sees a question the database doesn't. */
	private void startQuestion(Game game, int index) {
		var startedAt = Instant.now();
		questionStartNanos.put(game.getId(), System.nanoTime());
		game.startQuestion(index, startedAt);
		games.save(game);
		var q = game.currentQuestion().toDto();
		messaging.convertAndSend("/topic/game/" + game.getId(), new GameEvent("QUESTION_START",
				new GameEvent.QuestionStart(index, q.text(), List.of(q.optionA(), q.optionB(), q.optionC(), q.optionD()),
						q.timeLimitSec(), startedAt)));
	}
}
