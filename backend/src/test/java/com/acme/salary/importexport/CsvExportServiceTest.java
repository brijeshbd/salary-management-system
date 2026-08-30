package com.acme.salary.importexport;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.employee.Country;
import com.acme.salary.employee.JobGrade;
import com.acme.salary.employee.dto.CurrentSalary;
import com.acme.salary.employee.dto.EmployeeResponse;
import com.acme.salary.salary.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvExportServiceTest {

    private final CsvExportService service = new CsvExportService();

    @Test
    void writesHeaderAndOneRowPerEmployee() {
        EmployeeResponse employee = new EmployeeResponse(
                1L,
                "EMP-000001",
                "Ada",
                "Lovelace",
                "Engineering",
                Country.US,
                JobGrade.IC3,
                true,
                new CurrentSalary(new BigDecimal("95000.00"), Currency.USD, LocalDate.of(2025, 1, 1)),
                Instant.now(),
                Instant.now());

        String csv = service.exportEmployees(List.of(employee));
        List<String> lines = csv.lines().toList();

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).isEqualTo(
                "employeeCode,firstName,lastName,department,country,jobGrade,active,currentSalaryAmount,currentSalaryCurrency,currentSalaryEffectiveDate");
        assertThat(lines.get(1)).isEqualTo("EMP-000001,Ada,Lovelace,Engineering,US,IC3,true,95000.00,USD,2025-01-01");
    }

    @Test
    void employeeWithNoCurrentSalary_leavesSalaryColumnsBlank_ratherThanErroring() {
        EmployeeResponse employee = new EmployeeResponse(
                2L, "EMP-000002", "No", "Salary", "HR", Country.GB, JobGrade.IC1, false, null, Instant.now(), Instant.now());

        String csv = service.exportEmployees(List.of(employee));
        List<String> lines = csv.lines().toList();

        assertThat(lines.get(1)).isEqualTo("EMP-000002,No,Salary,HR,GB,IC1,false,,,");
    }

    @Test
    void emptyList_producesHeaderOnly() {
        String csv = service.exportEmployees(List.of());

        assertThat(csv.lines().toList()).hasSize(1);
    }
}
