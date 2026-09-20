package com.gleeds.quiz.game;

/** The Points formula, spec → Scoring. Pure; identical in both Modes. */
public final class Scoring {

	/** What one Answer (or timeout) does to a Player: the Points it earns and the Streak it leaves. */
	public record Scored(int points, int streak) {
	}

	private Scoring() {
	}

	/**
	 * @param responseMs server-measured, {@code 0 ≤ responseMs ≤ timeLimitMs}
	 * @param streak the Player's Streak before this Answer
	 */
	public static Scored score(boolean correct, long responseMs, long timeLimitMs, int streak) {
		if (!correct) {
			return new Scored(0, 0);
		}
		long base = Math.round(1000 * (1 - (responseMs / (double) timeLimitMs) / 2));   // 1000 instant → 500 at the buzzer
		int newStreak = streak + 1;
		double multiplier = 1 + 0.1 * Math.min(newStreak - 1, 5);                        // 1.0× first correct → 1.5× cap
		return new Scored((int) Math.round(base * multiplier), newStreak);
	}
}
