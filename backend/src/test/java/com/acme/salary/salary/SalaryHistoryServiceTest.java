package com.acme.salary.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.salary.common.exception.ResourceNotFoundException;
import com.acme.salary.employee.Country;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.JobGrade;
import com.acme.salary.salary.dto.SalaryAdjustmentRequest;
import com.acme.salary.salary.dto.SalaryRecordResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SalaryHistoryServiceTest {

    private EmployeeRepository employeeRepository;
    private SalaryRecordRepository salaryRecordRepository;
    private SalaryHistoryService service;

    @BeforeEach
    void setUp() {
        employeeRepository = mock(EmployeeRepository.class);
        salaryRecordRepository = mock(SalaryRecordRepository.class);
        service = new SalaryHistoryService(employeeRepository, salaryRecordRepository);
    }

    @Test
    void getHistory_whenEmployeeMissing_throws() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHistory(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getHistory_returnsRecordsNewestFirst() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee(1L)));
        SalaryRecord older = SalaryRecord.builder()
                .id(1L)
                .baseSalary(new BigDecimal("80000.00"))
                .currency(Currency.USD)
                .effectiveDate(LocalDate.of(2024, 1, 1))
                .reason(SalaryChangeReason.INITIAL)
                .build();
        SalaryRecord newer = SalaryRecord.builder()
                .id(2L)
                .baseSalary(new BigDecimal("90000.00"))
                .currency(Currency.USD)
                .effectiveDate(LocalDate.of(2025, 1, 1))
                .reason(SalaryChangeReason.RAISE)
                .build();
        when(salaryRecordRepository.findByEmployeeIdOrderByEffectiveDateDesc(1L))
                .thenReturn(List.of(newer, older));

        List<SalaryRecordResponse> history = service.getHistory(1L);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).effectiveDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(history.get(1).effectiveDate()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    @Test
    void addRecord_whenEmployeeMissing_throws() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.empty());
        SalaryAdjustmentRequest request = new SalaryAdjustmentRequest(
                new BigDecimal("100000.00"), Currency.USD, LocalDate.now(), SalaryChangeReason.RAISE);

        assertThatThrownBy(() -> service.addRecord(1L, request)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addRecord_savesNewRecordLinkedToEmployee_neverMutatingExisting() {
        Employee employee = employee(1L);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(salaryRecordRepository.save(any(SalaryRecord.class))).thenAnswer(invocation -> {
            SalaryRecord record = invocation.getArgument(0);
            record.setId(10L);
            return record;
        });

        SalaryAdjustmentRequest request = new SalaryAdjustmentRequest(
                new BigDecimal("105000.00"), Currency.USD, LocalDate.of(2026, 3, 1), SalaryChangeReason.RAISE);

        SalaryRecordResponse response = service.addRecord(1L, request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.baseSalary()).isEqualByComparingTo("105000.00");
        assertThat(response.reason()).isEqualTo(SalaryChangeReason.RAISE);
    }

    private Employee employee(Long id) {
        return Employee.builder()
                .id(id)
                .employeeCode("EMP-%06d".formatted(id))
                .firstName("A")
                .lastName("B")
                .department("Engineering")
                .country(Country.US)
                .jobGrade(JobGrade.IC2)
                .active(true)
                .build();
    }
}
