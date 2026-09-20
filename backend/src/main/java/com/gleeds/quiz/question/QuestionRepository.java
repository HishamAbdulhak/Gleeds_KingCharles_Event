package com.gleeds.quiz.question;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface QuestionRepository extends JpaRepository<Question, Long> {

	/** A Question Set draw: up to {@code n} random active questions (fewer if the Bank is short). */
	@Query(value = "SELECT * FROM question WHERE active ORDER BY random() LIMIT :n", nativeQuery = true)
	List<Question> drawActive(int n);

	/** Deletes the question unless a Game's Question Set references it (game_question is ON DELETE RESTRICT). */
	@Modifying
	@Query(value = "DELETE FROM question WHERE id = :id AND NOT EXISTS (SELECT 1 FROM game_question WHERE question_id = :id)", nativeQuery = true)
	int deleteIfUnreferenced(long id);
}
