package com.gleeds.quiz.question;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

/** Question Bank CRUD. Admin-only via SecurityConfig's /api/admin/** rule. */
@RestController
@RequestMapping("/api/admin/questions")
public class QuestionController {

	private final QuestionRepository repo;
	private final QuestionImportService importer;

	QuestionController(QuestionRepository repo, QuestionImportService importer) {
		this.repo = repo;
		this.importer = importer;
	}

	@GetMapping
	List<QuestionDto> list() {
		return repo.findAll(Sort.by("id")).stream().map(Question::toDto).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	QuestionDto create(@Valid @RequestBody QuestionDto dto) {
		return repo.save(new Question(dto)).toDto();
	}

	@PutMapping("/{id}")
	QuestionDto update(@PathVariable long id, @Valid @RequestBody QuestionDto dto) {
		var question = find(id);
		question.apply(dto);
		return repo.save(question).toDto();
	}

	/** A question already played in a Game is deactivated instead, so history keeps its row. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional   // the native delete and the dirty-checked deactivate share one transaction
	void delete(@PathVariable long id) {
		if (repo.deleteIfUnreferenced(id) == 0) {
			find(id).deactivate();
		}
	}

	/** Multipart {@code file}: CSV or XLSX. Valid rows are inserted; the rest come back as {row, message}. */
	@PostMapping("/import")
	QuestionImportService.Result importFile(@RequestParam MultipartFile file) {
		return importer.importFile(file);
	}

	private Question find(long id) {
		return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}
}
