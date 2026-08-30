# Trade-offs & Design Decisions

Decisions are recorded here as they're made, with the reasoning and the alternative considered —
not reconstructed after the fact.

## Product scope (see `REQUIREMENTS.md` for the full list)

Payroll/payslip generation, multi-currency conversion, employee self-service, approval
workflows, external HRIS integration, SSO, and compliance-certification tooling are all
deliberately out of v1 scope — each is a substantial sub-project with its own compliance/UX
surface, and none is required to let an HR Manager manage salary data and answer pay questions.

## Data model: enums/indexed columns, not normalized lookup tables

Department (indexed VARCHAR + app-level allow-list), Country, Currency, and Job Grade (enums)
are columns on `Employee`/`SalaryRecord`, not their own tables. None of them carry business logic
or attributes of their own in v1 (no department budgets, no country tax rules — that's payroll,
explicitly out of scope), so a lookup table would exist only to hold a name and would add a join
to every list/report query for no benefit at 10,000 rows. Trade-off accepted: adding a new
department requires an allow-list update rather than an admin-panel insert — acceptable since
department management isn't a stated requirement.

## Salary history: append-only table, no denormalized "current salary" cache

`SalaryRecord` is append-only; "current salary" is resolved as the row with the latest
`effective_date <= today` per employee via an indexed query, rather than cached on `Employee`.
Caching it would require a dual write (update `Employee` + insert `SalaryRecord`) on every salary
change, risking drift, for a query that's trivial at this scale without the cache.

## Schema migrations: Flyway, not Hibernate `ddl-auto`

`ddl-auto=validate` everywhere; schema changes go through versioned Flyway SQL migrations. This
guarantees the schema tested locally is identical to what's deployed, and the migration history
is itself a readable record of the schema's evolution.

## Auth: stateless JWT, not sessions

The Angular SPA and Spring Boot API are separately deployed artifacts, so stateless JWT avoids
cross-origin cookie/session complexity. Kept deliberately minimal: one seeded HR Manager account,
no OAuth2/SSO, no refresh-token rotation, no self-registration — matches "basic access control,
single role" without building enterprise auth that's explicitly out of scope.

## Testing: Testcontainers + real Postgres, not H2

The app relies on Postgres-specific behavior (`percentile_cont` for median in reports, numeric
precision, enum handling) that H2's compatibility mode doesn't fully replicate. Docker is already
a project dependency (local Postgres), so Testcontainers adds no new setup cost, and a real
Postgres instance is more trustworthy than a green suite that could still fail in production.

## Frontend state management: services + RxJS, not NgRx

The app is fundamentally CRUD plus read-mostly reports against a REST API — no complex
cross-cutting client state, optimistic updates, or deep shared-state trees that would justify
NgRx. The target job description lists RxJS as a skill, not NgRx specifically.

## Reporting: never sum across currencies

Grouped reports (by department, country, grade) and org-wide totals break results out per
currency (one row per group+currency) instead of producing a single combined figure. Since FX
conversion is explicitly out of scope, summing e.g. USD and INR salaries together would produce a
number with no real meaning — worse than not showing a total at all. A department with employees
in three currencies shows three rows, not one misleading blended average.

## Reporting: no caching layer

10,000 employees / ~25-30k salary records aggregate in low milliseconds on indexed Postgres
columns. A cache would need invalidation on every salary write to stay correct, adding
complexity to solve a performance problem that doesn't exist at this scale. Revisit if the
dataset grows by orders of magnitude or real latency issues show up in monitoring.
