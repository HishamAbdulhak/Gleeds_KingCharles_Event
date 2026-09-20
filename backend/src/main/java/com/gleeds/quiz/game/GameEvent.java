package com.gleeds.quiz.game;

import java.time.Instant;
import java.util.List;

/** Every server → client STOMP message. Mirrored by {@code frontend/lib/game.ts}. */
public record GameEvent(String type, Object payload) {

	/** A question as the Player sees it: no correct option. */
	public record QuestionStart(int index, String text, List<String> options, int timeLimitSec, Instant startedAt) {
	}

	/** Personal queue: was the Answer taken? {@code reason} only when refused. */
	public record AnswerAck(boolean accepted, String reason) {
	}

	/** Personal queue: what the Answer (or timeout) earned and the Player's running Score. */
	public record Result(boolean correct, int points, int streak, int score, int correctOption) {
	}

	/** Game topic, Solo: the Player's final Score and total response time (rank is ticket 07). */
	public record GameOver(int score, long totalResponseMs) {
	}
}
