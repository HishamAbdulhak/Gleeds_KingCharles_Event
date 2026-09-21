package com.gleeds.quiz;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/** Rows and credentials the boundary tests start from. */
final class Fixtures {

	/** The Admin V2__seed_admin.sql creates from src/test/resources/application.properties. */
	static final String ADMIN_EMAIL = "admin@test.local";
	static final String ADMIN_PASSWORD = "correct-horse";

	private Fixtures() {
	}

	/** The Admin's JWT, the way the Host screen gets it. */
	static String adminToken(int port) {
		return (String) RestClient.create("http://localhost:" + port).post().uri("/api/admin/login")
				.body(Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD)).retrieve().body(Map.class).get("token");
	}

	/** Empties the Games and the Question Bank, then seeds {@code n} questions (Q1…, options a–d) and a Game size of {@code n}. */
	static void questionBank(JdbcTemplate jdbc, int n, int correctOption, int timeLimitSec) {
		jdbc.execute("TRUNCATE game, question CASCADE");
		jdbc.update("UPDATE settings SET questions_per_game = ?", n);
		for (int i = 1; i <= n; i++) {
			jdbc.update("INSERT INTO question (text, option_a, option_b, option_c, option_d, correct_option, time_limit_sec) "
					+ "VALUES (?, 'a', 'b', 'c', 'd', ?, ?)", "Q" + i, correctOption, timeLimitSec);
		}
	}
}
