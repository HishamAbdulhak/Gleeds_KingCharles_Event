package com.gleeds.quiz.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The one pure unit test (spec → Testing Decisions): the formula the prize depends on. */
class ScoringTest {

	static final long LIMIT_MS = 20_000;

	@Test
	void instantCorrectAnswerIs1000() {
		assertThat(Scoring.score(true, 0, LIMIT_MS, 0)).isEqualTo(new Scoring.Scored(1000, 1));
	}

	@Test
	void correctAnswerAtTheBuzzerIs500() {
		assertThat(Scoring.score(true, LIMIT_MS, LIMIT_MS, 0)).isEqualTo(new Scoring.Scored(500, 1));
	}

	@Test
	void halfwayCorrectAnswerIs750() {
		assertThat(Scoring.score(true, 10_000, LIMIT_MS, 0).points()).isEqualTo(750);
	}

	@Test
	void wrongAnswerIsZeroAndResetsStreak() {
		assertThat(Scoring.score(false, 0, LIMIT_MS, 4)).isEqualTo(new Scoring.Scored(0, 0));
	}

	@Test
	void multiplierGrowsFrom1To1Point5AcrossSixConsecutiveCorrectAnswers() {
		int streak = 0;
		int[] expected = {1000, 1100, 1200, 1300, 1400, 1500};
		for (int i = 0; i < expected.length; i++) {
			var scored = Scoring.score(true, 0, LIMIT_MS, streak);
			assertThat(scored.points()).as("answer %d", i + 1).isEqualTo(expected[i]);
			assertThat(scored.streak()).isEqualTo(i + 1);
			streak = scored.streak();
		}
	}

	@Test
	void multiplierCapsAt1Point5() {
		assertThat(Scoring.score(true, 0, LIMIT_MS, 9).points()).isEqualTo(1500);
	}

	@Test
	void timeoutCountsAsWrongAndResetsStreak() {
		assertThat(Scoring.timeout()).isEqualTo(new Scoring.Scored(0, 0));
	}
}
