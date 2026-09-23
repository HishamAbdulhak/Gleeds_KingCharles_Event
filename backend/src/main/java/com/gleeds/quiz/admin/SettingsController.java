package com.gleeds.quiz.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * How many questions each Game draws (spec story 42). A running Game keeps its Question Set: it was drawn at creation.
 * Admin-only via SecurityConfig's /api/admin/** rule.
 */
@RestController
@RequestMapping("/api/admin/settings")
public class SettingsController {

	record Settings(@Min(1) @Max(50) int questionsPerGame) {
	}

	private final JdbcTemplate jdbc;

	SettingsController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@GetMapping
	Settings get() {
		// the single settings row (V1__init.sql inserts it; it can never be deleted)
		return new Settings(jdbc.queryForObject("SELECT questions_per_game FROM settings", Integer.class));
	}

	@PutMapping
	@Transactional
	Settings put(@Valid @RequestBody Settings settings) {
		jdbc.update("UPDATE settings SET questions_per_game = ?", settings.questionsPerGame());
		return settings;
	}
}
