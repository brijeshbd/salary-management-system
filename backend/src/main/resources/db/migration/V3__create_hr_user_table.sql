CREATE TABLE hr_user (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    CONSTRAINT uq_hr_user_email UNIQUE (email)
);
