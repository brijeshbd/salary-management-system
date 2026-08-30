package com.acme.salary.employee;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@code @Transactional} here (unlike the other IT classes) wraps each test method - including
 * its {@code @BeforeEach} fixture data - in a transaction that rolls back afterward. Without it,
 * fixture employees from earlier test methods would accumulate in the shared Testcontainers
 * Postgres instance and break these tests' exact-count assertions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@WithMockUser(username = "admin@acme.com", roles = "HR_MANAGER")
class EmployeeSearchControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void seedFixtureEmployees() throws Exception {
        createEmployee("Alice", "Anders", "Engineering", "US", "IC3", "95000.00", "USD");
        createEmployee("Bob", "Baker", "Sales", "GB", "IC1", "35000.00", "GBP");
        createEmployee("Carol", "Chen", "Engineering", "IN", "M1", "2500000.00", "INR");
    }

    private void createEmployee(
            String first, String last, String department, String country, String grade, String salary, String currency)
            throws Exception {
        String payload =
                """
                {
                  "firstName": "%s", "lastName": "%s", "department": "%s",
                  "country": "%s", "jobGrade": "%s", "baseSalary": %s,
                  "currency": "%s", "effectiveDate": "2025-01-01"
                }
                """
                        .formatted(first, last, department, country, grade, salary, currency);
        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
    }

    @Test
    void filterByDepartment_returnsOnlyMatchingEmployees() throws Exception {
        mockMvc.perform(get("/api/employees").param("department", "Engineering"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath(
                        "$.content[*].department",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("Engineering"))));
    }

    @Test
    void filterByCountry_returnsOnlyMatchingEmployees() throws Exception {
        mockMvc.perform(get("/api/employees").param("country", "GB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Baker"));
    }

    @Test
    void searchByName_matchesSubstringCaseInsensitively() throws Exception {
        mockMvc.perform(get("/api/employees").param("search", "chen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Carol"));
    }

    @Test
    void filterBySalaryRange_appliesToCurrentSalary() throws Exception {
        mockMvc.perform(get("/api/employees").param("minSalary", "50000").param("maxSalary", "150000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Anders"));
    }

    @Test
    void combinedFilters_areAllApplied() throws Exception {
        mockMvc.perform(get("/api/employees")
                        .param("department", "Engineering")
                        .param("country", "IN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Chen"));
    }

    @Test
    void noFilters_returnsEveryone() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }
}
