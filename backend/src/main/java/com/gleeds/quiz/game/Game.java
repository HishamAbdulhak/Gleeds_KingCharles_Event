package com.gleeds.quiz.game;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.gleeds.quiz.question.Question;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/** One run of questions in one Mode (table {@code game}). Never reused. */
@Entity
@Table(name = "game")
public class Game {

	public enum Mode {
		SOLO, BATTLE
	}

	public enum Status {
		LOBBY, QUESTION, REVEAL, LEADERBOARD, FINISHED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)   // assigned on persist, so save() inserts without a lookup
	private UUID id;

	@Enumerated(EnumType.STRING)
	private Mode mode;

	@Enumerated(EnumType.STRING)
	private Status status = Status.LOBBY;

	private int currentQuestionIndex = -1;
	private Instant questionStartedAt;
	private Instant endedAt;

	/** The Question Set: {@code game_question} rows, {@code position} as list index. */
	@ManyToMany
	@JoinTable(name = "game_question", joinColumns = @JoinColumn(name = "game_id"), inverseJoinColumns = @JoinColumn(name = "question_id"))
	@OrderColumn(name = "position")
	private List<Question> questions;

	protected Game() {
	}

	/** A Solo Game: no PIN, straight to the first question when the Player is ready. */
	public static Game solo(List<Question> questions) {
		var game = new Game();
		game.mode = Mode.SOLO;
		game.questions = questions;
		return game;
	}

	public UUID getId() {
		return id;
	}

	public Mode getMode() {
		return mode;
	}

	public Status getStatus() {
		return status;
	}

	public int getCurrentQuestionIndex() {
		return currentQuestionIndex;
	}

	public Question currentQuestion() {
		return questions.get(currentQuestionIndex);
	}

	public int questionCount() {
		return questions.size();
	}

	public void startQuestion(int index, Instant at) {
		status = Status.QUESTION;
		currentQuestionIndex = index;
		questionStartedAt = at;
	}

	public void finish(Instant at) {
		status = Status.FINISHED;
		endedAt = at;
	}
}
