package com.gleeds.quiz.game;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<Settings, Boolean> {

	default Settings get() {
		return findById(true).orElseThrow();   // V1__init.sql inserts the row; it can never be deleted
	}
}
