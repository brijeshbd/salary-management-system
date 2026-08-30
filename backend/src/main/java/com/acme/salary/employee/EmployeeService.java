package com.acme.salary.employee;

import com.acme.salary.common.PageResponse;
import com.acme.salary.common.exception.ResourceNotFoundException;
import com.acme.salary.employee.dto.CurrentSalary;
import com.acme.salary.employee.dto.EmployeeCreateRequest;
import com.acme.salary.employee.dto.EmployeeResponse;
import com.acme.salary.employee.dto.EmployeeUpdateRequest;
import com.acme.salary.salary.CurrentSalaryRow;
import com.acme.salary.salary.SalaryChangeReason;
import com.acme.salary.salary.SalaryRecord;
import com.acme.salary.salary.SalaryRecordRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final SalaryRecordRepository salaryRecordRepository;
    private final EmployeeMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public EmployeeResponse create(EmployeeCreateRequest request) {
        String employeeCode = nextEmployeeCode();
        Employee employee = employeeRepository.save(mapper.toEntity(request, employeeCode));

        SalaryRecord initialSalary = SalaryRecord.builder()
                .employee(employee)
                .baseSalary(request.baseSalary())
                .currency(request.currency())
                .effectiveDate(request.effectiveDate())
                .reason(SalaryChangeReason.INITIAL)
                .build();
        salaryRecordRepository.save(initialSalary);

        return mapper.toResponse(employee, mapper.toCurrentSalary(initialSalary));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getById(Long id) {
        Employee employee = findEmployeeOrThrow(id);
        return mapper.toResponse(employee, currentSalaryFor(id));
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
        Employee employee = findEmployeeOrThrow(id);
        mapper.applyUpdate(employee, request);
        return mapper.toResponse(employee, currentSalaryFor(id));
    }

    @Transactional
    public void deactivate(Long id) {
        Employee employee = findEmployeeOrThrow(id);
        employee.setActive(false);
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> list(Pageable pageable) {
        Page<Employee> page = employeeRepository.findAll(pageable);
        List<Long> ids = page.getContent().stream().map(Employee::getId).toList();
        Map<Long, CurrentSalaryRow> currentSalaries = salaryRecordRepository.findCurrentSalaries(ids).stream()
                .collect(Collectors.toMap(CurrentSalaryRow::getEmployeeId, Function.identity()));

        return PageResponse.of(
                page, employee -> mapper.toResponse(employee, mapper.toCurrentSalary(currentSalaries.get(employee.getId()))));
    }

    private CurrentSalary currentSalaryFor(Long employeeId) {
        return salaryRecordRepository
                .findFirstByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                        employeeId, LocalDate.now())
                .map(mapper::toCurrentSalary)
                .orElse(null);
    }

    private Employee findEmployeeOrThrow(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }

    private String nextEmployeeCode() {
        Long next = jdbcTemplate.queryForObject("SELECT nextval('employee_code_seq')", Long.class);
        return "EMP-%06d".formatted(next);
    }
}
