package com.gleeds.quiz.game;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Every server → client STOMP message. Mirrored by {@code frontend/lib/game.ts}. */
public record GameEvent(String type, Object payload) {

	/**
	 * Game topic, Battle in LOBBY: who has joined, in join order. Sent on every join and on every {@code ready}, so a
	 * refreshed screen re-syncs from the topic alone; {@code pin} is there for the same reason (the Host shows it).
	 */
	public record LobbyUpdate(String pin, List<LobbyPlayer> players) {
	}

	public record LobbyPlayer(UUID id, String name) {
	}

	/** A question as the Player sees it: no correct option. {@code total} is the Question Set size, for "3 / 10". */
	public record QuestionStart(int index, int total, String text, List<String> options, int timeLimitSec,
			Instant startedAt) {
	}

	/** Personal queue: was the Answer taken? {@code reason} only when refused. */
	public record AnswerAck(boolean accepted, String reason) {
	}

	/** Personal queue: what the Answer (or timeout) earned and the Player's running Score. */
	public record Result(boolean correct, int points, int streak, int score, int correctOption) {
	}

	/**
	 * Game topic, Solo: the Player's final Score and their rank on the Day Leaderboard; {@code rank} is null only when
	 * a Reset happened mid-Game, so the Game no longer counts.
	 */
	public record GameOver(int score, Integer rank) {
	}

	/** Leaderboard topic, as DAY_LEADERBOARD: the top of the Day Leaderboard whenever a Game finishes or a Reset happens. */
	public record Board(List<DayLeaderboard.Entry> top) {
	}
}
