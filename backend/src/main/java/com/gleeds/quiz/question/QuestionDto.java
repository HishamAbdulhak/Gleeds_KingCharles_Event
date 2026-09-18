package com.gleeds.quiz.question;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * API shape of a question, both directions. {@code id} is ignored on input; {@code timeLimitSec} defaults to 20
 * and {@code active} to true when omitted.
 */
public record QuestionDto(
		Long id,
		@NotBlank String text,
		@NotBlank String optionA,
		@NotBlank String optionB,
		@NotBlank String optionC,
		@NotBlank String optionD,
		@NotNull @Min(0) @Max(3) Integer correctOption,
		@Min(5) @Max(120) Integer timeLimitSec,
		String category,
		Boolean active) {
}
