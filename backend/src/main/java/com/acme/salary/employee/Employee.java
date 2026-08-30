package com.acme.salary.employee;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * An employee's profile. Current/historical pay lives entirely in {@link
 * com.acme.salary.salary.SalaryRecord} - not duplicated here - so there is exactly one place a
 * salary figure can be wrong.
 */
@Entity
@Table(name = "employee")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, unique = true, length = 20)
    private String employeeCode;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 50)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Country country;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_grade", nullable = false, length = 10)
    private JobGrade jobGrade;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
