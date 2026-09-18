package com.gleeds.quiz.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.gleeds.quiz.config.SecurityConfig;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

	/** "A session that lasts the day." */
	static final Duration TOKEN_TTL = Duration.ofHours(24);

	private final AdminUserRepository admins;
	private final PasswordEncoder passwordEncoder;
	private final JwtEncoder jwtEncoder;

	AdminAuthController(AdminUserRepository admins, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
		this.admins = admins;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
	}

	record LoginRequest(String email, String password) {
	}

	@PostMapping("/login")
	Map<String, String> login(@RequestBody LoginRequest req) {
		var admin = admins.findByEmailIgnoreCase(req.email() == null ? "" : req.email())
				.filter(a -> passwordEncoder.matches(req.password() == null ? "" : req.password(), a.getPasswordHash()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad credentials"));

		var now = Instant.now();
		var claims = JwtClaimsSet.builder()
				.subject(admin.getEmail())
				.claim(SecurityConfig.ROLES_CLAIM, List.of("ADMIN"))
				.issuedAt(now)
				.expiresAt(now.plus(TOKEN_TTL))
				.build();
		var token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
		return Map.of("token", token.getTokenValue());
	}

	@GetMapping("/me")
	Map<String, String> me(@AuthenticationPrincipal Jwt jwt) {
		return Map.of("email", jwt.getSubject());
	}
}
