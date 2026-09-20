package com.gleeds.quiz.game;

import java.time.Instant;
import java.util.List;

/** Every server → client STOMP message. Mirrored by {@code frontend/lib/game.ts}. */
public record GameEvent(String type, Object payload) {

	/** A question as the Player sees it: no correct option. */
	public record QuestionStart(int index, String text, List<String> options, int timeLimitSec, Instant startedAt) {
	}
}
