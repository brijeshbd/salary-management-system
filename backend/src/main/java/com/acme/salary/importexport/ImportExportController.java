package com.acme.salary.importexport;

import com.acme.salary.employee.Country;
import com.acme.salary.employee.EmployeeSearchCriteria;
import com.acme.salary.employee.EmployeeService;
import com.acme.salary.employee.JobGrade;
import com.acme.salary.importexport.dto.ImportSummary;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class ImportExportController {

    /** Large enough to cover the whole org in one export; see docs/tradeoffs.md re: no pagination
     * needed for CSV export at this scale. */
    private static final int EXPORT_PAGE_SIZE = 100_000;

    private final CsvImportService csvImportService;
    private final CsvExportService csvExportService;
    private final EmployeeService employeeService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportSummary importEmployees(@RequestParam("file") MultipartFile file) {
        return csvImportService.importEmployees(file);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportEmployees(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Country country,
            @RequestParam(required = false) JobGrade jobGrade,
            @RequestParam(required = false) BigDecimal minSalary,
            @RequestParam(required = false) BigDecimal maxSalary,
            @RequestParam(required = false) Boolean active) {
        var criteria = new EmployeeSearchCriteria(search, department, country, jobGrade, minSalary, maxSalary, active);
        var page = employeeService.search(criteria, PageRequest.of(0, EXPORT_PAGE_SIZE));
        String csv = csvExportService.exportEmployees(page.content());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"employees.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
