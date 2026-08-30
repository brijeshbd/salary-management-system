package com.acme.salary.salary.dto;

import com.acme.salary.salary.Currency;
import com.acme.salary.salary.SalaryChangeReason;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SalaryAdjustmentRequest(
        @NotNull @DecimalMin(value = "0.01", message = "must be positive") BigDecimal baseSalary,
        @NotNull Currency currency,
        @NotNull LocalDate effectiveDate,
        @NotNull SalaryChangeReason reason) {}
