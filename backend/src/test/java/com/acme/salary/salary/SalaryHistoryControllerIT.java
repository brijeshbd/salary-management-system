package com.acme.salary.salary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(username = "admin@acme.com", roles = "HR_MANAGER")
class SalaryHistoryControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    private long createEmployee() throws Exception {
        String payload =
                """
                {
                  "firstName": "Marie", "lastName": "Curie", "department": "Engineering",
                  "country": "US", "jobGrade": "IC5", "baseSalary": 120000.00,
                  "currency": "USD", "effectiveDate": "2024-01-01"
                }
                """;
        String response = mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    @Test
    void getHistory_initiallyHasOneRecordFromCreation() throws Exception {
        long id = createEmployee();

        mockMvc.perform(get("/api/employees/{id}/salary-history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reason").value("INITIAL"))
                .andExpect(jsonPath("$[0].baseSalary").value(120000.00));
    }

    @Test
    void addRecord_appearsInHistoryNewestFirst_andEmployeeReflectsNewCurrentSalary() throws Exception {
        long id = createEmployee();

        String raisePayload =
                """
                { "baseSalary": 130000.00, "currency": "USD", "effectiveDate": "2026-06-01", "reason": "RAISE" }
                """;
        mockMvc.perform(post("/api/employees/{id}/salary-history", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(raisePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value("RAISE"));

        mockMvc.perform(get("/api/employees/{id}/salary-history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reason").value("RAISE"))
                .andExpect(jsonPath("$[1].reason").value("INITIAL"));

        mockMvc.perform(get("/api/employees/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSalary.amount").value(130000.00));
    }

    @Test
    void getHistory_whenEmployeeMissing_returns404() throws Exception {
        mockMvc.perform(get("/api/employees/{id}/salary-history", 999_999_999))
                .andExpect(status().isNotFound());
    }
}
