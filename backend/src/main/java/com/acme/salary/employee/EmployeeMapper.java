package com.acme.salary.employee;

import com.acme.salary.employee.dto.CurrentSalary;
import com.acme.salary.employee.dto.EmployeeCreateRequest;
import com.acme.salary.employee.dto.EmployeeResponse;
import com.acme.salary.employee.dto.EmployeeUpdateRequest;
import com.acme.salary.salary.Currency;
import com.acme.salary.salary.CurrentSalaryRow;
import com.acme.salary.salary.SalaryRecord;
import org.springframework.stereotype.Component;

@Component
public class EmployeeMapper {

    public Employee toEntity(EmployeeCreateRequest request, String employeeCode) {
        return Employee.builder()
                .employeeCode(employeeCode)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .department(request.department())
                .country(request.country())
                .jobGrade(request.jobGrade())
                .active(true)
                .build();
    }

    public void applyUpdate(Employee employee, EmployeeUpdateRequest request) {
        employee.setFirstName(request.firstName());
        employee.setLastName(request.lastName());
        employee.setDepartment(request.department());
        employee.setCountry(request.country());
        employee.setJobGrade(request.jobGrade());
    }

    public EmployeeResponse toResponse(Employee employee, CurrentSalary currentSalary) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getDepartment(),
                employee.getCountry(),
                employee.getJobGrade(),
                employee.isActive(),
                currentSalary,
                employee.getCreatedAt(),
                employee.getUpdatedAt());
    }

    public CurrentSalary toCurrentSalary(SalaryRecord record) {
        if (record == null) return null;
        return new CurrentSalary(record.getBaseSalary(), record.getCurrency(), record.getEffectiveDate());
    }

    public CurrentSalary toCurrentSalary(CurrentSalaryRow row) {
        if (row == null) return null;
        return new CurrentSalary(row.getBaseSalary(), Currency.valueOf(row.getCurrency()), row.getEffectiveDate());
    }
}
