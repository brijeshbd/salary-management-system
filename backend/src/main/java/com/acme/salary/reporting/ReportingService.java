package com.acme.salary.reporting;

import com.acme.salary.reporting.dto.CurrencyTotal;
import com.acme.salary.reporting.dto.GroupSalarySummary;
import com.acme.salary.reporting.dto.PayDistributionBucket;
import com.acme.salary.reporting.dto.RecentRaise;
import com.acme.salary.salary.Currency;
import com.acme.salary.salary.SalaryChangeReason;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * All reports are scoped to active employees and computed against each employee's <em>current</em>
 * salary (latest effective_date <= today) - the same resolution used everywhere else in the app.
 *
 * <p>Grouped reports (by department/grade) never sum across currencies: since FX conversion is
 * explicitly out of scope (docs/tradeoffs.md), a department with employees paid in three
 * currencies produces three rows rather than one meaningless mixed-currency total.
 */
@Service
@RequiredArgsConstructor
public class ReportingService {

    private static final int PAY_DISTRIBUTION_BUCKETS = 10;

    private final JdbcTemplate jdbcTemplate;

    public List<GroupSalarySummary> summaryByDepartment() {
        return summaryByGroup("e.department");
    }

    public List<GroupSalarySummary> summaryByCountry() {
        return summaryByGroup("e.country");
    }

    public List<GroupSalarySummary> summaryByGrade() {
        return summaryByGroup("e.job_grade");
    }

    private List<GroupSalarySummary> summaryByGroup(String groupColumn) {
        String sql =
                """
                SELECT %s AS grp, cs.currency AS currency,
                       count(*) AS headcount,
                       round(avg(cs.base_salary), 2) AS avg_salary,
                       round(percentile_cont(0.5) WITHIN GROUP (ORDER BY cs.base_salary)::numeric, 2) AS median_salary,
                       round(sum(cs.base_salary), 2) AS total_cost
                FROM employee e
                JOIN LATERAL (
                    SELECT sr.base_salary, sr.currency FROM salary_record sr
                    WHERE sr.employee_id = e.id AND sr.effective_date <= CURRENT_DATE
                    ORDER BY sr.effective_date DESC LIMIT 1
                ) cs ON true
                WHERE e.active = true
                GROUP BY %s, cs.currency
                ORDER BY %s, cs.currency
                """
                        .formatted(groupColumn, groupColumn, groupColumn);

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new GroupSalarySummary(
                        rs.getString("grp"),
                        Currency.valueOf(rs.getString("currency")),
                        rs.getLong("headcount"),
                        rs.getBigDecimal("avg_salary"),
                        rs.getBigDecimal("median_salary"),
                        rs.getBigDecimal("total_cost")));
    }

    public List<CurrencyTotal> headcountAndCostByCurrency() {
        String sql =
                """
                SELECT cs.currency AS currency, count(*) AS headcount, round(sum(cs.base_salary), 2) AS total_cost
                FROM employee e
                JOIN LATERAL (
                    SELECT sr.base_salary, sr.currency FROM salary_record sr
                    WHERE sr.employee_id = e.id AND sr.effective_date <= CURRENT_DATE
                    ORDER BY sr.effective_date DESC LIMIT 1
                ) cs ON true
                WHERE e.active = true
                GROUP BY cs.currency
                ORDER BY cs.currency
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new CurrencyTotal(
                        Currency.valueOf(rs.getString("currency")), rs.getLong("headcount"), rs.getBigDecimal("total_cost")));
    }

    public List<PayDistributionBucket> payDistribution(Currency currency) {
        String sql =
                """
                SELECT cs.base_salary
                FROM employee e
                JOIN LATERAL (
                    SELECT sr.base_salary, sr.currency FROM salary_record sr
                    WHERE sr.employee_id = e.id AND sr.effective_date <= CURRENT_DATE
                    ORDER BY sr.effective_date DESC LIMIT 1
                ) cs ON true
                WHERE e.active = true AND cs.currency = ?
                """;
        List<BigDecimal> salaries = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getBigDecimal("base_salary"), currency.name());

        return bucketize(salaries);
    }

    /** Bucketing happens in Java, not SQL: at this scale (a few thousand salaries per currency at
     * most) fetching the raw values and dividing them into equal-width buckets here is simpler
     * and just as fast as an equivalent width_bucket() SQL query, and easier to unit test. */
    private List<PayDistributionBucket> bucketize(List<BigDecimal> salaries) {
        if (salaries.isEmpty()) {
            return List.of();
        }

        BigDecimal min = salaries.stream().min(BigDecimal::compareTo).orElseThrow();
        BigDecimal max = salaries.stream().max(BigDecimal::compareTo).orElseThrow();

        if (min.compareTo(max) == 0) {
            return List.of(new PayDistributionBucket(min, max, salaries.size()));
        }

        BigDecimal range = max.subtract(min);
        BigDecimal bucketWidth = range.divide(BigDecimal.valueOf(PAY_DISTRIBUTION_BUCKETS), 2, RoundingMode.HALF_UP);
        long[] counts = new long[PAY_DISTRIBUTION_BUCKETS];

        for (BigDecimal salary : salaries) {
            int index = salary.subtract(min).divide(bucketWidth, 0, RoundingMode.DOWN).intValue();
            if (index >= PAY_DISTRIBUTION_BUCKETS) index = PAY_DISTRIBUTION_BUCKETS - 1; // max value lands in the last bucket
            counts[index]++;
        }

        List<PayDistributionBucket> buckets = new ArrayList<>(PAY_DISTRIBUTION_BUCKETS);
        for (int i = 0; i < PAY_DISTRIBUTION_BUCKETS; i++) {
            BigDecimal rangeStart = min.add(bucketWidth.multiply(BigDecimal.valueOf(i)));
            BigDecimal rangeEnd = i == PAY_DISTRIBUTION_BUCKETS - 1 ? max : min.add(bucketWidth.multiply(BigDecimal.valueOf(i + 1)));
            buckets.add(new PayDistributionBucket(rangeStart, rangeEnd, counts[i]));
        }
        return buckets;
    }

    public List<RecentRaise> raisesSince(LocalDate since) {
        String sql =
                """
                SELECT e.employee_code, e.first_name, e.last_name, e.department,
                       cs.base_salary, cs.currency, cs.effective_date, cs.reason
                FROM employee e
                JOIN LATERAL (
                    SELECT sr.base_salary, sr.currency, sr.effective_date, sr.reason FROM salary_record sr
                    WHERE sr.employee_id = e.id AND sr.effective_date <= CURRENT_DATE
                    ORDER BY sr.effective_date DESC LIMIT 1
                ) cs ON true
                WHERE e.active = true AND cs.effective_date >= ?
                ORDER BY cs.effective_date DESC
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new RecentRaise(
                        rs.getString("employee_code"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("department"),
                        rs.getBigDecimal("base_salary"),
                        Currency.valueOf(rs.getString("currency")),
                        rs.getObject("effective_date", LocalDate.class),
                        SalaryChangeReason.valueOf(rs.getString("reason"))),
                since);
    }
}
