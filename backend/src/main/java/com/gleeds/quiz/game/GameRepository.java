package com.gleeds.quiz.game;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, UUID> {

	/** A Game is always loaded with its Question Set (one query, not one per question). */
	@Override
	@EntityGraph(attributePaths = "questions")
	Optional<Game> findById(UUID id);
}
