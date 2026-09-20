package com.gleeds.quiz.game;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A person in exactly one Game (table {@code player}). The row doubles as the Lead. */
@Entity
@Table(name = "player")
public class Player {

	@Id
	private UUID id = UUID.randomUUID();

	private UUID gameId;
	private String name;
	private String email;
	private Instant consentedAt = Instant.now();
	/** STOMP CONNECT credential and reconnect key. */
	private UUID sessionToken = UUID.randomUUID();
	private int score;
	private int streak;
	private Instant joinedAt = Instant.now();

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
}
