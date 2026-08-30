package com.acme.salary.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.salary.common.PageResponse;
import com.acme.salary.common.exception.ResourceNotFoundException;
import com.acme.salary.employee.dto.EmployeeCreateRequest;
import com.acme.salary.employee.dto.EmployeeResponse;
import com.acme.salary.employee.dto.EmployeeUpdateRequest;
import com.acme.salary.salary.Currency;
import com.acme.salary.salary.CurrentSalaryRow;
import com.acme.salary.salary.SalaryChangeReason;
import com.acme.salary.salary.SalaryRecord;
import com.acme.salary.salary.SalaryRecordRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

class EmployeeServiceTest {

    private EmployeeRepository employeeRepository;
    private SalaryRecordRepository salaryRecordRepository;
    private JdbcTemplate jdbcTemplate;
    private EmployeeService service;

    @BeforeEach
    void setUp() {
        employeeRepository = mock(EmployeeRepository.class);
        salaryRecordRepository = mock(SalaryRecordRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new EmployeeService(employeeRepository, salaryRecordRepository, new EmployeeMapper(), jdbcTemplate);
    }

    @Test
    void create_savesEmployeeAndInitialSalaryRecord() {
        when(jdbcTemplate.queryForObject(eq("SELECT nextval('employee_code_seq')"), eq(Long.class)))
                .thenReturn(5L);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setId(42L);
            return employee;
        });

        EmployeeCreateRequest request = new EmployeeCreateRequest(
                "Ada",
                "Lovelace",
                "Engineering",
                Country.US,
                JobGrade.IC3,
                new BigDecimal("95000.00"),
                Currency.USD,
                LocalDate.of(2026, 1, 1));

        EmployeeResponse response = service.create(request);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.employeeCode()).isEqualTo("EMP-000005");
        assertThat(response.firstName()).isEqualTo("Ada");
        assertThat(response.active()).isTrue();
        assertThat(response.currentSalary().amount()).isEqualByComparingTo("95000.00");
        assertThat(response.currentSalary().currency()).isEqualTo(Currency.USD);

        verify(salaryRecordRepository)
                .save(org.mockito.ArgumentMatchers.argThat(record -> record.getBaseSalary()
                                .compareTo(new BigDecimal("95000.00"))
                        == 0
                        && record.getReason() == SalaryChangeReason.INITIAL
                        && record.getEffectiveDate().equals(LocalDate.of(2026, 1, 1))));
    }

    @Test
    void getById_whenEmployeeMissing_throwsResourceNotFoundException() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getById_whenFound_includesResolvedCurrentSalary() {
        Employee employee = Employee.builder()
                .id(7L)
                .employeeCode("EMP-000007")
                .firstName("Grace")
                .lastName("Hopper")
                .department("Engineering")
                .country(Country.US)
                .jobGrade(JobGrade.IC5)
                .active(true)
                .build();
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));

        SalaryRecord current = SalaryRecord.builder()
                .baseSalary(new BigDecimal("150000.00"))
                .currency(Currency.USD)
                .effectiveDate(LocalDate.of(2026, 6, 1))
                .reason(SalaryChangeReason.RAISE)
                .build();
        when(salaryRecordRepository.findFirstByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                        eq(7L), any(LocalDate.class)))
                .thenReturn(Optional.of(current));

        EmployeeResponse response = service.getById(7L);

        assertThat(response.currentSalary().amount()).isEqualByComparingTo("150000.00");
        assertThat(response.currentSalary().effectiveDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void update_changesProfileFields_leavesSalaryUntouched() {
        Employee employee = Employee.builder()
                .id(3L)
                .employeeCode("EMP-000003")
                .firstName("Old")
                .lastName("Name")
                .department("Sales")
                .country(Country.GB)
                .jobGrade(JobGrade.IC1)
                .active(true)
                .build();
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(employee));
        when(salaryRecordRepository.findFirstByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                        eq(3L), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        EmployeeUpdateRequest request =
                new EmployeeUpdateRequest("New", "Name", "Engineering", Country.DE, JobGrade.IC4);
        EmployeeResponse response = service.update(3L, request);

        assertThat(response.firstName()).isEqualTo("New");
        assertThat(response.department()).isEqualTo("Engineering");
        assertThat(response.country()).isEqualTo(Country.DE);
        assertThat(response.jobGrade()).isEqualTo(JobGrade.IC4);
        verify(salaryRecordRepository, never()).save(any());
    }

    @Test
    void deactivate_setsEmployeeInactive() {
        Employee employee = Employee.builder()
                .id(9L)
                .employeeCode("EMP-000009")
                .firstName("A")
                .lastName("B")
                .department("HR")
                .country(Country.CA)
                .jobGrade(JobGrade.IC2)
                .active(true)
                .build();
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(employee));

        service.deactivate(9L);

        assertThat(employee.isActive()).isFalse();
    }

    @Test
    void search_batchesCurrentSalaryLookup_andPreservesIdOrder() {
        Employee e1 = Employee.builder()
                .id(1L)
                .employeeCode("EMP-000001")
                .firstName("One")
                .lastName("Person")
                .department("Engineering")
                .country(Country.US)
                .jobGrade(JobGrade.IC2)
                .active(true)
                .build();
        Employee e2 = Employee.builder()
                .id(2L)
                .employeeCode("EMP-000002")
                .firstName("Two")
                .lastName("Person")
                .department("Sales")
                .country(Country.GB)
                .jobGrade(JobGrade.IC3)
                .active(true)
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Long> idsPage = new PageImpl<>(List.of(1L, 2L), pageable, 2);
        when(employeeRepository.searchIds(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(idsPage);
        // findAllById intentionally returns in a different order than requested, to prove the
        // service re-sorts by the ids list rather than trusting hydration order.
        when(employeeRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(e2, e1));

        CurrentSalaryRow row1 = mock(CurrentSalaryRow.class);
        when(row1.getEmployeeId()).thenReturn(1L);
        when(row1.getBaseSalary()).thenReturn(new BigDecimal("80000.00"));
        when(row1.getCurrency()).thenReturn("USD");
        when(row1.getEffectiveDate()).thenReturn(LocalDate.of(2026, 3, 1));
        when(salaryRecordRepository.findCurrentSalaries(anyList())).thenReturn(List.of(row1));

        EmployeeSearchCriteria criteria = new EmployeeSearchCriteria(null, null, null, null, null, null, null);
        PageResponse<EmployeeResponse> result = service.search(criteria, pageable);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).hasSize(2);
        EmployeeResponse first = result.content().get(0);
        EmployeeResponse second = result.content().get(1);
        assertThat(first.id()).isEqualTo(1L);
        assertThat(first.currentSalary().amount()).isEqualByComparingTo("80000.00");
        assertThat(second.id()).isEqualTo(2L);
        assertThat(second.currentSalary()).isNull(); // no matching row for employee 2 -> null, not an error
    }
}
