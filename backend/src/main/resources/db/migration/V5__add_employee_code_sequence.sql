-- Seeded employees use the "ACME-" prefix (see DataSeeder). Employees created through the API
-- get an "EMP-" prefix from this sequence instead, so the two can never collide regardless of how
-- many rows were seeded.
CREATE SEQUENCE employee_code_seq START WITH 1;
