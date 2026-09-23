package com.gleeds.quiz.game;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;


/**
 * Live Game state, keyed by Game id. The database is the record; this holds only what a database can't: the monotonic
 * clock reading at question start, which response times are measured against (spec → Scoring), and who has already
 * answered the open question. A Solo Game paces itself; a Battle is driven by the Host's commands.
 */
@Service
public class GameEngine {

	/** Solo pacing: the gap between a question's RESULT and the next QUESTION_START. A Battle waits for the Host. */
	private static final long NEXT_QUESTION_DELAY_MS = 3_000;

	private static final Logger log = LoggerFactory.getLogger(GameEngine.class);

	/** The open question of one live Game. Fields are guarded by the instance's monitor; {@link #answers} is its own. */
	private static final class Live {
		final UUID gameId;
		int index = -1;
		long startNanos;
		long deadlineNanos;
		boolean open;
		/** Concurrent: the reveal and the Host state read it while an Answer that beat the deadline is still scoring. */
		final Map<UUID, Answered> answers = new ConcurrentHashMap<>();
		/** Ends the question at the deadline; cancelled when it ends early. */
		ScheduledFuture<?> timer;

		Live(UUID gameId) {
			this.gameId = gameId;
		}

		/** A Player's Answer to the open question: what they chose, and what it scored — null until it is stored. */
		record Answered(int option, GameEvent.Result result) {
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
			return answers.putIfAbsent(playerId, new Answered(option, null)) == null ? null : "Already answered";
		}

		/** What the Player's Answer to the question just played earned them; 0 when they did not answer it. */
		int points(UUID playerId) {
			var answered = answers.get(playerId);
			return answered == null || answered.result() == null ? 0 : answered.result().points();
		}

		/** How many chose each option, for the reveal, off the Answers as they stood when the question closed. */
		static List<Integer> counts(Map<UUID, Answered> answers) {
			var counts = new int[4];
			answers.values().forEach(answered -> counts[answered.option()]++);
			return Arrays.stream(counts).boxed().toList();
		}
	}

	private final Map<UUID, Live> live = new ConcurrentHashMap<>();
	/** One daemon thread for every timer: nothing here is slow enough to need more, and no thread ever busy-waits. */
	private final ScheduledExecutorService scheduler = Executors
			.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().factory());

	private final GameRepository games;
	private final PlayerRepository players;
	/** Answers are write-only here (the board reads them in SQL), so a plain insert beats an entity. */
	private final JdbcTemplate jdbc;
	private final DayLeaderboard leaderboard;
	private final SimpMessagingTemplate messaging;
	/** Explicit transactions: the timer callbacks are internal calls, which a {@code @Transactional} proxy never sees. */
	private final TransactionTemplate tx;

	GameEngine(GameRepository games, PlayerRepository players, JdbcTemplate jdbc, DayLeaderboard leaderboard,
			SimpMessagingTemplate messaging, PlatformTransactionManager transactions) {
		this.games = games;
		this.players = players;
		this.jdbc = jdbc;
		this.leaderboard = leaderboard;
		this.messaging = messaging;
		this.tx = new TransactionTemplate(transactions);
	}

	/**
	 * A client is subscribed to the Game topic (docs/adr/0001). A Solo Game starts on its Player's ready: the first
	 * question goes out; a second ready (page refresh) is ignored. A Battle in LOBBY answers anyone's ready — the
	 * Player who just joined, a refreshed Host — with the lobby. Past LOBBY nothing happens yet: SYNC is a later ticket.
	 */
	public void ready(UUID gameId, boolean fromPlayer) {
		tx.executeWithoutResult(status -> {
			var game = games.findById(gameId).orElseThrow();
			if (game.getStatus() != Game.Status.LOBBY) {
				return;
			}
			if (game.isBattle()) {
				publishLobby(game);
			} else if (fromPlayer) {   // an Admin watching a Solo topic must not start it before its Player is listening
				game.startNextQuestion(Instant.now());
				publishQuestion(game, live.computeIfAbsent(gameId, Live::new));
			}
		});
	}

	// --- Host commands (spec → Game flow → Battle). Each is idempotent and answers with the Game's new status. ---

	/** LOBBY → the first question. A Battle that has already started is left alone, so a double tap can't restart it. */
	public Game.Status start(UUID gameId) {
		return command(gameId, (game, state) -> {
			if (game.getStatus() == Game.Status.LOBBY) {
				game.startNextQuestion(Instant.now());
				publishQuestion(game, state);
			}
		});
	}

	/** Ends the open question now, wherever its timer had got to (spec story 31). Nothing to do once it is over. */
	public Game.Status reveal(UUID gameId) {
		var state = live.get(gameId);
		if (state != null) {
			endQuestion(state);
		}
		return games.findById(gameId).orElseThrow().getStatus();
	}

	/** REVEAL → the leaderboard; LEADERBOARD → the next question, or the Podium after the last. */
	public Game.Status next(UUID gameId) {
		return command(gameId, (game, state) -> {
			switch (game.getStatus()) {
				case REVEAL -> {
					game.showStandings();
					var event = new GameEvent("LEADERBOARD", new GameEvent.Standings(standings(gameId, state)));
					afterCommit(() -> messaging.convertAndSend(topic(gameId), event));
				}
				case LEADERBOARD -> advance(game, state);
				default -> {
					// LOBBY waits for start, QUESTION for the reveal, FINISHED has nowhere left to go
				}
			}
		});
	}

	/** Ends the Battle from any state: the Podium, then the big screen's idle Day Leaderboard. */
	public Game.Status end(UUID gameId) {
		var open = live.get(gameId);
		if (open != null) {
			endQuestion(open);   // whatever was open ends the normal way: its timer stops and every Player gets their RESULT
		}
		return command(gameId, (game, state) -> {
			if (game.getStatus() != Game.Status.FINISHED) {
				finish(game, state);
			}
		});
	}

	/** Runs a Host command against the live Game and answers with its status, which is what the big screen switches on. */
	private Game.Status command(UUID gameId, BiConsumer<Game, Live> action) {
		return tx.execute(status -> {
			var game = games.findById(gameId).orElseThrow();
			if (game.getStatus() == Game.Status.FINISHED) {
				return Game.Status.FINISHED;   // nothing left to drive, and no live state worth creating to find that out
			}
			action.accept(game, live.computeIfAbsent(gameId, Live::new));
			return game.getStatus();
		});
	}

	/** LOBBY_UPDATE with the Game's Players, on the Game topic once the caller's transaction commits. */
	public void publishLobby(Game game) {
		var lobby = players.findByGameIdOrderByJoinedAt(game.getId()).stream()
				.map(p -> new GameEvent.LobbyPlayer(p.getId(), p.getName())).toList();
		var event = new GameEvent("LOBBY_UPDATE", new GameEvent.LobbyUpdate(game.getPin(), lobby));
		afterCommit(() -> messaging.convertAndSend(topic(game.getId()), event));
	}

	/**
	 * A Player's Answer to {@code questionIndex}. Response time is clocked on entry, before any lookup. Refused with
	 * a negative ANSWER_ACK when the question isn't the open one, the deadline has passed or the Player already
	 * answered (in memory; the answer table's unique constraint is the backstop). Otherwise the Answer is stored and
	 * the Player's Score and Streak updated. A Solo Player gets their RESULT at once; a Battle's waits for the reveal,
	 * so one phone cannot show the group the correct option, and the Host topic gets the fresh roster instead.
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
		boolean everyoneIsIn;
		try {
			everyoneIsIn = tx.execute(status -> {
				var game = games.findById(gameId).orElseThrow();
				var player = players.findById(playerId).orElseThrow();
				var q = game.currentQuestion().toDto();
				boolean correct = q.correctOption() == option;
				var scored = Scoring.score(correct, responseMs, q.timeLimitSec() * 1000L, player.getStreak());
				player.apply(scored);
				jdbc.update("INSERT INTO answer (game_id, player_id, question_id, selected_option, correct, response_ms, points) "
						+ "VALUES (?, ?, ?, ?, ?, ?, ?)", gameId, playerId, q.id(), option, correct, responseMs, scored.points());
				var result = new GameEvent.Result(correct, scored.points(), scored.streak(), player.getScore(),
						q.correctOption());
				boolean stillOpen;
				synchronized (state) {
					state.answers.put(playerId, new Live.Answered(option, result));
					stillOpen = state.open;
				}
				afterCommit(() -> toPlayer(playerId, new GameEvent("ANSWER_ACK", new GameEvent.AnswerAck(true, null))));
				var lobby = players.findByGameIdOrderByJoinedAt(gameId);
				if (!game.isBattle() || !stillOpen) {
					// Solo has nobody to wait for; a Battle Answer that landed as the question ended has missed the reveal
					afterCommit(() -> toPlayer(playerId, new GameEvent("RESULT", result)));
				} else {
					publishHostState(gameId, lobby, state.answers.keySet());
				}
				return state.answers.size() == lobby.size();
			});
		} catch (RuntimeException e) {
			// the Answer was taken in memory but not stored: give it back, so the Player can retry or time out normally
			log.error("Storing Player {}'s Answer failed", playerId, e);
			state.answers.remove(playerId);
			refuse(playerId, "Server error");
			return;
		}
		if (everyoneIsIn) {   // nobody left to wait for: the question ends now rather than running its timer out
			endQuestion(state);
		}
	}

	private void refuse(UUID playerId, String reason) {
		toPlayer(playerId, new GameEvent("ANSWER_ACK", new GameEvent.AnswerAck(false, reason)));
	}

	/**
	 * Ends the open question exactly once (later Answers are refused), handing back the Answers as they stood at that
	 * instant; null if it was already over. The snapshot is what keeps the two RESULT senders apart: an Answer still
	 * scoring here goes on to read {@code open == false} and send its own, and sits in the snapshot with a null result,
	 * which {@link #endQuestion} skips. Without it the two could both fire and the phone's result screen would collapse.
	 */
	private Map<UUID, Live.Answered> close(Live state) {
		synchronized (state) {
			if (!state.open) {
				return null;
			}
			state.open = false;
			state.timer.cancel(false);
			return Map.copyOf(state.answers);
		}
	}

	/**
	 * The question is over, however it ended — the deadline, the Host's reveal, or the last Player answering. Everyone
	 * who did not answer gets a wrong-by-timeout RESULT and loses their Streak; in a Battle everyone else gets the
	 * RESULT held since their Answer, and the big screen gets the REVEAL. Solo then paces itself to the next question.
	 */
	private void endQuestion(Live state) {
		var answers = close(state);
		if (answers == null) {
			return;
		}
		tx.executeWithoutResult(status -> {
			var game = games.findById(state.gameId).orElseThrow();
			int correctOption = game.currentQuestion().toDto().correctOption();
			boolean battle = game.isBattle();
			var lobby = players.findByGameIdOrderByJoinedAt(state.gameId);
			for (var player : lobby) {
				var answered = answers.get(player.getId());
				if (answered == null) {
					player.apply(new Scoring.Scored(0, 0));
					var timedOut = new GameEvent.Result(false, 0, 0, player.getScore(), correctOption);
					afterCommit(() -> toPlayer(player.getId(), new GameEvent("RESULT", timedOut)));
				} else if (battle && answered.result() != null) {   // null: still scoring, and its own transaction sends it
					afterCommit(() -> toPlayer(player.getId(), new GameEvent("RESULT", answered.result())));
				}
			}
			if (battle) {
				game.reveal();
				var reveal = new GameEvent("REVEAL", new GameEvent.Reveal(correctOption, Live.counts(answers)));
				afterCommit(() -> messaging.convertAndSend(topic(state.gameId), reveal));
			} else {
				afterCommit(() -> schedule(NEXT_QUESTION_DELAY_MS, () -> autoAdvance(state)));
			}
		});
	}

	/** Solo pacing: the next question a few seconds after the result. */
	private void autoAdvance(Live state) {
		tx.executeWithoutResult(status -> {
			var game = games.findById(state.gameId).orElseThrow();
			if (game.getStatus() != Game.Status.FINISHED) {   // the Host ended the Game while this timer was pending
				advance(game, state);
			}
		});
	}

	/** The next question of the Question Set, or the end of the Game after the last. */
	private void advance(Game game, Live state) {
		if (game.startNextQuestion(Instant.now())) {
			publishQuestion(game, state);
		} else {
			finish(game, state);
		}
	}

	/**
	 * Marks the Game FINISHED, tells the Players how it ended, and pushes the fresh board to the Host screen. Each
	 * Player then gets their name and best Score today on their own queue: the phone that proves the prize
	 * (docs/adr/0003), and the email stays here.
	 */
	private void finish(Game game, Live state) {
		game.finish(Instant.now());
		var lobby = players.findByGameIdOrderByJoinedAt(game.getId());
		var best = lobby.stream().collect(Collectors.toMap(Player::getId, p -> leaderboard.bestOf(p.getEmail())));
		GameEvent.GameOver ending;
		if (game.isBattle()) {
			ending = new GameEvent.GameOver(null, null, standings(game.getId(), state));
		} else {
			var player = lobby.get(0);   // Solo: exactly one
			ending = new GameEvent.GameOver(player.getScore(),
					best.get(player.getId()).map(DayLeaderboard.Entry::rank).orElse(null), null);
		}
		var over = new GameEvent("GAME_OVER", ending);
		var board = new GameEvent("DAY_LEADERBOARD", new GameEvent.Board(leaderboard.top()));
		afterCommit(() -> {
			live.remove(game.getId());
			messaging.convertAndSend(topic(game.getId()), over);
			for (var player : lobby) {
				var score = best.get(player.getId()).map(DayLeaderboard.Entry::score).orElse(null);
				toPlayer(player.getId(), new GameEvent("BEST_SCORE", new GameEvent.BestScore(player.getName(), score)));
			}
			messaging.convertAndSend("/topic/leaderboard", board);
		});
	}

	/** The Standings: every Player by Score, with what the question just played earned them. Ties keep join order. */
	private List<GameEvent.Standing> standings(UUID gameId, Live state) {
		return players.findByGameIdOrderByJoinedAt(gameId).stream()
				.map(p -> new GameEvent.Standing(p.getId(), p.getName(), p.getScore(), state.points(p.getId())))
				.sorted(Comparator.comparingInt(GameEvent.Standing::score).reversed())
				.toList();
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
				state.answers.clear();
				state.timer = schedule(TimeUnit.SECONDS.toMillis(q.timeLimitSec()), () -> endQuestion(state));
			}
			messaging.convertAndSend(topic(game.getId()), event);
		});
		if (game.isBattle()) {   // a fresh question, so nobody has answered it yet
			publishHostState(game.getId(), players.findByGameIdOrderByJoinedAt(game.getId()), Set.of());
		}
	}

	/** HOST_STATE on the Host-only topic: the roster with who is in on the open question and everyone's Score. */
	private void publishHostState(UUID gameId, List<Player> lobby, Set<UUID> answered) {
		var roster = lobby.stream()
				.map(p -> new GameEvent.HostPlayer(p.getId(), p.getName(), answered.contains(p.getId()), p.getScore()))
				.toList();
		var event = new GameEvent("HOST_STATE", new GameEvent.HostState(roster));
		afterCommit(() -> messaging.convertAndSend(topic(gameId) + "/host", event));
	}

	private static String topic(UUID gameId) {
		return "/topic/game/" + gameId;
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
