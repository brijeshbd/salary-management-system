CREATE TABLE employee (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_code  VARCHAR(20)  NOT NULL,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    department     VARCHAR(50)  NOT NULL,
    country        VARCHAR(2)   NOT NULL,
    job_grade      VARCHAR(10)  NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_employee_employee_code UNIQUE (employee_code)
);
