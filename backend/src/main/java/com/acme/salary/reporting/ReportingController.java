package com.acme.salary.reporting;

import com.acme.salary.reporting.dto.CurrencyTotal;
import com.acme.salary.reporting.dto.GroupSalarySummary;
import com.acme.salary.reporting.dto.PayDistributionBucket;
import com.acme.salary.reporting.dto.RecentRaise;
import com.acme.salary.salary.Currency;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportingController {

    private final ReportingService reportingService;
    private final ReportingCsvService reportingCsvService;

    @GetMapping("/summary/by-department")
    public List<GroupSalarySummary> summaryByDepartment() {
        return reportingService.summaryByDepartment();
    }

    @GetMapping(value = "/summary/by-department/export", produces = "text/csv")
    public ResponseEntity<String> exportSummaryByDepartment() {
        return csvResponse(reportingCsvService.groupSummary(reportingService.summaryByDepartment(), "department"), "pay-by-department.csv");
    }

    @GetMapping("/summary/by-country")
    public List<GroupSalarySummary> summaryByCountry() {
        return reportingService.summaryByCountry();
    }

    @GetMapping(value = "/summary/by-country/export", produces = "text/csv")
    public ResponseEntity<String> exportSummaryByCountry() {
        return csvResponse(reportingCsvService.groupSummary(reportingService.summaryByCountry(), "country"), "pay-by-country.csv");
    }

    @GetMapping("/summary/by-grade")
    public List<GroupSalarySummary> summaryByGrade() {
        return reportingService.summaryByGrade();
    }

    @GetMapping(value = "/summary/by-grade/export", produces = "text/csv")
    public ResponseEntity<String> exportSummaryByGrade() {
        return csvResponse(reportingCsvService.groupSummary(reportingService.summaryByGrade(), "jobGrade"), "pay-by-grade.csv");
    }

    @GetMapping("/headcount-cost")
    public List<CurrencyTotal> headcountCost() {
        return reportingService.headcountAndCostByCurrency();
    }

    @GetMapping(value = "/headcount-cost/export", produces = "text/csv")
    public ResponseEntity<String> exportHeadcountCost() {
        return csvResponse(reportingCsvService.currencyTotals(reportingService.headcountAndCostByCurrency()), "headcount-cost.csv");
    }

    @GetMapping("/pay-distribution")
    public List<PayDistributionBucket> payDistribution(@RequestParam Currency currency) {
        return reportingService.payDistribution(currency);
    }

    @GetMapping(value = "/pay-distribution/export", produces = "text/csv")
    public ResponseEntity<String> exportPayDistribution(@RequestParam Currency currency) {
        return csvResponse(
                reportingCsvService.payDistribution(reportingService.payDistribution(currency)),
                "pay-distribution-%s.csv".formatted(currency));
    }

    @GetMapping("/raises")
    public List<RecentRaise> raises(@RequestParam LocalDate since) {
        return reportingService.raisesSince(since);
    }

    @GetMapping(value = "/raises/export", produces = "text/csv")
    public ResponseEntity<String> exportRaises(@RequestParam LocalDate since) {
        return csvResponse(reportingCsvService.raises(reportingService.raisesSince(since)), "raises-since-%s.csv".formatted(since));
    }

    private ResponseEntity<String> csvResponse(String csv, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(filename))
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
