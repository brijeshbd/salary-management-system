package com.acme.salary.importexport;

import com.acme.salary.employee.dto.CurrentSalary;
import com.acme.salary.employee.dto.EmployeeResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

@Service
public class CsvExportService {

    private static final List<String> HEADERS = List.of(
            "employeeCode",
            "firstName",
            "lastName",
            "department",
            "country",
            "jobGrade",
            "active",
            "currentSalaryAmount",
            "currentSalaryCurrency",
            "currentSalaryEffectiveDate");

    public String exportEmployees(List<EmployeeResponse> employees) {
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(HEADERS.toArray(String[]::new)).get();

        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (EmployeeResponse employee : employees) {
                CurrentSalary salary = employee.currentSalary();
                printer.printRecord(
                        employee.employeeCode(),
                        employee.firstName(),
                        employee.lastName(),
                        employee.department(),
                        employee.country(),
                        employee.jobGrade(),
                        employee.active(),
                        salary == null ? "" : salary.amount(),
                        salary == null ? "" : salary.currency(),
                        salary == null ? "" : salary.effectiveDate());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return writer.toString();
    }
}
