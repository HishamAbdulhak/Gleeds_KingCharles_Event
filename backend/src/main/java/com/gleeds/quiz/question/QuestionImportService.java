package com.gleeds.quiz.question;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Imports the client's sheet: header {@code text,a,b,c,d,correct,time_limit,category} (case-insensitive, last two
 * optional), {@code correct} as A–D. Row numbers in errors are the sheet's own (header = 1).
 */
@Service
public class QuestionImportService {

	public record RowError(int row, String message) {
	}

	public record Result(int imported, List<RowError> errors) {
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

	private final QuestionRepository repo;
	private final Validator validator;

	QuestionImportService(QuestionRepository repo, Validator validator) {
		this.repo = repo;
		this.validator = validator;
	}

	@Transactional
	public Result importFile(MultipartFile file) {
		var rows = read(file).stream().filter(r -> !r.isBlank()).toList();
		if (rows.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty");
		}
		var header = rows.get(0).cells().stream().map(h -> h.toLowerCase().replace(BOM, "").trim()).toList();
		var missing = REQUIRED.stream().filter(c -> !header.contains(c)).toList();
		if (!missing.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing column(s): " + String.join(", ", missing));
		}
		Map<String, Integer> columnIndex = IntStream.range(0, header.size()).boxed()
				.collect(Collectors.toMap(header::get, i -> i, (first, dup) -> first));

		var valid = new ArrayList<Question>();
		var errors = new ArrayList<RowError>();
		for (var row : rows.subList(1, rows.size())) {
			var problems = new ArrayList<String>();
			Function<String, String> cell = column -> row.cell(columnIndex.getOrDefault(column, -1));

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
					.map(QuestionImportService::describe).sorted().forEach(problems::add);

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
		var field = v.getPropertyPath().toString();
		var column = switch (field) {
			case "optionA", "optionB", "optionC", "optionD" -> field.substring(6).toLowerCase();
			case "timeLimitSec" -> "time_limit";
			default -> field;
		};
		return column + " " + v.getMessage();
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
