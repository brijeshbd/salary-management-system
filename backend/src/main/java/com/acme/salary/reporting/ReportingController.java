package com.acme.salary.reporting;

import com.acme.salary.reporting.dto.CurrencyTotal;
import com.acme.salary.reporting.dto.GroupSalarySummary;
import com.acme.salary.reporting.dto.PayDistributionBucket;
import com.acme.salary.reporting.dto.RecentRaise;
import com.acme.salary.salary.Currency;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportingController {

    private final ReportingService reportingService;

    @GetMapping("/summary/by-department")
    public List<GroupSalarySummary> summaryByDepartment() {
        return reportingService.summaryByDepartment();
    }

    @GetMapping("/summary/by-country")
    public List<GroupSalarySummary> summaryByCountry() {
        return reportingService.summaryByCountry();
    }

    @GetMapping("/summary/by-grade")
    public List<GroupSalarySummary> summaryByGrade() {
        return reportingService.summaryByGrade();
    }

    @GetMapping("/headcount-cost")
    public List<CurrencyTotal> headcountCost() {
        return reportingService.headcountAndCostByCurrency();
    }

    @GetMapping("/pay-distribution")
    public List<PayDistributionBucket> payDistribution(@RequestParam Currency currency) {
        return reportingService.payDistribution(currency);
    }

    @GetMapping("/raises")
    public List<RecentRaise> raises(@RequestParam LocalDate since) {
        return reportingService.raisesSince(since);
    }
}
