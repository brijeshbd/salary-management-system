package com.acme.salary.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
class EmployeeControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullCrudLifecycle() throws Exception {
        String createPayload =
                """
                {
                  "firstName": "Ada",
                  "lastName": "Lovelace",
                  "department": "Engineering",
                  "country": "US",
                  "jobGrade": "IC3",
                  "baseSalary": 95000.00,
                  "currency": "USD",
                  "effectiveDate": "2026-01-01"
                }
                """;

        String createResponse = mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeCode", containsString("EMP-")))
                .andExpect(jsonPath("$.currentSalary.amount").value(95000.00))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = ((Number) JsonPath.read(createResponse, "$.id")).longValue();

        mockMvc.perform(get("/api/employees/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.currentSalary.currency").value("USD"));

        String updatePayload =
                """
                {
                  "firstName": "Augusta",
                  "lastName": "Lovelace",
                  "department": "Product",
                  "country": "GB",
                  "jobGrade": "IC4"
                }
                """;

        mockMvc.perform(put("/api/employees/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Augusta"))
                .andExpect(jsonPath("$.department").value("Product"))
                .andExpect(jsonPath("$.country").value("GB"))
                // salary is untouched by a profile update
                .andExpect(jsonPath("$.currentSalary.amount").value(95000.00));

        mockMvc.perform(delete("/api/employees/{id}", id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/employees/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void getById_whenMissing_returns404WithErrorBody() throws Exception {
        mockMvc.perform(get("/api/employees/{id}", 999_999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("999999999")));
    }

    @Test
    void create_withMissingRequiredFields_returns400WithFieldErrors() throws Exception {
        String invalidPayload = """
                { "firstName": "" }
                """;

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lastName").exists())
                .andExpect(jsonPath("$.fieldErrors.department").exists());
    }

    @Test
    void list_returnsPaginatedEnvelope() throws Exception {
        for (int i = 0; i < 3; i++) {
            String payload =
                    """
                    {
                      "firstName": "Bulk",
                      "lastName": "Employee%d",
                      "department": "Sales",
                      "country": "DE",
                      "jobGrade": "IC1",
                      "baseSalary": 50000.00,
                      "currency": "EUR",
                      "effectiveDate": "2025-01-01"
                    }
                    """
                            .formatted(i);
            mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isCreated());
        }

        String listResponse = mockMvc.perform(get("/api/employees").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long totalElements = ((Number) JsonPath.read(listResponse, "$.totalElements")).longValue();
        assertThat(totalElements).isGreaterThanOrEqualTo(3);
    }
}
