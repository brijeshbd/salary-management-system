package com.acme.salary.reporting.dto;

import com.acme.salary.salary.Currency;
import com.acme.salary.salary.SalaryChangeReason;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RecentRaise(
        String employeeCode,
        String firstName,
        String lastName,
        String department,
        BigDecimal newSalary,
        Currency currency,
        LocalDate effectiveDate,
        SalaryChangeReason reason) {}
