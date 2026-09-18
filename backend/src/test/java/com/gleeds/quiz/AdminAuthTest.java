package com.gleeds.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

/** Boundary test: admin login and Bearer-JWT protection of /api/admin/**. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AdminAuthTest {

	@LocalServerPort
	int port;

	@Value("${admin.email}")
	String adminEmail;

	@Value("${admin.password}")
	String adminPassword;

	RestClient client() {
		return RestClient.create("http://localhost:" + port);
	}

	@SuppressWarnings("unchecked")
	Map<String, String> login(String email, String password) {
		return client().post().uri("/api/admin/login").body(Map.of("email", email, "password", password)).retrieve()
				.body(Map.class);
	}

	@Test
	void loginReturnsToken() {
		var body = login(adminEmail, adminPassword);
		assertThat(body.get("token")).isNotBlank();
	}

	@Test
	void loginIsCaseInsensitiveOnEmail() {
		assertThat(login(adminEmail.toUpperCase(), adminPassword).get("token")).isNotBlank();
	}

	@Test
	void wrongPasswordIs401() {
		var status = client().post().uri("/api/admin/login")
				.body(Map.of("email", adminEmail, "password", "nope"))
				.exchange((req, res) -> res.getStatusCode());
		assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void adminEndpointWithoutTokenIs401() {
		var status = client().get().uri("/api/admin/me").exchange((req, res) -> res.getStatusCode());
		assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void adminEndpointWithGarbageTokenIs401() {
		var status = client().get().uri("/api/admin/me").header("Authorization", "Bearer not.a.jwt")
				.exchange((req, res) -> res.getStatusCode());
		assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void adminEndpointWithTokenIs200() {
		var token = login(adminEmail, adminPassword).get("token");
		var response = client().get().uri("/api/admin/me").header("Authorization", "Bearer " + token).retrieve()
				.toEntity(String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains(adminEmail);
	}
}
