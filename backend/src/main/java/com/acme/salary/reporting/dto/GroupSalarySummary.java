package com.acme.salary.reporting.dto;

import com.acme.salary.salary.Currency;
import java.math.BigDecimal;

/**
 * Aggregate pay stats for one (group, currency) pair - e.g. one row per department per currency,
 * never mixing currencies into a single sum, since FX conversion is out of scope (see
 * docs/tradeoffs.md). A department with employees paid in three currencies produces three rows.
 */
public record GroupSalarySummary(
        String group, Currency currency, long headcount, BigDecimal avgSalary, BigDecimal medianSalary, BigDecimal totalCost) {}
