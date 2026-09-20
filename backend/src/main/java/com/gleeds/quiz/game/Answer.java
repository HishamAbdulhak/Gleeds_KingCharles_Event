package com.gleeds.quiz.game;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A Player's single, final choice for one question (table {@code answer}; unique per player + question). */
@Entity
@Table(name = "answer")
public class Answer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID gameId;
	private UUID playerId;
	private Long questionId;
	@JdbcTypeCode(SqlTypes.SMALLINT)
	private int selectedOption;
	private boolean correct;
	/** Server-measured: monotonic receipt minus monotonic question start. */
	private int responseMs;
	private int points;
	private Instant submittedAt = Instant.now();

	protected Answer() {
	}

	public Answer(UUID gameId, UUID playerId, Long questionId, int selectedOption, boolean correct, int responseMs,
			int points) {
		this.gameId = gameId;
		this.playerId = playerId;
		this.questionId = questionId;
		this.selectedOption = selectedOption;
		this.correct = correct;
		this.responseMs = responseMs;
		this.points = points;
	}
}
