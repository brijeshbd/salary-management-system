package com.acme.salary.salary;

import com.acme.salary.employee.Employee;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One salary-effective-period for an employee. Append-only: a raise/adjustment is a new row,
 * never a mutation of a prior one, so history is preserved by construction. "Current salary" is
 * whichever row has the latest {@code effectiveDate <= today} for that employee - resolved by
 * query, not stored anywhere.
 *
 * <p>Deliberately unidirectional ({@code @ManyToOne} only, no back-reference collection on {@link
 * Employee}) so fetching a page of employees never risks lazily pulling in each one's full salary
 * history.
 */
@Entity
@Table(name = "salary_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class SalaryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "base_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseSalary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SalaryChangeReason reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
