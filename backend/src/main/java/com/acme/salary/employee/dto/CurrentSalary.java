package com.acme.salary.employee.dto;

import com.acme.salary.salary.Currency;
import java.math.BigDecimal;
import java.time.LocalDate;

/** The resolved "current" salary for one employee - null when an employee somehow has no salary
 * record yet, which shouldn't happen via the API (creation always inserts one) but can't be ruled
 * out entirely (e.g. future-dated-only history). */
public record CurrentSalary(BigDecimal amount, Currency currency, LocalDate effectiveDate) {}
