package com.acme.salary.employee;

import lombok.Getter;

/**
 * A fixed, ordinal job-level ladder: individual-contributor grades IC1-IC6, then management
 * grades M1-M4. {@code level} exists so range queries ("grade >= M1") don't depend on enum
 * declaration order.
 */
@Getter
public enum JobGrade {
    IC1(1), IC2(2), IC3(3), IC4(4), IC5(5), IC6(6),
    M1(7), M2(8), M3(9), M4(10);

    private final int level;

    JobGrade(int level) {
        this.level = level;
    }
}
