package com.gleeds.quiz.game;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.gleeds.quiz.question.Question;
import com.gleeds.quiz.question.QuestionRepository;

/** The Question Bank as Games see it: draws a Question Set (spec → Domain model), {@code questions_per_game} random active questions. */
@Service
class QuestionBank {

	private final QuestionRepository questions;
	private final JdbcTemplate jdbc;

	QuestionBank(QuestionRepository questions, JdbcTemplate jdbc) {
		this.questions = questions;
		this.jdbc = jdbc;
	}

	/** 409 when the Question Bank can't fill a Game. */
	List<Question> draw() {
		// the single settings row (V1__init.sql inserts it; it can never be deleted)
		int perGame = jdbc.queryForObject("SELECT questions_per_game FROM settings", Integer.class);
		var draw = questions.drawActive(perGame);
		if (draw.size() < perGame) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"The Question Bank has " + draw.size() + " active questions; a Game needs " + perGame);
		}
		return draw;
	}
}
