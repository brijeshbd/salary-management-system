package com.acme.salary.seed;

import com.acme.salary.employee.Country;
import com.acme.salary.employee.JobGrade;
import com.acme.salary.salary.Currency;
import com.acme.salary.salary.SalaryChangeReason;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.datafaker.Faker;

/**
 * Generates a single synthetic {@link GeneratedEmployee} with a realistic salary history, using
 * the distributions in {@link SeedReferenceData}. Not unit tested (see docs/tradeoffs.md) - it's
 * a dev/demo data tool, not core application logic.
 */
final class EmployeeSeedGenerator {

    private final Faker faker = new Faker();

    GeneratedEmployee generate(int sequenceNumber, LocalDate today) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String department = WeightedRandomPicker.pick(SeedReferenceData.DEPARTMENT_WEIGHTS, random);
        Country country = WeightedRandomPicker.pick(SeedReferenceData.COUNTRY_WEIGHTS, random);
        JobGrade jobGrade = WeightedRandomPicker.pick(SeedReferenceData.GRADE_WEIGHTS, random);
        Currency currency = SeedReferenceData.COUNTRY_CURRENCY.get(country);

        return new GeneratedEmployee(
                "ACME-%06d".formatted(sequenceNumber),
                faker.name().firstName(),
                faker.name().lastName(),
                department,
                country,
                jobGrade,
                generateSalaryHistory(country, jobGrade, currency, today, random));
    }

    private List<GeneratedSalaryRecord> generateSalaryHistory(
            Country country, JobGrade jobGrade, Currency currency, LocalDate today, ThreadLocalRandom random) {
        int recordCount = random.nextInt(1, 5); // 1-4 inclusive

        double currentSalary =
                SeedReferenceData.COUNTRY_BASE_IC1_SALARY.get(country)
                        * SeedReferenceData.GRADE_MULTIPLIER.get(jobGrade)
                        * (1 + random.nextDouble(-0.10, 0.10));

        double[] salaries = new double[recordCount];
        salaries[recordCount - 1] = currentSalary;
        for (int i = recordCount - 2; i >= 0; i--) {
            double raisePct = random.nextDouble(0.03, 0.10);
            salaries[i] = salaries[i + 1] / (1 + raisePct);
        }

        LocalDate[] effectiveDates = new LocalDate[recordCount];
        // Spread the *current* record's date over 2 years, not a narrow recent window - otherwise
        // every employee looks like they got a raise "recently," which makes a raises-since-date
        // report meaningless (it would just return everyone for any reasonable cutoff).
        effectiveDates[recordCount - 1] = today.minusMonths(random.nextInt(0, 25));
        for (int i = recordCount - 2; i >= 0; i--) {
            effectiveDates[i] = effectiveDates[i + 1].minusMonths(random.nextInt(9, 19));
        }

        List<GeneratedSalaryRecord> history = new ArrayList<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            SalaryChangeReason reason = i == 0 ? SalaryChangeReason.INITIAL : pickRaiseReason(random);
            BigDecimal amount = BigDecimal.valueOf(salaries[i]).setScale(2, RoundingMode.HALF_UP);
            history.add(new GeneratedSalaryRecord(amount, currency, effectiveDates[i], reason));
        }
        return history;
    }

    private SalaryChangeReason pickRaiseReason(ThreadLocalRandom random) {
        int roll = random.nextInt(100);
        if (roll < 80) return SalaryChangeReason.RAISE;
        if (roll < 90) return SalaryChangeReason.PROMOTION;
        return SalaryChangeReason.ADJUSTMENT;
    }
}
