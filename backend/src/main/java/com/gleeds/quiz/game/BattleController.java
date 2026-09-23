package com.gleeds.quiz.game;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

/**
 * A Battle (spec → Game flow → Battle): an Admin creates it with a PIN, Players join by that PIN, and the Host's
 * commands drive it from the big screen. Every command is idempotent and answers with the Game's new status.
 */
@RestController
public class BattleController {

	/** Spec stories 28–29: no Battle with one person; a lobby stays readable on the big screen. */
	static final int MIN_PLAYERS = 2;
	static final int MAX_PLAYERS = 4;

	record Created(UUID gameId, String pin) {
	}

	/** What every Host command answers with: the Game's status once the command has been applied. */
	record State(Game.Status status) {
	}

	private final GameRepository games;
	private final PlayerRepository players;
	private final QuestionBank bank;
	private final GameEngine engine;

	BattleController(GameRepository games, PlayerRepository players, QuestionBank bank, GameEngine engine) {
		this.games = games;
		this.players = players;
		this.bank = bank;
		this.engine = engine;
	}

	@PostMapping("/api/games")
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	Created create() {   // any body is ignored: only Battles are created here (Solo starts at POST /api/solo)
		var draw = bank.draw();
		String pin;
		do {   // ponytail: a check-then-insert race is one in a million per concurrent create; the unique index is the backstop
			pin = "%06d".formatted(ThreadLocalRandom.current().nextInt(1_000_000));
		} while (games.existsByPin(pin));
		var game = games.save(Game.battle(draw, pin));
		return new Created(game.getId(), pin);
	}

	/**
	 * Public. 201 with the new Seat; 200 with the existing one when the email is already in this Game (a refresh
	 * reconnects, spec story 22 — before the guards, so a seated Player is never told the lobby is full). 409 with
	 * {@code GAME_STARTED} once the Battle has left LOBBY, {@code LOBBY_FULL} at four Players; the message is the code
	 * the join page maps to its wording.
	 */
	@PostMapping("/api/games/{pin}/join")
	@Transactional
	ResponseEntity<Seat> join(@PathVariable String pin, @Valid @RequestBody JoinRequest req) {
		var game = games.findByPin(pin)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No Battle with that PIN"));
		var lobby = players.findByGameIdOrderByJoinedAt(game.getId());
		var seated = lobby.stream().filter(p -> p.getEmail().equalsIgnoreCase(req.email())).findFirst();
		if (seated.isPresent()) {
			return ResponseEntity.ok(Seat.of(seated.get()));
		}
		if (game.getStatus() != Game.Status.LOBBY) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "GAME_STARTED");
		}
		// ponytail: two joins racing at three Players could seat a fifth; lock the Game row (FOR UPDATE) if a lobby ever overflows
		if (lobby.size() >= MAX_PLAYERS) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "LOBBY_FULL");
		}
		var player = players.save(new Player(game.getId(), req.name(), req.email()));
		engine.publishLobby(game);
		return ResponseEntity.status(HttpStatus.CREATED).body(Seat.of(player));
	}

	/** Admin. 409 below {@link #MIN_PLAYERS}; otherwise 202 with the new status. */
	@PostMapping("/api/games/{id}/start")
	@ResponseStatus(HttpStatus.ACCEPTED)
	State start(@PathVariable UUID id) {
		requireGame(id);
		if (players.findByGameIdOrderByJoinedAt(id).size() < MIN_PLAYERS) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "A Battle needs " + MIN_PLAYERS + "–" + MAX_PLAYERS + " Players");
		}
		return new State(engine.start(id));
	}

	/** Admin. Ends the open question now, so the group does not wait out a timer everyone has already beaten. */
	@PostMapping("/api/games/{id}/reveal")
	@ResponseStatus(HttpStatus.ACCEPTED)
	State reveal(@PathVariable UUID id) {
		requireGame(id);
		return new State(engine.reveal(id));
	}

	/** Admin. The reveal's leaderboard, then the next question — or the Podium after the last. */
	@PostMapping("/api/games/{id}/next")
	@ResponseStatus(HttpStatus.ACCEPTED)
	State next(@PathVariable UUID id) {
		requireGame(id);
		return new State(engine.next(id));
	}

	/** Admin. Ends the Battle from any state: the Podium, and the big screen's board is up to date again. */
	@PostMapping("/api/games/{id}/end")
	@ResponseStatus(HttpStatus.ACCEPTED)
	State end(@PathVariable UUID id) {
		requireGame(id);
		return new State(engine.end(id));
	}

	private void requireGame(UUID id) {
		if (!games.existsById(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such Game");
		}
	}
}
