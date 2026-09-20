package com.gleeds.quiz.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Stateless Bearer-JWT security. Tokens are HS256 signed with JWT_SECRET; the resource-server
 * filter verifies them and maps the {@code scope} claim to SCOPE_* authorities.
 */
@Configuration
public class SecurityConfig {

	/** Value of the JWT {@code scope} claim; Spring's default converter exposes it as {@link #ADMIN_AUTHORITY}. */
	public static final String ADMIN_SCOPE = "ADMIN";
	public static final String ADMIN_AUTHORITY = "SCOPE_" + ADMIN_SCOPE;

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> {})
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.oauth2ResourceServer(rs -> rs.jwt(jwt -> {}))
				.authorizeHttpRequests(auth -> auth
						// Boot renders exceptions by forwarding to /error; a public endpoint's 400 must not become a 401
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(HttpMethod.GET, "/api/health").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/admin/login", "/api/solo").permitAll()
						.requestMatchers("/ws").permitAll()   // STOMP CONNECT is authenticated by WsAuthInterceptor
						.requestMatchers("/api/admin/**").hasAuthority(ADMIN_AUTHORITY)
						.anyRequest().authenticated())
				.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${cors.origin}") String origin) {
		var config = new CorsConfiguration();
		config.setAllowedOrigins(List.of(origin));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecretKeySpec jwtKey(@Value("${jwt.secret}") String secret) {
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKeySpec key) {
		return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKeySpec key) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(key));
	}
}
