package com.acme.salary.salary;

import com.acme.salary.salary.dto.SalaryAdjustmentRequest;
import com.acme.salary.salary.dto.SalaryRecordResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees/{employeeId}/salary-history")
@RequiredArgsConstructor
public class SalaryHistoryController {

    private final SalaryHistoryService salaryHistoryService;

    @GetMapping
    public List<SalaryRecordResponse> getHistory(@PathVariable Long employeeId) {
        return salaryHistoryService.getHistory(employeeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalaryRecordResponse addRecord(
            @PathVariable Long employeeId, @Valid @RequestBody SalaryAdjustmentRequest request) {
        return salaryHistoryService.addRecord(employeeId, request);
    }
}
