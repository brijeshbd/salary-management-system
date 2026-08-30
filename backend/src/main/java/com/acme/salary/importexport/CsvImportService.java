package com.acme.salary.importexport;

import com.acme.salary.employee.Country;
import com.acme.salary.employee.EmployeeService;
import com.acme.salary.employee.JobGrade;
import com.acme.salary.employee.dto.EmployeeCreateRequest;
import com.acme.salary.importexport.dto.ImportRowError;
import com.acme.salary.importexport.dto.ImportSummary;
import com.acme.salary.salary.Currency;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Imports employees from a CSV with columns: firstName, lastName, department, country, jobGrade,
 * baseSalary, currency, effectiveDate - the same fields {@link EmployeeCreateRequest} needs,
 * since each valid row becomes a new employee with an initial salary.
 *
 * <p>Partial success by design: one malformed row (a real Excel export is rarely perfectly clean)
 * is reported and skipped, not allowed to fail the whole file. Each valid row is created via
 * {@link EmployeeService#create}, which runs its own transaction per call - so rows already
 * created before a later bad row are not rolled back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private static final List<String> REQUIRED_HEADERS =
            List.of("firstName", "lastName", "department", "country", "jobGrade", "baseSalary", "currency", "effectiveDate");

    private final EmployeeService employeeService;

    public ImportSummary importEmployees(MultipartFile file) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .get();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
                CSVParser parser = format.parse(reader)) {

            List<String> missingHeaders = REQUIRED_HEADERS.stream()
                    .filter(h -> !parser.getHeaderNames().contains(h))
                    .toList();
            if (!missingHeaders.isEmpty()) {
                return new ImportSummary(
                        0,
                        0,
                        0,
                        List.of(new ImportRowError(0, "Missing required column(s): " + String.join(", ", missingHeaders))));
            }

            int succeeded = 0;
            List<ImportRowError> errors = new ArrayList<>();

            for (CSVRecord record : parser) {
                int rowNumber = (int) record.getRecordNumber();
                try {
                    EmployeeCreateRequest request = parseRow(record);
                    employeeService.create(request);
                    succeeded++;
                } catch (RowValidationException e) {
                    errors.add(new ImportRowError(rowNumber, e.getMessage()));
                } catch (Exception e) {
                    log.warn("Unexpected error importing CSV row {}", rowNumber, e);
                    errors.add(new ImportRowError(rowNumber, "Unexpected error: " + e.getMessage()));
                }
            }

            return new ImportSummary(succeeded + errors.size(), succeeded, errors.size(), errors);
        } catch (IOException e) {
            return new ImportSummary(0, 0, 0, List.of(new ImportRowError(0, "Could not read file: " + e.getMessage())));
        }
    }

    private EmployeeCreateRequest parseRow(CSVRecord record) {
        List<String> problems = new ArrayList<>();

        String firstName = requireNonBlank(record.get("firstName"), "firstName", problems);
        String lastName = requireNonBlank(record.get("lastName"), "lastName", problems);
        String department = requireNonBlank(record.get("department"), "department", problems);
        Country country = parseEnum(Country.class, record.get("country"), "country", problems);
        JobGrade jobGrade = parseEnum(JobGrade.class, record.get("jobGrade"), "jobGrade", problems);
        Currency currency = parseEnum(Currency.class, record.get("currency"), "currency", problems);
        BigDecimal baseSalary = parseSalary(record.get("baseSalary"), problems);
        LocalDate effectiveDate = parseDate(record.get("effectiveDate"), problems);

        if (!problems.isEmpty()) {
            throw new RowValidationException(String.join("; ", problems));
        }

        return new EmployeeCreateRequest(
                firstName, lastName, department, country, jobGrade, baseSalary, currency, effectiveDate);
    }

    private String requireNonBlank(String value, String field, List<String> problems) {
        if (value == null || value.isBlank()) {
            problems.add(field + " is required");
            return null;
        }
        return value;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field, List<String> problems) {
        if (value == null || value.isBlank()) {
            problems.add(field + " is required");
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            problems.add("invalid " + field + ": " + value);
            return null;
        }
    }

    private BigDecimal parseSalary(String value, List<String> problems) {
        if (value == null || value.isBlank()) {
            problems.add("baseSalary is required");
            return null;
        }
        try {
            BigDecimal salary = new BigDecimal(value.trim());
            if (salary.signum() <= 0) {
                problems.add("baseSalary must be positive");
                return null;
            }
            return salary;
        } catch (NumberFormatException e) {
            problems.add("invalid baseSalary: " + value);
            return null;
        }
    }

    private LocalDate parseDate(String value, List<String> problems) {
        if (value == null || value.isBlank()) {
            problems.add("effectiveDate is required");
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value.trim());
            if (date.isAfter(LocalDate.now())) {
                problems.add("effectiveDate cannot be in the future");
                return null;
            }
            return date;
        } catch (DateTimeParseException e) {
            problems.add("invalid effectiveDate (expected YYYY-MM-DD): " + value);
            return null;
        }
    }

    private static class RowValidationException extends RuntimeException {
        RowValidationException(String message) {
            super(message);
        }
    }
}
