package com.gleeds.quiz.question;

import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;

/** Question Bank CRUD and the CSV/XLSX import. Admin-only via SecurityConfig's /api/admin/** rule. */
@RestController
@RequestMapping("/api/admin/questions")
public class QuestionController {

	private final QuestionRepository repo;
	private final Validator validator;

	QuestionController(QuestionRepository repo, Validator validator) {
		this.repo = repo;
		this.validator = validator;
	}

	@GetMapping
	List<QuestionDto> list() {
		return repo.findAll(Sort.by("id")).stream().map(Question::toDto).toList();
	}

	@PostMapping
	ResponseEntity<QuestionDto> create(@Valid @RequestBody QuestionDto dto) {
		var saved = repo.save(new Question(dto)).toDto();
		return ResponseEntity.created(URI.create("/api/admin/questions/" + saved.id())).body(saved);
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

	private Question find(long id) {
		return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	// --- Import: the client's sheet, header text,a,b,c,d,correct,time_limit,category (case-insensitive, last two
	// optional), correct as A–D. Row numbers in errors are the sheet's own (header = 1). ---

	record RowError(int row, String message) {
	}

	record Result(int imported, List<RowError> errors) {
	}

	/** Sheet row: 1-based number as the Admin sees it, cells in column order. */
	record Row(int number, List<String> cells) {
		String cell(int index) {
			return index >= 0 && index < cells.size() ? cells.get(index) : "";
		}

		boolean isBlank() {
			return cells.stream().allMatch(String::isBlank);
		}
	}

	private static final List<String> REQUIRED = List.of("text", "a", "b", "c", "d", "correct");
	/** Excel writes CSV as UTF-8 with a byte-order mark, which would otherwise glue itself to the first header. */
	private static final String BOM = "﻿";

	/** Multipart {@code file}: CSV or XLSX. Valid rows are inserted; the rest come back as {row, message}. */
	@PostMapping("/import")
	@Transactional
	Result importFile(@RequestParam MultipartFile file) {
		var rows = read(file).stream().filter(r -> !r.isBlank()).toList();
		if (rows.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty");
		}
		var header = rows.get(0).cells().stream().map(h -> h.toLowerCase().replace(BOM, "").trim()).toList();
		var missing = REQUIRED.stream().filter(c -> !header.contains(c)).toList();
		if (!missing.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing column(s): " + String.join(", ", missing));
		}

		var valid = new ArrayList<Question>();
		var errors = new ArrayList<RowError>();
		for (var row : rows.subList(1, rows.size())) {
			var problems = new ArrayList<String>();
			Function<String, String> cell = column -> row.cell(header.indexOf(column));

			var correct = cell.apply("correct").toUpperCase();
			Integer correctOption = correct.length() == 1 && "ABCD".contains(correct) ? "ABCD".indexOf(correct) : null;
			if (correctOption == null) {
				problems.add("correct must be A–D (got '" + cell.apply("correct") + "')");
			}

			Integer timeLimit = null;
			var timeLimitText = cell.apply("time_limit");
			if (!timeLimitText.isBlank()) {
				try {
					timeLimit = Integer.parseInt(timeLimitText);
				} catch (NumberFormatException e) {
					problems.add("time_limit must be a whole number of seconds (got '" + timeLimitText + "')");
				}
			}

			var category = cell.apply("category");
			var dto = new QuestionDto(null, cell.apply("text"), cell.apply("a"), cell.apply("b"), cell.apply("c"),
					cell.apply("d"), correctOption, timeLimit, category.isBlank() ? null : category, null);
			// correctOption is null only when the letter parse above already reported it
			validator.validate(dto).stream().filter(v -> !v.getPropertyPath().toString().equals("correctOption"))
					.map(QuestionController::describe).sorted().forEach(problems::add);

			if (problems.isEmpty()) {
				valid.add(new Question(dto));
			} else {
				errors.add(new RowError(row.number(), String.join("; ", problems)));
			}
		}
		repo.saveAll(valid);
		return new Result(valid.size(), errors);
	}

	/** Sheet column names, not DTO field names: "optionB" → "b". */
	private static String describe(ConstraintViolation<QuestionDto> v) {
		var column = v.getPropertyPath().toString().replaceFirst("^option(.)$", "$1").replace("timeLimitSec", "time_limit");
		return column.toLowerCase() + " " + v.getMessage();
	}

	private static List<Row> read(MultipartFile file) {
		var name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
		try {
			return name.endsWith(".xlsx") ? readXlsx(file) : readCsv(file);
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the file: " + e.getMessage());
		}
	}

	private static List<Row> readCsv(MultipartFile file) throws Exception {
		var format = CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(false).setTrim(true).get();
		try (var parser = format.parse(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
			return parser.stream().map(r -> new Row((int) r.getRecordNumber(), r.toList())).toList();
		}
	}

	private static List<Row> readXlsx(MultipartFile file) throws Exception {
		var rows = new ArrayList<Row>();
		var formatter = new DataFormatter();
		try (var workbook = new XSSFWorkbook(file.getInputStream())) {
			for (var row : workbook.getSheetAt(0)) {
				var cells = IntStream.range(0, row.getLastCellNum())
						.mapToObj(i -> formatter.formatCellValue(row.getCell(i)).trim()).toList();
				rows.add(new Row(row.getRowNum() + 1, cells));
			}
		}
		return rows;
	}
}
