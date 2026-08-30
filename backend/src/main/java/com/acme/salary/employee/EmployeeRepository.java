package com.acme.salary.employee;

import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Ids of employees matching all given (nullable) filters, salary filters included - a plain
     * JPA Specification can't express "current salary" (it isn't a column) without either a
     * fragile correlated-max-date subquery or losing the N+1-safe batching this app relies on
     * elsewhere, so this goes straight to native SQL with a LATERAL join instead. Returns ids
     * only; the service hydrates full {@link Employee} rows and batches current salaries the same
     * way the plain list endpoint does.
     */
    @Query(
            value =
                    """
            SELECT e.id
            FROM employee e
            LEFT JOIN LATERAL (
                SELECT sr.base_salary FROM salary_record sr
                WHERE sr.employee_id = e.id AND sr.effective_date <= CURRENT_DATE
                ORDER BY sr.effective_date DESC LIMIT 1
            ) cs ON true
            WHERE (:search IS NULL OR e.first_name ILIKE CONCAT('%', :search, '%')
                                  OR e.last_name ILIKE CONCAT('%', :search, '%')
                                  OR e.employee_code ILIKE CONCAT('%', :search, '%'))
              AND (:department IS NULL OR e.department = :department)
              AND (:country IS NULL OR e.country = :country)
              AND (:jobGrade IS NULL OR e.job_grade = :jobGrade)
              AND (:active IS NULL OR e.active = :active)
              AND (:minSalary IS NULL OR cs.base_salary >= :minSalary)
              AND (:maxSalary IS NULL OR cs.base_salary <= :maxSalary)
            ORDER BY e.id
            """,
            countQuery =
                    """
            SELECT count(*)
            FROM employee e
            LEFT JOIN LATERAL (
                SELECT sr.base_salary FROM salary_record sr
                WHERE sr.employee_id = e.id AND sr.effective_date <= CURRENT_DATE
                ORDER BY sr.effective_date DESC LIMIT 1
            ) cs ON true
            WHERE (:search IS NULL OR e.first_name ILIKE CONCAT('%', :search, '%')
                                  OR e.last_name ILIKE CONCAT('%', :search, '%')
                                  OR e.employee_code ILIKE CONCAT('%', :search, '%'))
              AND (:department IS NULL OR e.department = :department)
              AND (:country IS NULL OR e.country = :country)
              AND (:jobGrade IS NULL OR e.job_grade = :jobGrade)
              AND (:active IS NULL OR e.active = :active)
              AND (:minSalary IS NULL OR cs.base_salary >= :minSalary)
              AND (:maxSalary IS NULL OR cs.base_salary <= :maxSalary)
            """,
            nativeQuery = true)
    Page<Long> searchIds(
            @Param("search") String search,
            @Param("department") String department,
            @Param("country") String country,
            @Param("jobGrade") String jobGrade,
            @Param("minSalary") BigDecimal minSalary,
            @Param("maxSalary") BigDecimal maxSalary,
            @Param("active") Boolean active,
            Pageable pageable);
}
