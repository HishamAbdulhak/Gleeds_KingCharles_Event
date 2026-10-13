package com.gleeds.quiz.question;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A question's Category: one of three, each with its own fixed number of questions in every Game (docs/adr/0007).
 * The label is what the database stores, the wire carries and the Admin types; frontend/lib/admin.ts mirrors it.
 */
public enum Category {
	KING_CHARLES("King Charles", "king_charles_per_game"),
	UK_SAUDI("UK–Saudi", "uk_saudi_per_game"),
	GLEEDS("Gleeds", "gleeds_per_game");

	/** For messages; annotation values need a constant, so it can't be built from the labels. */
	public static final String NAMES = "King Charles, UK–Saudi, Gleeds";

	private final String label;
	private final String perGameColumn;

	Category(String label, String perGameColumn) {
		this.label = label;
		this.perGameColumn = perGameColumn;
	}

	@JsonValue
	public String label() {
		return label;
	}

	/** The settings column holding how many questions of this Category each Game draws (V4__category_mix.sql). */
	public String perGameColumn() {
		return perGameColumn;
	}

	/** Case-insensitive, trimmed, a plain hyphen for the dash ("uk-saudi" → UK_SAUDI); null when it names none. */
	@JsonCreator
	public static Category parse(String text) {
		if (text == null) {
			return null;
		}
		var wanted = text.strip().replace('-', '–');
		return Arrays.stream(values()).filter(c -> c.label.equalsIgnoreCase(wanted)).findFirst().orElse(null);
	}
}
