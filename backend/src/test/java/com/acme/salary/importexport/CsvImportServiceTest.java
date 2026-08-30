package com.acme.salary.importexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.salary.employee.Country;
import com.acme.salary.employee.EmployeeService;
import com.acme.salary.employee.JobGrade;
import com.acme.salary.employee.dto.EmployeeResponse;
import com.acme.salary.importexport.dto.ImportSummary;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class CsvImportServiceTest {

    private EmployeeService employeeService;
    private CsvImportService service;

    @BeforeEach
    void setUp() {
        employeeService = mock(EmployeeService.class);
        service = new CsvImportService(employeeService);
    }

    @Test
    void allValidRows_areAllCreated_andSummaryReportsFullSuccess() {
        when(employeeService.create(any())).thenReturn(dummyResponse());
        String csv =
                """
                firstName,lastName,department,country,jobGrade,baseSalary,currency,effectiveDate
                Ada,Lovelace,Engineering,US,IC3,95000.00,USD,2025-01-01
                Grace,Hopper,Engineering,US,IC5,150000.00,USD,2020-01-01
                """;

        ImportSummary summary = service.importEmployees(csvFile(csv));

        assertThat(summary.totalRows()).isEqualTo(2);
        assertThat(summary.succeeded()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(0);
        assertThat(summary.errors()).isEmpty();
        verify(employeeService, times(2)).create(any());
    }

    @Test
    void invalidRows_areSkippedWithMessages_validRowsStillImport() {
        when(employeeService.create(any())).thenReturn(dummyResponse());
        String csv =
                """
                firstName,lastName,department,country,jobGrade,baseSalary,currency,effectiveDate
                Ada,Lovelace,Engineering,US,IC3,95000.00,USD,2025-01-01
                ,Missing,Engineering,US,IC3,95000.00,USD,2025-01-01
                Bad,Country,Engineering,ZZ,IC3,95000.00,USD,2025-01-01
                Bad,Salary,Engineering,US,IC3,not-a-number,USD,2025-01-01
                Bad,Date,Engineering,US,IC3,95000.00,USD,not-a-date
                Future,Dated,Engineering,US,IC3,95000.00,USD,2099-01-01
                """;

        ImportSummary summary = service.importEmployees(csvFile(csv));

        assertThat(summary.totalRows()).isEqualTo(6);
        assertThat(summary.succeeded()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(5);
        assertThat(summary.errors()).hasSize(5);
        assertThat(summary.errors().get(0).row()).isEqualTo(2); // first data row = 1
        assertThat(summary.errors().get(0).message()).contains("firstName is required");
        assertThat(summary.errors().get(1).message()).contains("invalid country");
        assertThat(summary.errors().get(2).message()).contains("invalid baseSalary");
        assertThat(summary.errors().get(3).message()).contains("invalid effectiveDate");
        assertThat(summary.errors().get(4).message()).contains("cannot be in the future");
        verify(employeeService, times(1)).create(any());
    }

    @Test
    void missingRequiredColumn_failsFastWithoutProcessingAnyRow() {
        String csv =
                """
                firstName,lastName,department
                Ada,Lovelace,Engineering
                """;

        ImportSummary summary = service.importEmployees(csvFile(csv));

        assertThat(summary.errors()).hasSize(1);
        assertThat(summary.errors().get(0).message()).contains("Missing required column");
        verify(employeeService, times(0)).create(any());
    }

    private EmployeeResponse dummyResponse() {
        return new EmployeeResponse(
                1L, "EMP-000001", "First", "Last", "Engineering", Country.US, JobGrade.IC3, true, null, Instant.now(), Instant.now());
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "employees.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
