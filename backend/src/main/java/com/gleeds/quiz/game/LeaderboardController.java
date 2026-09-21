package com.gleeds.quiz.game;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Public reads of the Day Leaderboard: the Host idle screen's first paint and the Solo end screen. */
@RestController
public class LeaderboardController {

	record Rank(int rank) {
	}

	private final DayLeaderboard leaderboard;
	private final PlayerRepository players;

	LeaderboardController(DayLeaderboard leaderboard, PlayerRepository players) {
		this.leaderboard = leaderboard;
		this.players = players;
	}

	@GetMapping("/api/leaderboard")
	List<DayLeaderboard.Entry> top(@RequestParam(defaultValue = "10") int top) {
		return leaderboard.top(top);
	}

	/** Where the Solo Game's Player stands right now: their email's rank, which a worse replay doesn't lower. */
	@GetMapping("/api/leaderboard/rank")
	Rank rank(@RequestParam UUID gameId) {
		var player = players.findByGameId(gameId).stream().findFirst()   // Solo: exactly one
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such Game"));
		return new Rank(leaderboard.rankOf(player.getEmail())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not on the board since the last Reset")));
	}
}
