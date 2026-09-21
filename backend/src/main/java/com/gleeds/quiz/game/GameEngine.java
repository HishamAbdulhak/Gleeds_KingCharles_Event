package com.gleeds.quiz.game;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;


/**
 * Live Game state, keyed by Game id. The database is the record; this holds only what a database can't: the monotonic
 * clock reading at question start, which response times are measured against (spec → Scoring), and who has already
 * answered the open question.
 */
@Service
public class GameEngine {

	/** Solo pacing: the gap between a question's RESULT and the next QUESTION_START. */
	private static final long NEXT_QUESTION_DELAY_MS = 3_000;

	private static final Logger log = LoggerFactory.getLogger(GameEngine.class);

	/** The open question of one live Game. All fields guarded by the instance's monitor. */
	private static final class Live {
		final UUID gameId;
		int index = -1;
		long startNanos;
		long deadlineNanos;
		boolean open;
		final Set<UUID> answered = new HashSet<>();
		/** Ends the question at the deadline; cancelled when it ends early. */
		ScheduledFuture<?> timer;

		Live(UUID gameId) {
			this.gameId = gameId;
		}

		/** Takes the Player's Answer (marking them as answered) and returns null, or returns why it is refused. */
		String take(UUID playerId, int questionIndex, int option, long receiptNanos) {
			if (option < 0 || option > 3) {
				return "Option must be 0–3";
			}
			if (!open || questionIndex != index) {
				return "Question " + questionIndex + " is not open";
			}
			if (receiptNanos > deadlineNanos) {
				return "Too late";
			}
			return answered.add(playerId) ? null : "Already answered";
		}
	}

	private final Map<UUID, Live> live = new ConcurrentHashMap<>();
	/** One daemon thread for every timer: nothing here is slow enough to need more, and no thread ever busy-waits. */
	private final ScheduledExecutorService scheduler = Executors
			.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().factory());

	private final GameRepository games;
	private final PlayerRepository players;
	private final AnswerRepository answers;
	private final DayLeaderboard leaderboard;
	private final SimpMessagingTemplate messaging;
	/** Explicit transactions: the timer callbacks are internal calls, which a {@code @Transactional} proxy never sees. */
	private final TransactionTemplate tx;

	GameEngine(GameRepository games, PlayerRepository players, AnswerRepository answers, DayLeaderboard leaderboard,
			SimpMessagingTemplate messaging, PlatformTransactionManager transactions) {
		this.games = games;
		this.players = players;
		this.answers = answers;
		this.leaderboard = leaderboard;
		this.messaging = messaging;
		this.tx = new TransactionTemplate(transactions);
	}

	/**
	 * A Solo Player is subscribed and ready: publish the first question. Idempotent — a second ready (page refresh)
	 * is ignored; reconnect sync is a later ticket.
	 */
	public void startSolo(UUID gameId) {
		tx.executeWithoutResult(status -> {
			var game = games.findById(gameId).orElseThrow();
			if (game.getMode() != Game.Mode.SOLO || game.getStatus() != Game.Status.LOBBY) {
				return;
			}
			game.startNextQuestion(Instant.now());
			publishQuestion(game, live.computeIfAbsent(gameId, Live::new));
		});
	}

	/**
	 * A Player's Answer to {@code questionIndex}. Response time is clocked on entry, before any lookup. Refused with
	 * a negative ANSWER_ACK when the question isn't the open one, the deadline has passed or the Player already
	 * answered (in memory; the answer table's unique constraint is the backstop). Otherwise the Answer is stored,
	 * the Player's Score and Streak updated, and ANSWER_ACK then RESULT go to the Player's queue.
	 */
	public void answer(UUID gameId, UUID playerId, int questionIndex, int option) {
		long receiptNanos = System.nanoTime();
		var state = live.get(gameId);
		if (state == null) {
			refuse(playerId, "Game is not running");
			return;
		}
		String refusal;
		long responseMs;
		synchronized (state) {
			refusal = state.take(playerId, questionIndex, option, receiptNanos);
			responseMs = TimeUnit.NANOSECONDS.toMillis(receiptNanos - state.startNanos);
		}
		if (refusal != null) {
			refuse(playerId, refusal);
			return;
		}
		try {
			tx.executeWithoutResult(status -> {
				var game = games.findById(gameId).orElseThrow();
				var player = players.findById(playerId).orElseThrow();
				var q = game.currentQuestion().toDto();
				boolean correct = q.correctOption() == option;
				var scored = Scoring.score(correct, responseMs, q.timeLimitSec() * 1000L, player.getStreak());
				player.apply(scored);
				answers.save(new Answer(gameId, playerId, q.id(), option, correct, (int) responseMs, scored.points()));
				afterCommit(() -> {
					toPlayer(playerId, new GameEvent("ANSWER_ACK", new GameEvent.AnswerAck(true, null)));
					toPlayer(playerId, new GameEvent("RESULT", new GameEvent.Result(correct, scored.points(),
							scored.streak(), player.getScore(), q.correctOption())));
				});
			});
		} catch (RuntimeException e) {
			// the Answer was taken in memory but not stored: give it back, so the Player can retry or time out normally
			log.error("Storing Player {}'s Answer failed", playerId, e);
			synchronized (state) {
				state.answered.remove(playerId);
			}
			refuse(playerId, "Server error");
			return;
		}
		// ponytail: Solo is the only Mode yet, so the one Answer ends the question; Battle (ticket 08) waits for all
		if (close(state)) {
			schedule(NEXT_QUESTION_DELAY_MS, () -> next(state));
		}
	}

	private void refuse(UUID playerId, String reason) {
		toPlayer(playerId, new GameEvent("ANSWER_ACK", new GameEvent.AnswerAck(false, reason)));
	}

	/** Ends the open question exactly once: later Answers are refused. False if it was already over. */
	private boolean close(Live state) {
		synchronized (state) {
			if (!state.open) {
				return false;
			}
			state.open = false;
			state.timer.cancel(false);
			return true;
		}
	}

	/** The deadline passed: every Player without an Answer gets a wrong-by-timeout RESULT and loses their Streak. */
	private void timeout(Live state) {
		if (!close(state)) {
			return;   // an Answer got there first
		}
		tx.executeWithoutResult(status -> {
			var game = games.findById(state.gameId).orElseThrow();
			int correctOption = game.currentQuestion().toDto().correctOption();
			for (var player : players.findByGameId(state.gameId)) {
				if (state.answered.contains(player.getId())) {   // closed above on this thread: nobody adds any more
					continue;
				}
				player.apply(new Scoring.Scored(0, 0));
				afterCommit(() -> toPlayer(player.getId(),
						new GameEvent("RESULT", new GameEvent.Result(false, 0, 0, player.getScore(), correctOption))));
			}
		});
		schedule(NEXT_QUESTION_DELAY_MS, () -> next(state));
	}

	/** The next question, or GAME_OVER after the last. */
	private void next(Live state) {
		tx.executeWithoutResult(status -> {
			var game = games.findById(state.gameId).orElseThrow();
			if (game.startNextQuestion(Instant.now())) {
				publishQuestion(game, state);
			} else {
				finish(game);
			}
		});
	}

	/** Marks the Game FINISHED, tells the Player their Score and rank, and pushes the fresh board to the Host screen. */
	private void finish(Game game) {
		game.finish(Instant.now());
		var player = players.findByGameId(game.getId()).get(0);   // Solo: exactly one; Battle's Podium is ticket 08
		var rank = leaderboard.rankOf(player.getEmail()).orElse(null);
		var over = new GameEvent("GAME_OVER", new GameEvent.GameOver(player.getScore(), rank));
		var board = new GameEvent("DAY_LEADERBOARD", new GameEvent.Board(leaderboard.top(10)));   // ten rows read from 5 m
		afterCommit(() -> {
			live.remove(game.getId());
			messaging.convertAndSend("/topic/game/" + game.getId(), over);
			messaging.convertAndSend("/topic/leaderboard", board);
		});
	}

	/** A scheduled task that throws would otherwise vanish into the future: log it, so a stuck Game is at least visible. */
	private ScheduledFuture<?> schedule(long delayMs, Runnable task) {
		return scheduler.schedule(() -> {
			try {
				task.run();
			} catch (RuntimeException e) {
				log.error("Game timer failed", e);
			}
		}, delayMs, TimeUnit.MILLISECONDS);
	}

	/** QUESTION_START for the Game's current question, and the clock and timer that go with it. */
	private void publishQuestion(Game game, Live state) {
		int index = game.getCurrentQuestionIndex();
		var q = game.currentQuestion().toDto();
		var event = new GameEvent("QUESTION_START", new GameEvent.QuestionStart(index, game.questionCount(), q.text(),
				List.of(q.optionA(), q.optionB(), q.optionC(), q.optionD()), q.timeLimitSec(), game.getQuestionStartedAt()));
		afterCommit(() -> {
			// the clock starts when the question leaves the server, not when the row was written
			synchronized (state) {
				state.index = index;
				state.startNanos = System.nanoTime();
				state.deadlineNanos = state.startNanos + TimeUnit.SECONDS.toNanos(q.timeLimitSec());
				state.open = true;
				state.answered.clear();
				state.timer = schedule(TimeUnit.SECONDS.toMillis(q.timeLimitSec()), () -> timeout(state));
			}
			messaging.convertAndSend("/topic/game/" + game.getId(), event);
		});
	}

	private void toPlayer(UUID playerId, GameEvent event) {
		messaging.convertAndSendToUser(playerId.toString(), "/queue/player", event);
	}

	/** STOMP publishes happen after commit, so a client never sees a row the database doesn't. */
	private static void afterCommit(Runnable action) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
	}
}
