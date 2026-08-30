CREATE TABLE salary_record (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id     BIGINT       NOT NULL REFERENCES employee (id),
    base_salary     NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    effective_date  DATE         NOT NULL,
    reason          VARCHAR(20),
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);
