package com.acme.salary.seed;

import com.acme.salary.employee.Country;
import com.acme.salary.employee.JobGrade;
import com.acme.salary.salary.Currency;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reference distributions used only by {@link DataSeeder} to generate realistic-looking synthetic
 * data. Weights and salary bands are illustrative, not real market data.
 */
final class SeedReferenceData {

    private SeedReferenceData() {}

    /** Department name -> relative weight. Engineering/Sales sized larger, matching a typical
     * org shape rather than an even split across departments. */
    static final Map<String, Integer> DEPARTMENT_WEIGHTS = new LinkedHashMap<>();

    static {
        DEPARTMENT_WEIGHTS.put("Engineering", 25);
        DEPARTMENT_WEIGHTS.put("Sales", 20);
        DEPARTMENT_WEIGHTS.put("Customer Support", 12);
        DEPARTMENT_WEIGHTS.put("Marketing", 10);
        DEPARTMENT_WEIGHTS.put("Operations", 10);
        DEPARTMENT_WEIGHTS.put("Finance", 8);
        DEPARTMENT_WEIGHTS.put("Product", 6);
        DEPARTMENT_WEIGHTS.put("HR", 4);
        DEPARTMENT_WEIGHTS.put("Legal", 3);
        DEPARTMENT_WEIGHTS.put("Design", 2);
    }

    /** Country -> relative weight. HQ country (US) sized largest. */
    static final Map<Country, Integer> COUNTRY_WEIGHTS = new LinkedHashMap<>();

    static {
        COUNTRY_WEIGHTS.put(Country.US, 35);
        COUNTRY_WEIGHTS.put(Country.IN, 25);
        COUNTRY_WEIGHTS.put(Country.GB, 15);
        COUNTRY_WEIGHTS.put(Country.DE, 10);
        COUNTRY_WEIGHTS.put(Country.CA, 8);
        COUNTRY_WEIGHTS.put(Country.AU, 4);
        COUNTRY_WEIGHTS.put(Country.SG, 3);
    }

    /** Each country pays in exactly one currency in v1 (no multi-currency employees). */
    static final Map<Country, Currency> COUNTRY_CURRENCY = Map.of(
            Country.US, Currency.USD,
            Country.GB, Currency.GBP,
            Country.IN, Currency.INR,
            Country.DE, Currency.EUR,
            Country.CA, Currency.CAD,
            Country.AU, Currency.AUD,
            Country.SG, Currency.SGD);

    /** Anchor annual base salary for grade IC1, in the country's own local currency - not FX
     * conversions of one figure, so each country's numbers look plausible in their own currency
     * (e.g. INR figures are naturally larger in magnitude than GBP ones). */
    static final Map<Country, Double> COUNTRY_BASE_IC1_SALARY = Map.of(
            Country.US, 65_000d,
            Country.GB, 40_000d,
            Country.IN, 900_000d,
            Country.DE, 48_000d,
            Country.CA, 68_000d,
            Country.AU, 72_000d,
            Country.SG, 55_000d);

    /** Job grade -> multiplier applied to the country's IC1 anchor. Individual-contributor and
     * management ladders overlap in pay (a senior IC can out-earn a first-line manager), which
     * is realistic and also means grade alone doesn't perfectly predict pay in reports. */
    static final Map<JobGrade, Double> GRADE_MULTIPLIER = new LinkedHashMap<>();

    static {
        GRADE_MULTIPLIER.put(JobGrade.IC1, 1.0);
        GRADE_MULTIPLIER.put(JobGrade.IC2, 1.25);
        GRADE_MULTIPLIER.put(JobGrade.IC3, 1.55);
        GRADE_MULTIPLIER.put(JobGrade.IC4, 1.9);
        GRADE_MULTIPLIER.put(JobGrade.IC5, 2.3);
        GRADE_MULTIPLIER.put(JobGrade.IC6, 2.8);
        GRADE_MULTIPLIER.put(JobGrade.M1, 2.5);
        GRADE_MULTIPLIER.put(JobGrade.M2, 3.0);
        GRADE_MULTIPLIER.put(JobGrade.M3, 3.6);
        GRADE_MULTIPLIER.put(JobGrade.M4, 4.3);
    }

    /** Job grade -> relative weight, skewed toward mid grades like a real org pyramid (few at
     * the very top or very bottom). */
    static final Map<JobGrade, Integer> GRADE_WEIGHTS = new LinkedHashMap<>();

    static {
        GRADE_WEIGHTS.put(JobGrade.IC1, 10);
        GRADE_WEIGHTS.put(JobGrade.IC2, 20);
        GRADE_WEIGHTS.put(JobGrade.IC3, 22);
        GRADE_WEIGHTS.put(JobGrade.IC4, 16);
        GRADE_WEIGHTS.put(JobGrade.IC5, 8);
        GRADE_WEIGHTS.put(JobGrade.IC6, 4);
        GRADE_WEIGHTS.put(JobGrade.M1, 10);
        GRADE_WEIGHTS.put(JobGrade.M2, 6);
        GRADE_WEIGHTS.put(JobGrade.M3, 3);
        GRADE_WEIGHTS.put(JobGrade.M4, 1);
    }
}
