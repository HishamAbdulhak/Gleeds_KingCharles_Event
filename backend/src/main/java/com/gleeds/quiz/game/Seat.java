package com.gleeds.quiz.game;

import java.util.UUID;

/** A Player's Seat in one Game (CONTEXT.md): the ids and the session token the phone keeps. */
record Seat(UUID gameId, UUID playerId, UUID sessionToken) {

	static Seat of(Player player) {
		return new Seat(player.getGameId(), player.getId(), player.getSessionToken());
	}
}
