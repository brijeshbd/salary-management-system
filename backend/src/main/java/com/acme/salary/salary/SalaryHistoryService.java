package com.acme.salary.salary;

import com.acme.salary.common.exception.ResourceNotFoundException;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.salary.dto.SalaryAdjustmentRequest;
import com.acme.salary.salary.dto.SalaryRecordResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalaryHistoryService {

    private final EmployeeRepository employeeRepository;
    private final SalaryRecordRepository salaryRecordRepository;

    @Transactional(readOnly = true)
    public List<SalaryRecordResponse> getHistory(Long employeeId) {
        requireEmployee(employeeId);
        return salaryRecordRepository.findByEmployeeIdOrderByEffectiveDateDesc(employeeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SalaryRecordResponse addRecord(Long employeeId, SalaryAdjustmentRequest request) {
        Employee employee = requireEmployee(employeeId);

        SalaryRecord record = SalaryRecord.builder()
                .employee(employee)
                .baseSalary(request.baseSalary())
                .currency(request.currency())
                .effectiveDate(request.effectiveDate())
                .reason(request.reason())
                .build();

        return toResponse(salaryRecordRepository.save(record));
    }

    private Employee requireEmployee(Long employeeId) {
        return employeeRepository
                .findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
    }

    private SalaryRecordResponse toResponse(SalaryRecord record) {
        return new SalaryRecordResponse(
                record.getId(),
                record.getBaseSalary(),
                record.getCurrency(),
                record.getEffectiveDate(),
                record.getReason(),
                record.getCreatedAt());
    }
}
