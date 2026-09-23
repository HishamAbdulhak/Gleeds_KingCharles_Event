package com.gleeds.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/** Boundary test for the Admin's settings (#10, spec stories 42 and 46): how many questions each Game draws. */
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
		Fixtures.questionBank(jdbc, 5, 1, 20);   // a Bank of 5, Question Sets of 5
		admin = RestClient.builder().baseUrl("http://localhost:" + port)
				.defaultHeader("Authorization", "Bearer " + Fixtures.adminToken(port)).build();
	}

	@SuppressWarnings("unchecked")
	Map<String, Object> settings() {
		return admin.get().uri("/api/admin/settings").retrieve().body(Map.class);
	}

	@Test
	@SuppressWarnings("unchecked")
	void theNextSoloGameDrawsTheUpdatedNumberOfQuestions() throws Exception {
		admin.put().uri("/api/admin/settings").body(Map.of("questionsPerGame", 3)).retrieve().toBodilessEntity();

		assertThat(settings()).isEqualTo(Map.of("questionsPerGame", 3));
		var started = RestClient.create("http://localhost:" + port).post().uri("/api/solo")
				.body(Map.of("name", "Ada", "email", "ada@example.com")).retrieve().body(Map.class);
		var session = Stomp.connectAsPlayer(port, (String) started.get("sessionToken"));
		var question = Stomp.next(Stomp.ready(session, (String) started.get("gameId")), "QUESTION_START");
		assertThat(question).containsEntry("total", 3);
		session.disconnect();
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 51 })
	void aQuestionSetSizeOutside1To50IsRefusedAndTheOldOneKept(int questionsPerGame) {
		var status = admin.put().uri("/api/admin/settings").body(Map.of("questionsPerGame", questionsPerGame))
				.exchange((req, res) -> res.getStatusCode());

		assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(settings()).isEqualTo(Map.of("questionsPerGame", 5));
	}

	@Test
	void settingsWithoutATokenAre401() {
		var anonymous = RestClient.create("http://localhost:" + port);
		var read = anonymous.get().uri("/api/admin/settings").exchange((req, res) -> res.getStatusCode());
		var write = anonymous.put().uri("/api/admin/settings").body(Map.of("questionsPerGame", 3))
				.exchange((req, res) -> res.getStatusCode());

		assertThat(read).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(write).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(settings()).isEqualTo(Map.of("questionsPerGame", 5));
	}
}
