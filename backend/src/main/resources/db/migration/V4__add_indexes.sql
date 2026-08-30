-- Filter/group-by columns used by search and reporting endpoints.
CREATE INDEX idx_employee_department ON employee (department);
CREATE INDEX idx_employee_country ON employee (country);
CREATE INDEX idx_employee_job_grade ON employee (job_grade);

-- Most queries default to active employees only.
CREATE INDEX idx_employee_active ON employee (active) WHERE active = TRUE;

-- The index that matters most: makes "current salary" (latest effective_date per employee) and
-- "full history for employee, newest first" fast instead of a per-employee table scan.
CREATE INDEX idx_salary_record_employee_effective_date
    ON salary_record (employee_id, effective_date DESC);
