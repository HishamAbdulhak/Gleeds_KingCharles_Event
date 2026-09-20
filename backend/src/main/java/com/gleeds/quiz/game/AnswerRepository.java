package com.gleeds.quiz.game;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

	/** A Player's time over the whole Game; a question without an Answer counts as its full time limit (spec → Day Leaderboard). */
	@Query(value = """
			SELECT coalesce(sum(coalesce(a.response_ms, q.time_limit_sec * 1000)), 0)
			FROM game_question gq
			JOIN question q ON q.id = gq.question_id
			LEFT JOIN answer a ON a.question_id = gq.question_id AND a.player_id = :playerId
			WHERE gq.game_id = :gameId""", nativeQuery = true)
	long totalResponseMs(UUID gameId, UUID playerId);
}
