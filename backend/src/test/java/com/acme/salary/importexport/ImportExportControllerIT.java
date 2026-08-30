package com.acme.salary.importexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@WithMockUser(username = "admin@acme.com", roles = "HR_MANAGER")
class ImportExportControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void importThenExport_roundTripsCleanly() throws Exception {
        String csv =
                """
                firstName,lastName,department,country,jobGrade,baseSalary,currency,effectiveDate
                Ada,Lovelace,Engineering,US,IC3,95000.00,USD,2025-01-01
                Bad,Row,Engineering,ZZ,IC3,95000.00,USD,2025-01-01
                Grace,Hopper,Engineering,US,IC5,150000.00,USD,2020-01-01
                """;
        MockMultipartFile file =
                new MockMultipartFile("file", "employees.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/employees/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.succeeded").value(2))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.errors[0].row").value(2))
                .andExpect(jsonPath("$.errors[0].message", org.hamcrest.Matchers.containsString("invalid country")));

        String csvExport = mockMvc.perform(get("/api/employees/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("employees.csv")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> lines = csvExport.lines().toList();
        assertThat(lines.get(0)).startsWith("employeeCode,firstName,lastName");
        assertThat(lines).hasSize(3); // header + Ada + Grace, the invalid row was never created
        assertThat(csvExport).contains("Lovelace").contains("Hopper").doesNotContain("Bad,Row");
    }

    @Test
    void export_respectsSearchFilters() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "e.csv",
                "text/csv",
                """
                firstName,lastName,department,country,jobGrade,baseSalary,currency,effectiveDate
                Ada,Lovelace,Engineering,US,IC3,95000.00,USD,2025-01-01
                Bob,Baker,Sales,GB,IC1,35000.00,GBP,2025-01-01
                """
                        .getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/employees/import").file(file)).andExpect(status().isOk());

        String csvExport = mockMvc.perform(get("/api/employees/export").param("department", "Sales"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(csvExport).contains("Baker").doesNotContain("Lovelace");
    }
}
