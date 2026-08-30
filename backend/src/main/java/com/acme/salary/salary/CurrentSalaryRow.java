package com.acme.salary.salary;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Interface projection for {@link SalaryRecordRepository#findCurrentSalaries}; column aliases in
 * that query must match these getter names. */
public interface CurrentSalaryRow {
    Long getEmployeeId();

    BigDecimal getBaseSalary();

    String getCurrency();

    LocalDate getEffectiveDate();
}
