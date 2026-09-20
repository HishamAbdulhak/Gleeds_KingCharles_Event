package com.gleeds.quiz.game;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.gleeds.quiz.question.QuestionRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Solo start: public, no PIN. Creates the Game, its Question Set and the one Player in a single transaction. */
@RestController
public class SoloController {

	/** {@code consent} is a primitive so a missing field reads as false and fails {@link AssertTrue}. */
	record StartRequest(@NotBlank String name, @NotBlank @Email String email, @AssertTrue boolean consent) {
	}

	record Started(UUID gameId, UUID playerId, UUID sessionToken) {
	}

	private final GameRepository games;
	private final PlayerRepository players;
	private final QuestionRepository questions;
	private final JdbcTemplate jdbc;

	SoloController(GameRepository games, PlayerRepository players, QuestionRepository questions, JdbcTemplate jdbc) {
		this.games = games;
		this.players = players;
		this.questions = questions;
		this.jdbc = jdbc;
	}

	@PostMapping("/api/solo")
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	Started start(@Valid @RequestBody StartRequest req) {
		// the single settings row (V1__init.sql inserts it; it can never be deleted)
		int perGame = jdbc.queryForObject("SELECT questions_per_game FROM settings", Integer.class);
		var draw = questions.drawActive(perGame);
		if (draw.size() < perGame) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"The Question Bank has " + draw.size() + " active questions; a Game needs " + perGame);
		}
		var game = games.save(Game.solo(draw));
		var player = players.save(new Player(game.getId(), req.name(), req.email()));
		return new Started(game.getId(), player.getId(), player.getSessionToken());
	}
}
