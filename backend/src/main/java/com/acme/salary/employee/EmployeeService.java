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
import org.springframework.data.domain.PageRequest;
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
    public PageResponse<EmployeeResponse> search(EmployeeSearchCriteria criteria, Pageable pageable) {
        // The native query already orders by e.id; a client-supplied Sort would otherwise get
        // naively appended by Spring Data after that ORDER BY, producing invalid SQL. Sorting
        // combined with filters isn't supported in v1 - results are always id-ordered.
        Pageable unsortedPage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<Long> idsPage = employeeRepository.searchIds(
                criteria.search(),
                criteria.department(),
                criteria.country() == null ? null : criteria.country().name(),
                criteria.jobGrade() == null ? null : criteria.jobGrade().name(),
                criteria.minSalary(),
                criteria.maxSalary(),
                criteria.active(),
                unsortedPage);

        List<Long> ids = idsPage.getContent();
        Map<Long, Employee> employeesById = employeeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        Map<Long, CurrentSalaryRow> currentSalaries = salaryRecordRepository.findCurrentSalaries(ids).stream()
                .collect(Collectors.toMap(CurrentSalaryRow::getEmployeeId, Function.identity()));

        // findAllById doesn't preserve input order, so rebuild it from the (already paginated,
        // already ordered) id list rather than trusting hydration order.
        List<EmployeeResponse> content = ids.stream()
                .map(employeesById::get)
                .map(employee -> mapper.toResponse(employee, mapper.toCurrentSalary(currentSalaries.get(employee.getId()))))
                .toList();

        return new PageResponse<>(
                content, idsPage.getNumber(), idsPage.getSize(), idsPage.getTotalElements(), idsPage.getTotalPages());
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
