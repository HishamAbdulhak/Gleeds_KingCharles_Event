package com.gleeds.quiz.admin;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * How many questions of each Category every Game draws (spec story 42, docs/adr/0007); a Game is their sum. A running
 * Game keeps its Question Set: it was drawn at creation. Admin-only via SecurityConfig's /api/admin/** rule.
 */
@RestController
@RequestMapping("/api/admin/settings")
public class SettingsController {

	/** Boxed, so a PUT that leaves a count out is a 400, not a silent 0. */
	record Settings(@NotNull @Min(0) Integer kingCharlesPerGame, @NotNull @Min(0) Integer ukSaudiPerGame,
			@NotNull @Min(0) Integer gleedsPerGame) {
	}

	private final JdbcTemplate jdbc;

	SettingsController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@GetMapping
	Settings get() {
		// the single settings row (V1__init.sql inserts it; it can never be deleted)
		return jdbc.queryForObject("SELECT king_charles_per_game, uk_saudi_per_game, gleeds_per_game FROM settings",
				(rs, i) -> new Settings(rs.getInt(1), rs.getInt(2), rs.getInt(3)));
	}

	@PutMapping
	Settings put(@Valid @RequestBody Settings settings) {
		if (settings.kingCharlesPerGame() + settings.ukSaudiPerGame() + settings.gleedsPerGame() < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A Game needs at least one question");
		}
		jdbc.update("UPDATE settings SET king_charles_per_game = ?, uk_saudi_per_game = ?, gleeds_per_game = ?",
				settings.kingCharlesPerGame(), settings.ukSaudiPerGame(), settings.gleedsPerGame());
		return settings;
	}
}
