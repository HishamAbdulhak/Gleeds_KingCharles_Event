package com.gleeds.quiz.admin;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the single Admin from ADMIN_EMAIL / ADMIN_PASSWORD. A Java migration (not SQL) so the
 * password is bcrypt-hashed here and never appears in a script or in flyway_schema_history.
 * Spring Boot hands JavaMigration beans to Flyway, which is how this sees the properties.
 */
@Component
public class V2__SeedAdmin extends BaseJavaMigration {

	private final String email;
	private final String password;

	V2__SeedAdmin(@Value("${admin.email}") String email, @Value("${admin.password}") String password) {
		this.email = email;
		this.password = password;
	}

	@Override
	public void migrate(Context context) throws Exception {
		try (var stmt = context.getConnection()
				.prepareStatement("INSERT INTO admin_user (email, password_hash) VALUES (?, ?)")) {
			stmt.setString(1, email);
			stmt.setString(2, new BCryptPasswordEncoder().encode(password));
			stmt.executeUpdate();
		}
	}
}
