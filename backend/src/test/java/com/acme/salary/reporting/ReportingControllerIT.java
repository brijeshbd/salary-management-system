package com.acme.salary.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@WithMockUser(username = "admin@acme.com", roles = "HR_MANAGER")
class ReportingControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    private long engineeringUsdRaiseCandidateId;

    @BeforeEach
    void seedFixtureEmployees() throws Exception {
        // Engineering / US / USD: 100k, 200k, 300k -> avg=200k, median=200k, total=600k
        createEmployee("A", "One", "Engineering", "US", "IC2", "100000.00", "USD", "2025-01-01");
        engineeringUsdRaiseCandidateId = createEmployee("A", "Two", "Engineering", "US", "IC3", "200000.00", "USD", "2025-01-01");
        createEmployee("A", "Three", "Engineering", "US", "IC4", "300000.00", "USD", "2025-01-01");
        // Sales / GB / GBP: 40k, 60k -> avg=50k, median=50k, total=100k
        createEmployee("B", "One", "Sales", "GB", "IC1", "40000.00", "GBP", "2025-01-01");
        createEmployee("B", "Two", "Sales", "GB", "IC2", "60000.00", "GBP", "2025-01-01");
        // Engineering / DE / EUR: single employee, distinct currency from the USD Engineering group
        createEmployee("C", "One", "Engineering", "DE", "M1", "90000.00", "EUR", "2025-01-01");
    }

    private long createEmployee(
            String first, String last, String department, String country, String grade, String salary, String currency, String date)
            throws Exception {
        String payload =
                """
                {
                  "firstName": "%s", "lastName": "%s", "department": "%s",
                  "country": "%s", "jobGrade": "%s", "baseSalary": %s,
                  "currency": "%s", "effectiveDate": "%s"
                }
                """
                        .formatted(first, last, department, country, grade, salary, currency, date);
        String response = mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    @Test
    void summaryByDepartment_neverMixesCurrencies_andComputesCorrectStats() throws Exception {
        mockMvc.perform(get("/api/reports/summary/by-department"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)) // Engineering/USD, Engineering/EUR, Sales/GBP
                .andExpect(jsonPath("$[?(@.group=='Engineering' && @.currency=='USD')].headcount").value(3))
                .andExpect(jsonPath("$[?(@.group=='Engineering' && @.currency=='USD')].avgSalary").value(200000.00))
                .andExpect(jsonPath("$[?(@.group=='Engineering' && @.currency=='USD')].medianSalary").value(200000.00))
                .andExpect(jsonPath("$[?(@.group=='Engineering' && @.currency=='USD')].totalCost").value(600000.00))
                .andExpect(jsonPath("$[?(@.group=='Engineering' && @.currency=='EUR')].headcount").value(1))
                .andExpect(jsonPath("$[?(@.group=='Sales' && @.currency=='GBP')].avgSalary").value(50000.00));
    }

    @Test
    void summaryByCountry_groupsByCountry() throws Exception {
        mockMvc.perform(get("/api/reports/summary/by-country"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.group=='US')].headcount").value(3))
                .andExpect(jsonPath("$[?(@.group=='GB')].headcount").value(2))
                .andExpect(jsonPath("$[?(@.group=='DE')].headcount").value(1));
    }

    @Test
    void summaryByGrade_groupsByGrade() throws Exception {
        mockMvc.perform(get("/api/reports/summary/by-grade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.group=='IC2' && @.currency=='USD')].headcount").value(1));
    }

    @Test
    void headcountCost_totalsPerCurrency_neverMixed() throws Exception {
        mockMvc.perform(get("/api/reports/headcount-cost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.currency=='USD')].headcount").value(3))
                .andExpect(jsonPath("$[?(@.currency=='USD')].totalCost").value(600000.00))
                .andExpect(jsonPath("$[?(@.currency=='GBP')].totalCost").value(100000.00))
                .andExpect(jsonPath("$[?(@.currency=='EUR')].totalCost").value(90000.00));
    }

    @Test
    void payDistribution_bucketsAllSalariesForOneCurrency() throws Exception {
        mockMvc.perform(get("/api/reports/pay-distribution").param("currency", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].rangeStart").value(100000.00));
    }

    @Test
    void payDistribution_currencyWithNoEmployees_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/reports/pay-distribution").param("currency", "SGD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void raises_filtersOnCurrentSalaryEffectiveDate() throws Exception {
        // All fixture employees have effectiveDate=2025-01-01, so a `since` in early 2025 includes
        // everyone and a `since` after that excludes everyone - until we add a later raise below.
        mockMvc.perform(get("/api/reports/raises").param("since", "2024-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));

        mockMvc.perform(get("/api/reports/raises").param("since", "2026-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        String raisePayload =
                """
                { "baseSalary": 210000.00, "currency": "USD", "effectiveDate": "2026-06-01", "reason": "RAISE" }
                """;
        mockMvc.perform(post("/api/employees/{id}/salary-history", engineeringUsdRaiseCandidateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(raisePayload))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/reports/raises").param("since", "2026-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lastName").value("Two"))
                .andExpect(jsonPath("$[0].newSalary").value(210000.00))
                .andExpect(jsonPath("$[0].reason").value("RAISE"));
    }

    @Test
    void exportSummaryByDepartment_returnsCsvWithHeaderAndDataRows() throws Exception {
        String csv = mockMvc.perform(get("/api/reports/summary/by-department/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("pay-by-department.csv")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(csv).startsWith("department,currency,headcount,avgSalary,medianSalary,totalCost");
        assertThat(csv).contains("Engineering,USD,3,200000.00,200000.00,600000.00");
        assertThat(csv).contains("Sales,GBP,2,50000.00,50000.00,100000.00");
    }

    @Test
    void exportRaises_reflectsTheSameSinceFilterAsTheJsonEndpoint() throws Exception {
        String emptyCsv = mockMvc.perform(get("/api/reports/raises/export").param("since", "2026-01-01"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("raises-since-2026-01-01.csv")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(emptyCsv.strip()).isEqualTo("employeeCode,firstName,lastName,department,newSalary,currency,effectiveDate,reason");

        String raisePayload =
                """
                { "baseSalary": 210000.00, "currency": "USD", "effectiveDate": "2026-06-01", "reason": "RAISE" }
                """;
        mockMvc.perform(post("/api/employees/{id}/salary-history", engineeringUsdRaiseCandidateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(raisePayload))
                .andExpect(status().isCreated());

        String csv = mockMvc.perform(get("/api/reports/raises/export").param("since", "2026-01-01"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(csv).contains("Two,Engineering,210000.00,USD,2026-06-01,RAISE");
    }
}
