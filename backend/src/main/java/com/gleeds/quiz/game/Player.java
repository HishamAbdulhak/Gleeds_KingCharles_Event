package com.gleeds.quiz.game;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A person in exactly one Game (table {@code player}). The row doubles as the Lead. */
@Entity
@Table(name = "player")
public class Player {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)   // assigned on persist, so save() inserts without a lookup
	private UUID id;

	private UUID gameId;
	private String name;
	private String email;
	private Instant consentedAt = Instant.now();
	/** STOMP CONNECT credential and reconnect key. */
	private UUID sessionToken = UUID.randomUUID();
	private int score;
	private int streak;

	protected Player() {
	}

	public Player(UUID gameId, String name, String email) {
		this.gameId = gameId;
		this.name = name;
		this.email = email;
	}

	public UUID getId() {
		return id;
	}

	public UUID getGameId() {
		return gameId;
	}

	public UUID getSessionToken() {
		return sessionToken;
	}

	public int getScore() {
		return score;
	}

	public int getStreak() {
		return streak;
	}

	/** Banks an Answer's (or a timeout's) Points and carries its Streak forward. */
	public void apply(Scoring.Scored scored) {
		score += scored.points();
		streak = scored.streak();
	}
}
