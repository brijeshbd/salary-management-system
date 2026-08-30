package com.acme.salary.employee;

import java.math.BigDecimal;

/**
 * All fields optional (null = "don't filter on this"). A criteria with every field null is
 * equivalent to "list everyone" - {@link EmployeeService#search} doesn't special-case that, the
 * same query just has no-op conditions.
 */
public record EmployeeSearchCriteria(
        String search,
        String department,
        Country country,
        JobGrade jobGrade,
        BigDecimal minSalary,
        BigDecimal maxSalary,
        Boolean active) {}
