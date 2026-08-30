package com.acme.salary.salary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalaryRecordRepository extends JpaRepository<SalaryRecord, Long> {

    List<SalaryRecord> findByEmployeeIdOrderByEffectiveDateDesc(Long employeeId);

    Optional<SalaryRecord> findFirstByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
            Long employeeId, LocalDate asOf);

    /**
     * The latest effective (i.e. current) salary for each of the given employees, in one query -
     * used by the employee list endpoint to avoid an N+1 lookup per row. {@code DISTINCT ON} is
     * Postgres' idiom for "latest row per group" and reads more directly than an equivalent
     * ROW_NUMBER()/window-function query would here.
     */
    @Query(
            value =
                    """
            SELECT DISTINCT ON (sr.employee_id)
                sr.employee_id AS employeeId,
                sr.base_salary AS baseSalary,
                sr.currency AS currency,
                sr.effective_date AS effectiveDate
            FROM salary_record sr
            WHERE sr.employee_id IN (:employeeIds) AND sr.effective_date <= CURRENT_DATE
            ORDER BY sr.employee_id, sr.effective_date DESC
            """,
            nativeQuery = true)
    List<CurrentSalaryRow> findCurrentSalaries(@Param("employeeIds") List<Long> employeeIds);
}
