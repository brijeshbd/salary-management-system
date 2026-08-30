package com.acme.salary.seed;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Generates realistic synthetic employees + salary history, active only under the {@code seed}
 * profile so it never runs in tests or a normal dev/prod boot. Idempotent - skips entirely if the
 * employee table already has rows, so re-running the profile by accident doesn't duplicate data.
 *
 * <p>Inserts via raw {@link JdbcTemplate} batches rather than JPA {@code save()} in a loop: at
 * 10,000 employees plus ~15,000-40,000 salary records, entity-by-entity saves would mean far more
 * round trips and persistence-context/dirty-checking overhead than this needs.
 */
@Slf4j
@Component
@Profile("seed")
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private static final int CHUNK_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final EmployeeSeedGenerator generator = new EmployeeSeedGenerator();

    @Value("${app.seed.employee-count:10000}")
    private int employeeCount;

    @Override
    public void run(ApplicationArguments args) {
        Integer existing = jdbcTemplate.queryForObject("SELECT count(*) FROM employee", Integer.class);
        if (existing != null && existing > 0) {
            log.info("Seed skipped: employee table already has {} rows.", existing);
            return;
        }

        log.info("Seeding {} employees...", employeeCount);
        long startedAt = System.currentTimeMillis();
        LocalDate today = LocalDate.now();
        int sequenceNumber = 1;

        for (int chunkStart = 0; chunkStart < employeeCount; chunkStart += CHUNK_SIZE) {
            int chunkSize = Math.min(CHUNK_SIZE, employeeCount - chunkStart);
            List<GeneratedEmployee> chunk = new ArrayList<>(chunkSize);
            for (int i = 0; i < chunkSize; i++) {
                chunk.add(generator.generate(sequenceNumber++, today));
            }

            transactionTemplate.executeWithoutResult(status -> insertChunk(chunk));
            log.info("Seeded {}/{} employees", chunkStart + chunkSize, employeeCount);
        }

        log.info("Seeding complete in {} ms.", System.currentTimeMillis() - startedAt);
    }

    private void insertChunk(List<GeneratedEmployee> chunk) {
        List<Long> employeeIds = insertEmployees(chunk);
        insertSalaryHistory(chunk, employeeIds);
    }

    private List<Long> insertEmployees(List<GeneratedEmployee> chunk) {
        StringBuilder sql = new StringBuilder(
                "INSERT INTO employee (employee_code, first_name, last_name, department, country, job_grade, active) VALUES ");
        List<Object> params = new ArrayList<>(chunk.size() * 7);
        for (int i = 0; i < chunk.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("(?, ?, ?, ?, ?, ?, TRUE)");
            GeneratedEmployee e = chunk.get(i);
            params.add(e.employeeCode());
            params.add(e.firstName());
            params.add(e.lastName());
            params.add(e.department());
            params.add(e.country().name());
            params.add(e.jobGrade().name());
        }
        sql.append(" RETURNING id");

        // A single multi-row INSERT ... VALUES ... RETURNING id preserves the input row order in
        // its output in PostgreSQL, which is what lets us zip these ids back up with `chunk` by
        // index below.
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> rs.getLong(1), params.toArray());
    }

    private void insertSalaryHistory(List<GeneratedEmployee> chunk, List<Long> employeeIds) {
        String sql =
                "INSERT INTO salary_record (employee_id, base_salary, currency, effective_date, reason) VALUES (?, ?, ?, ?, ?)";
        List<Object[]> batchArgs = new ArrayList<>();
        for (int i = 0; i < chunk.size(); i++) {
            Long employeeId = employeeIds.get(i);
            for (GeneratedSalaryRecord record : chunk.get(i).salaryHistory()) {
                batchArgs.add(new Object[] {
                    employeeId,
                    record.baseSalary(),
                    record.currency().name(),
                    record.effectiveDate(),
                    record.reason().name()
                });
            }
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }
}
