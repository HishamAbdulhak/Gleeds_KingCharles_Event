package com.gleeds.quiz;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Boundary test: Question Bank CRUD and CSV/XLSX import over HTTP, real Postgres. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class QuestionBankTest {

	static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
	};

	@LocalServerPort
	int port;

	@Value("${admin.email}")
	String adminEmail;

	@Value("${admin.password}")
	String adminPassword;

	@Autowired
	JdbcTemplate jdbc;

	RestClient admin;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		jdbc.execute("TRUNCATE game, question CASCADE");
		var token = RestClient.create("http://localhost:" + port).post().uri("/api/admin/login")
				.body(Map.of("email", adminEmail, "password", adminPassword)).retrieve().body(Map.class).get("token");
		admin = RestClient.builder().baseUrl("http://localhost:" + port)
				.defaultHeader("Authorization", "Bearer " + token).build();
	}

	static Map<String, Object> question(String text) {
		return new HashMap<>(Map.of("text", text, "optionA", "A", "optionB", "B", "optionC", "C", "optionD", "D",
				"correctOption", 2, "timeLimitSec", 30, "category", "ROYAL"));
	}

	List<Map<String, Object>> list() {
		return admin.get().uri("/api/admin/questions").retrieve().body(LIST);
	}

	@Test
	@SuppressWarnings("unchecked")
	void createThenListShowsTheQuestion() {
		var created = admin.post().uri("/api/admin/questions").body(question("Who is the King?")).retrieve()
				.toEntity(Map.class);

		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(created.getBody().get("id")).isNotNull();
		assertThat(created.getBody().get("active")).isEqualTo(true);
		assertThat(created.getHeaders().getLocation()).hasPath("/api/admin/questions/" + created.getBody().get("id"));

		var questions = list();
		assertThat(questions).hasSize(1);
		assertThat(questions.get(0)).containsEntry("text", "Who is the King?").containsEntry("correctOption", 2)
				.containsEntry("timeLimitSec", 30).containsEntry("category", "ROYAL").containsEntry("active", true);
	}

	@Test
	void invalidQuestionIs400WithFieldMessage() {
		var q = question("Bad");
		q.put("optionB", " ");
		q.put("correctOption", 4);
		q.put("timeLimitSec", 3);

		assertThatThrownBy(() -> admin.post().uri("/api/admin/questions").body(q).retrieve().toBodilessEntity())
				.isInstanceOfSatisfying(HttpClientErrorException.BadRequest.class, e -> assertThat(e.getResponseBodyAsString())
						.contains("optionB").contains("correctOption").contains("timeLimitSec"));
		assertThat(list()).isEmpty();
	}

	@SuppressWarnings("unchecked")
	long create(String text) {
		return ((Number) admin.post().uri("/api/admin/questions").body(question(text)).retrieve().body(Map.class)
				.get("id")).longValue();
	}

	@Test
	void updateReplacesTheQuestion() {
		var id = create("Old text");
		var q = question("New text");
		q.put("correctOption", 0);
		q.put("active", false);

		var status = admin.put().uri("/api/admin/questions/{id}", id).body(q).exchange((req, res) -> res.getStatusCode());

		assertThat(status).isEqualTo(HttpStatus.OK);
		assertThat(list()).singleElement().satisfies(saved -> assertThat(saved).containsEntry("id", (int) id)
				.containsEntry("text", "New text").containsEntry("correctOption", 0).containsEntry("active", false));
	}

	@Test
	void updateWithoutActiveOrTimeLimitKeepsTheCurrentValues() {
		var id = create("Deactivated");
		var off = question("Deactivated");
		off.put("active", false);
		admin.put().uri("/api/admin/questions/{id}", id).body(off).retrieve().toBodilessEntity();
		var q = question("Still deactivated");
		q.remove("timeLimitSec");   // fixture never sets "active", so this PUT omits both

		admin.put().uri("/api/admin/questions/{id}", id).body(q).retrieve().toBodilessEntity();

		assertThat(list()).singleElement().satisfies(saved -> assertThat(saved).containsEntry("active", false)
				.containsEntry("timeLimitSec", 30));
	}

	@Test
	void updateOfUnknownIdIs404() {
		var status = admin.put().uri("/api/admin/questions/{id}", 999999).body(question("x"))
				.exchange((req, res) -> res.getStatusCode());
		assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void deleteRemovesAnUnreferencedQuestion() {
		var id = create("Delete me");

		var status = admin.delete().uri("/api/admin/questions/{id}", id).exchange((req, res) -> res.getStatusCode());

		assertThat(status).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(list()).isEmpty();
	}

	@Test
	void deleteOfAQuestionUsedByAGameDeactivatesItInstead() {
		var id = create("Played already");
		jdbc.update("INSERT INTO game (id, mode) VALUES (gen_random_uuid(), 'SOLO')");
		jdbc.update("INSERT INTO game_question (game_id, question_id, position) SELECT id, ?, 0 FROM game", id);

		var status = admin.delete().uri("/api/admin/questions/{id}", id).exchange((req, res) -> res.getStatusCode());

		assertThat(status).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(list()).singleElement().satisfies(saved -> assertThat(saved).containsEntry("id", (int) id)
				.containsEntry("active", false));
	}

	@Test
	void deleteOfUnknownIdIs404() {
		var status = admin.delete().uri("/api/admin/questions/{id}", 999999).exchange((req, res) -> res.getStatusCode());
		assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
	}

	// --- import: the client's sheet, header + 4 rows, row 4 has an invalid `correct` ---

	static final String[][] SHEET = {
			{ "Text", "A", "B", "C", "D", "Correct", "Time_Limit", "Category" },
			{ "Q1", "a1", "b1", "c1", "d1", "C", "30", "ROYAL" },
			{ "Q2", "a2", "b2", "c2", "d2", "a", "", "" },
			{ "Q3", "a3", "b3", "c3", "d3", "E", "20", "" },
			{ "Q4", "a4", "b4", "c4", "d4", "D", "15", "UK_SAUDI" } };

	static byte[] csv(String[][] rows) {
		return Arrays.stream(rows).map(r -> String.join(",", r)).collect(Collectors.joining("\n")).getBytes(UTF_8);
	}

	static byte[] xlsx(String[][] rows) throws IOException {
		try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
			var sheet = wb.createSheet();
			for (int r = 0; r < rows.length; r++) {
				var row = sheet.createRow(r);
				for (int c = 0; c < rows[r].length; c++) {
					var cell = row.createCell(c);
					if (rows[r][c].matches("\\d+")) {
						cell.setCellValue(Integer.parseInt(rows[r][c]));   // Excel stores numbers as numbers
					} else {
						cell.setCellValue(rows[r][c]);
					}
				}
			}
			wb.write(out);
			return out.toByteArray();
		}
	}

	@SuppressWarnings("unchecked")
	Map<String, Object> importFile(String filename, byte[] bytes) {
		var parts = new LinkedMultiValueMap<String, Object>();
		parts.add("file", new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return filename;
			}
		});
		return admin.post().uri("/api/admin/questions/import").contentType(MediaType.MULTIPART_FORM_DATA).body(parts)
				.retrieve().body(Map.class);
	}

	@SuppressWarnings("unchecked")
	void assertSheetImported(Map<String, Object> result) {
		assertThat(result).containsEntry("imported", 3);
		var errors = (List<Map<String, Object>>) result.get("errors");
		assertThat(errors).singleElement().satisfies(e -> {
			assertThat(e).containsEntry("row", 4);
			assertThat((String) e.get("message")).containsIgnoringCase("correct");
		});

		var questions = list();
		assertThat(questions).extracting(q -> q.get("text")).containsExactly("Q1", "Q2", "Q4");
		assertThat(questions.get(0)).containsEntry("correctOption", 2).containsEntry("timeLimitSec", 30)
				.containsEntry("category", "ROYAL").containsEntry("active", true);
		assertThat(questions.get(1)).containsEntry("correctOption", 0).containsEntry("timeLimitSec", 20);
		assertThat(questions.get(1).get("category")).isNull();
		assertThat(questions.get(2)).containsEntry("correctOption", 3).containsEntry("timeLimitSec", 15)
				.containsEntry("category", "UK_SAUDI");
	}

	@Test
	void csvImportInsertsValidRowsAndReportsTheBadOne() {
		assertSheetImported(importFile("questions.csv", csv(SHEET)));
	}

	@Test
	void xlsxImportMatchesCsv() throws IOException {
		assertSheetImported(importFile("questions.xlsx", xlsx(SHEET)));
	}

	@Test
	void importWithoutRequiredColumnsIs400WithTheMissingNames() {
		assertThatThrownBy(() -> importFile("bad.csv", "text,a,b\nQ,1,2".getBytes(UTF_8)))
				.isInstanceOfSatisfying(HttpClientErrorException.BadRequest.class,
						e -> assertThat(e.getResponseBodyAsString()).contains("c, d, correct"));
		assertThat(list()).isEmpty();
	}
}
