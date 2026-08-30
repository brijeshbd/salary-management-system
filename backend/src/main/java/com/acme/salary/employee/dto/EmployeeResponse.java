package com.acme.salary.employee.dto;

import com.acme.salary.employee.Country;
import com.acme.salary.employee.JobGrade;
import java.time.Instant;

public record EmployeeResponse(
        Long id,
        String employeeCode,
        String firstName,
        String lastName,
        String department,
        Country country,
        JobGrade jobGrade,
        boolean active,
        CurrentSalary currentSalary,
        Instant createdAt,
        Instant updatedAt) {}
