package com.gleeds.quiz.game;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The single {@code settings} row (id is always {@code true}, enforced by V1__init.sql). */
@Entity
@Table(name = "settings")
public class Settings {

	@Id
	private Boolean id = true;

	private int questionsPerGame;
	private Instant leaderboardSince;

	protected Settings() {
	}

	public int getQuestionsPerGame() {
		return questionsPerGame;
	}

	public Instant getLeaderboardSince() {
		return leaderboardSince;
	}
}
