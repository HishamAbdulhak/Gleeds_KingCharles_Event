package com.gleeds.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Boundary test for docs/adr/0007: every Game draws each Category's per-Game number, shuffled together. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CategoryMixTest {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	RestClient client;

	@BeforeEach
	void setUp() {
		jdbc.execute("TRUNCATE game, question CASCADE");
		for (var category : List.of("King Charles", "UK–Saudi", "Gleeds")) {
			for (int i = 1; i <= 5; i++) {
				jdbc.update("INSERT INTO question (text, option_a, option_b, option_c, option_d, correct_option, category) "
						+ "VALUES (?, 'a', 'b', 'c', 'd', 0, ?)", category + " " + i, category);
			}
		}
		Fixtures.perGame(jdbc, 2, 1, 1);
		client = RestClient.create("http://localhost:" + port);
	}

	@SuppressWarnings("unchecked")
	UUID startSolo() {
		var seat = client.post().uri("/api/solo").body(Map.of("name", "Ada", "email", "ada@example.com")).retrieve()
				.body(Map.class);
		return UUID.fromString((String) seat.get("gameId"));
	}

	List<String> categoriesInPlayOrder(UUID gameId) {
		return jdbc.queryForList("SELECT q.category FROM game_question gq JOIN question q ON q.id = gq.question_id "
				+ "WHERE gq.game_id = ? ORDER BY gq.position", String.class, gameId);
	}

	/** Grouped order comes up 1 time in 12 for a fair shuffle of 2/1/1, so ten grouped Games in a row mean no shuffle. */
	@Test
	void everySoloGameDrawsTheMixAndInterleavesTheCategories() {
		var games = IntStream.range(0, 10).mapToObj(i -> categoriesInPlayOrder(startSolo())).toList();

		assertThat(games).allSatisfy(categories -> assertThat(categories)
				.containsExactlyInAnyOrder("King Charles", "King Charles", "UK–Saudi", "Gleeds"));
		assertThat(games).anySatisfy(categories -> assertThat(categories)
				.isNotEqualTo(List.of("King Charles", "King Charles", "UK–Saudi", "Gleeds")));
	}

	static final String SHORT_GLEEDS = "The Question Bank has 2 active Gleeds questions; a Game needs 3";

	@Test
	void soloStartIs409NamingTheShortCategory() {
		Fixtures.perGame(jdbc, 2, 1, 3);
		jdbc.update("UPDATE question SET active = false WHERE text IN ('Gleeds 1', 'Gleeds 2', 'Gleeds 3')");

		assertThatThrownBy(this::startSolo).isInstanceOfSatisfying(HttpClientErrorException.Conflict.class,
				e -> assertThat(e.getResponseBodyAsString()).contains(SHORT_GLEEDS));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM game", Integer.class)).isZero();
	}

	@Test
	void battleCreateIs409NamingEveryShortCategory() {
		Fixtures.perGame(jdbc, 6, 1, 3);
		jdbc.update("UPDATE question SET active = false WHERE text IN ('Gleeds 1', 'Gleeds 2', 'Gleeds 3')");

		assertThatThrownBy(() -> client.post().uri("/api/games").header("Authorization", "Bearer " + Fixtures.adminToken(port))
				.body(Map.of("mode", "BATTLE")).retrieve().toBodilessEntity())
				.isInstanceOfSatisfying(HttpClientErrorException.Conflict.class, e -> assertThat(e.getResponseBodyAsString())
						.contains("The Question Bank has 5 active King Charles questions; a Game needs 6")
						.contains(SHORT_GLEEDS).doesNotContain("UK–Saudi questions"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM game", Integer.class)).isZero();
	}
}
