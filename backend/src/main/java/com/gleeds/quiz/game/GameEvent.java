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

	/** Game topic, Battle: how many Players are in on the open question, after every accepted Answer. */
	public record AnswerCount(int answered, int total) {
	}

	/** Game topic, Battle: the question is over — the correct option and how many chose each, by option index. */
	public record Reveal(int correctOption, int[] counts) {
	}

	/** One Player's place in the Standings: their Score, and what the question just played earned them. */
	public record Standing(UUID playerId, String name, int score, int delta) {
	}

	/** Game topic, Battle between questions, as LEADERBOARD (the spec's name): the Standings, best first. */
	public record Standings(List<Standing> players) {
	}

	/** Host topic, Battle: the roster as the big screen needs it — who is in on the open question, and everyone's Score. */
	public record HostState(List<HostPlayer> players) {
	}

	public record HostPlayer(UUID id, String name, boolean answered, int score) {
	}

	/**
	 * Game topic, the end of the Game. Solo: the Player's final Score and their rank on the Day Leaderboard
	 * ({@code rank} is null only when a Reset happened mid-Game, so the Game no longer counts). Battle: the Podium,
	 * every Player ranked, which is the same Standings the leaderboard between questions shows.
	 */
	public record GameOver(Integer score, Integer rank, List<Standing> podium) {

		static GameOver solo(int score, Integer rank) {
			return new GameOver(score, rank, null);
		}

		static GameOver battle(List<Standing> podium) {
			return new GameOver(null, null, podium);
		}
	}

	/** Leaderboard topic, as DAY_LEADERBOARD: the top of the Day Leaderboard whenever a Game finishes or a Reset happens. */
	public record Board(List<DayLeaderboard.Entry> top) {
	}
}
