package com.gleeds.quiz.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.gleeds.quiz.config.SecurityConfig;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

	private final JdbcTemplate jdbc;
	private final PasswordEncoder passwordEncoder;
	private final JwtEncoder jwtEncoder;

	AdminAuthController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
		this.jdbc = jdbc;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
	}

	record LoginRequest(String email, String password) {
	}

	@PostMapping("/login")
	Map<String, String> login(@RequestBody LoginRequest req) {
		jdbc.query("SELECT password_hash FROM admin_user WHERE lower(email) = lower(?)", (rs, i) -> rs.getString(1),
				req.email()).stream().findFirst()
				.filter(hash -> passwordEncoder.matches(req.password() == null ? "" : req.password(), hash))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad credentials"));

		var now = Instant.now();
		var claims = JwtClaimsSet.builder()
				.subject(req.email().toLowerCase())
				.claim("scope", SecurityConfig.ADMIN_SCOPE)
				.issuedAt(now)
				.expiresAt(now.plus(Duration.ofHours(24)))   // "a session that lasts the day"
				.build();
		var token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
		return Map.of("token", token.getTokenValue());
	}
}
