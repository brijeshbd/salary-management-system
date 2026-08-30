package com.acme.salary.seed;

import com.acme.salary.salary.Currency;
import com.acme.salary.salary.SalaryChangeReason;
import java.math.BigDecimal;
import java.time.LocalDate;

record GeneratedSalaryRecord(
        BigDecimal baseSalary, Currency currency, LocalDate effectiveDate, SalaryChangeReason reason) {}
