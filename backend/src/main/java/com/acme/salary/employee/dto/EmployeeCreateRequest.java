package com.acme.salary.employee.dto;

import com.acme.salary.employee.Country;
import com.acme.salary.employee.JobGrade;
import com.acme.salary.salary.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeCreateRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 50) String department,
        @NotNull Country country,
        @NotNull JobGrade jobGrade,
        @NotNull @DecimalMin(value = "0.01", message = "must be positive") BigDecimal baseSalary,
        @NotNull Currency currency,
        @NotNull @PastOrPresent LocalDate effectiveDate) {}
