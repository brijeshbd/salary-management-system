package com.acme.salary.salary.dto;

import com.acme.salary.salary.Currency;
import com.acme.salary.salary.SalaryChangeReason;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SalaryRecordResponse(
        Long id,
        BigDecimal baseSalary,
        Currency currency,
        LocalDate effectiveDate,
        SalaryChangeReason reason,
        Instant createdAt) {}
