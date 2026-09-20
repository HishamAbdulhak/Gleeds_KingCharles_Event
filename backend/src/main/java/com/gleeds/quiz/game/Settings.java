package com.gleeds.quiz.game;

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

	protected Settings() {
	}

	public int getQuestionsPerGame() {
		return questionsPerGame;
	}
}
