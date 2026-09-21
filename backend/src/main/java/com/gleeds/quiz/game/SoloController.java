package com.gleeds.quiz.game;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** Solo start: public, no PIN. Creates the Game, its Question Set and the one Player in a single transaction. */
@RestController
public class SoloController {

	private final GameRepository games;
	private final PlayerRepository players;
	private final QuestionBank bank;

	SoloController(GameRepository games, PlayerRepository players, QuestionBank bank) {
		this.games = games;
		this.players = players;
		this.bank = bank;
	}

	@PostMapping("/api/solo")
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	Seat start(@Valid @RequestBody JoinRequest req) {
		var game = games.save(Game.solo(bank.draw()));
		var player = players.save(new Player(game.getId(), req.name(), req.email()));
		return Seat.of(player);
	}
}
