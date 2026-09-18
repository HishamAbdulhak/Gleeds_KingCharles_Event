package com.gleeds.quiz.question;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row of the Question Bank (table {@code question}, V1__init.sql). */
@Entity
@Table(name = "question")
public class Question {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String text;

	// explicit names: Spring's snake_case strategy turns "optionA" into "optiona"
	@Column(name = "option_a")
	private String optionA;
	@Column(name = "option_b")
	private String optionB;
	@Column(name = "option_c")
	private String optionC;
	@Column(name = "option_d")
	private String optionD;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	private int correctOption;

	private int timeLimitSec = 20;   // matches the column default in V1__init.sql
	private String category;
	private boolean active = true;

	protected Question() {
	}

	public Question(QuestionDto dto) {
		apply(dto);
	}

	/**
	 * Copies every editable field from the DTO. Omitted {@code timeLimitSec} / {@code active} keep the current value
	 * (the field defaults on a new Question), so a PUT without {@code active} can't silently reactivate.
	 */
	public void apply(QuestionDto dto) {
		text = dto.text();
		optionA = dto.optionA();
		optionB = dto.optionB();
		optionC = dto.optionC();
		optionD = dto.optionD();
		correctOption = dto.correctOption();
		timeLimitSec = dto.timeLimitSec() == null ? timeLimitSec : dto.timeLimitSec();
		category = dto.category();
		active = dto.active() == null ? active : dto.active();
	}

	public QuestionDto toDto() {
		return new QuestionDto(id, text, optionA, optionB, optionC, optionD, correctOption, timeLimitSec, category,
				active);
	}

	public void deactivate() {
		active = false;
	}
}
