package com.gleeds.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V4__category_mix.sql against the Bank it meets on prod: a scratch database in the same container is migrated to V3,
 * given the live Bank's labels, then migrated on.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MigrationTest {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PostgreSQLContainer postgres;

	@Autowired
	Flyway flyway;

	@Test
	void legacyCategoriesBecomeUkSaudiAndAnythingElseIsParkedInactive() {
		jdbc.execute("DROP DATABASE IF EXISTS migration_test");
		jdbc.execute("CREATE DATABASE migration_test");
		var url = postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/migration_test");
		var scratch = new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
		var config = Flyway.configure().configuration(flyway.getConfiguration()).dataSource(scratch);
		var db = new JdbcTemplate(scratch);

		config.target("3").load().migrate();
		for (var category : new String[] { "UK", "Saudi Arabia", "UK-Saudi Relations", "UK-Saudi", "King Charles", "Gleeds",
				"Royal", null }) {
			db.update("INSERT INTO question (text, option_a, option_b, option_c, option_d, correct_option, category) "
					+ "VALUES (?, 'a', 'b', 'c', 'd', 0, ?)", String.valueOf(category), category);
		}
		config.target("4").load().migrate();

		assertThat(db.queryForList("SELECT text, category, active FROM question ORDER BY id")).containsExactly(
				row("UK", "UK–Saudi", true), row("Saudi Arabia", "UK–Saudi", true),
				row("UK-Saudi Relations", "UK–Saudi", true), row("UK-Saudi", "UK–Saudi", true),
				row("King Charles", "King Charles", true), row("Gleeds", "Gleeds", true),
				row("Royal", "Gleeds", false), row("null", "Gleeds", false));
		assertThat(db.queryForMap("SELECT king_charles_per_game, uk_saudi_per_game, gleeds_per_game FROM settings"))
				.isEqualTo(Map.of("king_charles_per_game", 4, "uk_saudi_per_game", 3, "gleeds_per_game", 3));
	}

	static Map<String, Object> row(String text, String category, boolean active) {
		return Map.of("text", text, "category", category, "active", active);
	}
}
