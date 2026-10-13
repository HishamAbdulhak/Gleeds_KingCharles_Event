package com.gleeds.quiz.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.gleeds.quiz.question.Category;
import com.gleeds.quiz.question.Question;
import com.gleeds.quiz.question.QuestionRepository;

/**
 * The Question Bank as Games see it: draws a Question Set (spec → Domain model), each Category's per-Game number of
 * random active questions, shuffled together (docs/adr/0007).
 */
@Service
class QuestionBank {

	private final QuestionRepository questions;
	private final JdbcTemplate jdbc;

	QuestionBank(QuestionRepository questions, JdbcTemplate jdbc) {
		this.questions = questions;
		this.jdbc = jdbc;
	}

	/** 409 naming every Category the Question Bank can't fill a Game from. */
	List<Question> draw() {
		// the single settings row (V1__init.sql inserts it; it can never be deleted)
		var settings = jdbc.queryForMap("SELECT * FROM settings");
		var draw = new ArrayList<Question>();
		var shortfalls = new ArrayList<String>();
		for (var category : Category.values()) {
			int perGame = (Integer) settings.get(category.perGameColumn());
			if (perGame == 0) {
				continue;
			}
			var drawn = questions.drawActive(category.label(), perGame);
			if (drawn.size() < perGame) {
				shortfalls.add("The Question Bank has " + drawn.size() + " active " + category.label()
						+ " questions; a Game needs " + perGame);
			}
			draw.addAll(drawn);
		}
		if (!shortfalls.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, String.join("; ", shortfalls));
		}
		Collections.shuffle(draw);   // the Categories interleave instead of playing in blocks
		return draw;
	}
}
