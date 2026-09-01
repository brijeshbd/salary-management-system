package com.acme.salary.reporting;

import com.acme.salary.reporting.dto.CurrencyTotal;
import com.acme.salary.reporting.dto.GroupSalarySummary;
import com.acme.salary.reporting.dto.PayDistributionBucket;
import com.acme.salary.reporting.dto.RecentRaise;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

/** Mirrors {@code CsvExportService} (employee export) - same library, same "records in, CSV
 * string out" shape, one method per report so each keeps its own column headers. */
@Service
public class ReportingCsvService {

    public String groupSummary(List<GroupSalarySummary> rows, String groupHeader) {
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT
                .builder()
                .setHeader(groupHeader, "currency", "headcount", "avgSalary", "medianSalary", "totalCost")
                .get();

        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (GroupSalarySummary row : rows) {
                printer.printRecord(
                        row.group(), row.currency(), row.headcount(), row.avgSalary(), row.medianSalary(), row.totalCost());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return writer.toString();
    }

    public String currencyTotals(List<CurrencyTotal> rows) {
        StringWriter writer = new StringWriter();
        CSVFormat format =
                CSVFormat.DEFAULT.builder().setHeader("currency", "headcount", "totalCost").get();

        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (CurrencyTotal row : rows) {
                printer.printRecord(row.currency(), row.headcount(), row.totalCost());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return writer.toString();
    }

    public String payDistribution(List<PayDistributionBucket> rows) {
        StringWriter writer = new StringWriter();
        CSVFormat format =
                CSVFormat.DEFAULT.builder().setHeader("rangeStart", "rangeEnd", "count").get();

        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (PayDistributionBucket row : rows) {
                printer.printRecord(row.rangeStart(), row.rangeEnd(), row.count());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return writer.toString();
    }

    public String raises(List<RecentRaise> rows) {
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT
                .builder()
                .setHeader("employeeCode", "firstName", "lastName", "department", "newSalary", "currency", "effectiveDate", "reason")
                .get();

        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (RecentRaise row : rows) {
                printer.printRecord(
                        row.employeeCode(),
                        row.firstName(),
                        row.lastName(),
                        row.department(),
                        row.newSalary(),
                        row.currency(),
                        row.effectiveDate(),
                        row.reason());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return writer.toString();
    }
}
