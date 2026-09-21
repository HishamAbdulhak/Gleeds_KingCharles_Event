package com.gleeds.quiz.game;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

	Optional<Player> findBySessionToken(UUID sessionToken);

	/** A Game's Players in the order they joined, which is the order the lobby shows them. */
	List<Player> findByGameIdOrderByJoinedAt(UUID gameId);
}
