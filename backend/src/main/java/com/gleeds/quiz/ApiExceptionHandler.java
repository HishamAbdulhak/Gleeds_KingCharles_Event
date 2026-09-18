package com.gleeds.quiz;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Client errors as {@code {"message": "…"}} so the UI can show the reason (Boot's default body omits it). */
@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	Map<String, String> invalidBody(MethodArgumentNotValidException ex) {
		return Map.of("message", ex.getFieldErrors().stream().sorted(Comparator.comparing(FieldError::getField))
				.map(f -> f.getField() + ": " + f.getDefaultMessage()).collect(Collectors.joining(", ")));
	}

	@ExceptionHandler(ResponseStatusException.class)
	ResponseEntity<Map<String, String>> statusException(ResponseStatusException ex) {
		var reason = ex.getReason() == null ? HttpStatus.valueOf(ex.getStatusCode().value()).getReasonPhrase()
				: ex.getReason();
		return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", reason));
	}
}
