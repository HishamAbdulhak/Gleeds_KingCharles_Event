package com.gleeds.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/** Boundary test: real HTTP against the app, real Postgres via Testcontainers. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SecurityAndSchemaTest {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void anythingElseIsNotPublic() {
		var status = RestClient.create("http://localhost:" + port).get().uri("/api/anything").exchange((req, res) -> res.getStatusCode());
		assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void flywayCreatedTheSchema() {
		Integer failed = jdbc.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
		assertThat(failed).isZero();

		List<String> tables = jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1",
				String.class);
		assertThat(tables).contains("admin_user", "question", "settings", "game", "game_question", "player",
				"answer");

		Integer settingsRows = jdbc.queryForObject("SELECT count(*) FROM settings", Integer.class);
		assertThat(settingsRows).isEqualTo(1);
	}
}
