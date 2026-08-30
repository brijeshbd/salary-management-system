package com.acme.salary.employee.dto;

import com.acme.salary.employee.Country;
import com.acme.salary.employee.JobGrade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Profile-only update - salary changes go through the salary-history endpoint (added in a
 * later milestone) so every salary change is preserved as history, never overwritten in place. */
public record EmployeeUpdateRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 50) String department,
        @NotNull Country country,
        @NotNull JobGrade jobGrade) {}
