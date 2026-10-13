package com.gleeds.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/** Boundary test for the Admin's settings (#10, spec stories 42 and 46, docs/adr/0007): each Game's Category mix. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SettingsTest {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	RestClient admin;

	@BeforeEach
	void setUp() {
		Fixtures.questionBank(jdbc, 5, 1, 20);   // a Bank of 5 King Charles questions, Question Sets of 5
		admin = RestClient.builder().baseUrl("http://localhost:" + port)
				.defaultHeader("Authorization", "Bearer " + Fixtures.adminToken(port)).build();
	}

	@SuppressWarnings("unchecked")
	Map<String, Object> settings() {
		return admin.get().uri("/api/admin/settings").retrieve().body(Map.class);
	}

	static Map<String, Object> mix(int kingCharles, int ukSaudi, int gleeds) {
		return Map.of("kingCharlesPerGame", kingCharles, "ukSaudiPerGame", ukSaudi, "gleedsPerGame", gleeds);
	}

	/** Other test classes share the database and change the mix, so this puts back the column defaults first. */
	@Test
	void theDefaultMixIsFourKingCharlesThreeUkSaudiAndThreeGleeds() {
		jdbc.update("UPDATE settings SET king_charles_per_game = DEFAULT, uk_saudi_per_game = DEFAULT, gleeds_per_game = DEFAULT");

		assertThat(settings()).isEqualTo(mix(4, 3, 3));
	}

	@Test
	@SuppressWarnings("unchecked")
	void theNextSoloGameDrawsTheUpdatedMix() throws Exception {
		admin.put().uri("/api/admin/settings").body(mix(3, 0, 0)).retrieve().toBodilessEntity();

		assertThat(settings()).isEqualTo(mix(3, 0, 0));
		var started = RestClient.create("http://localhost:" + port).post().uri("/api/solo")
				.body(Map.of("name", "Ada", "email", "ada@example.com")).retrieve().body(Map.class);
		var session = Stomp.connectAsPlayer(port, (String) started.get("sessionToken"));
		var question = Stomp.next(Stomp.ready(session, (String) started.get("gameId")), "QUESTION_START");
		assertThat(question).containsEntry("total", 3);
		session.disconnect();
	}

	static List<Map<String, Object>> badMixes() {
		return List.of(mix(-1, 3, 3), mix(4, -1, 3), mix(4, 3, -1), mix(0, 0, 0), Map.of("kingCharlesPerGame", 4));
	}

	@ParameterizedTest
	@MethodSource("badMixes")
	void aNegativeOrMissingCountOrAnEmptyGameIsRefusedAndTheOldMixKept(Map<String, Object> bad) {
		var status = admin.put().uri("/api/admin/settings").body(bad).exchange((req, res) -> res.getStatusCode());

		assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(settings()).isEqualTo(mix(5, 0, 0));
	}

	@Test
	void settingsWithoutATokenAre401() {
		var anonymous = RestClient.create("http://localhost:" + port);
		var read = anonymous.get().uri("/api/admin/settings").exchange((req, res) -> res.getStatusCode());
		var write = anonymous.put().uri("/api/admin/settings").body(mix(3, 0, 0))
				.exchange((req, res) -> res.getStatusCode());

		assertThat(read).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(write).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(settings()).isEqualTo(mix(5, 0, 0));
	}
}
