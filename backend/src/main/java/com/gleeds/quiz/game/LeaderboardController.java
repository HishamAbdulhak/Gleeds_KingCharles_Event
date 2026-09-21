package com.gleeds.quiz.game;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public read of the Day Leaderboard: the Host idle screen's first paint (updates arrive on the topic). */
@RestController
public class LeaderboardController {

	private final DayLeaderboard leaderboard;

	LeaderboardController(DayLeaderboard leaderboard) {
		this.leaderboard = leaderboard;
	}

	@GetMapping("/api/leaderboard")
	List<DayLeaderboard.Entry> top() {
		return leaderboard.top();
	}
}
