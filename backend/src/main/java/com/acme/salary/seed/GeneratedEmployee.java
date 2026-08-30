package com.acme.salary.seed;

import com.acme.salary.employee.Country;
import com.acme.salary.employee.JobGrade;
import java.util.List;

record GeneratedEmployee(
        String employeeCode,
        String firstName,
        String lastName,
        String department,
        Country country,
        JobGrade jobGrade,
        List<GeneratedSalaryRecord> salaryHistory) {}
