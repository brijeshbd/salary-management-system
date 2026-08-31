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
dataset grows by orders of magnitude or real latency issues show up in monitoring. The same
reasoning applies to introducing Redis anywhere else in the app (e.g. as a session/rate-limit
store) — nothing here needs shared state across instances at this scale, and stateless JWT auth
was chosen specifically to avoid needing one (see above).

## Concurrency: audited for thread-safety, one deliberate multithreading demonstration

Every `@Service`/`@Component`/`@RestController`/`@Configuration`/filter class was audited for
thread-safety under Spring's default model (singleton beans, each HTTP request on its own thread
from Tomcat's pool). Result: no violations — every bean holds only `private final` fields set once
via constructor injection or `@Value` at startup, with all per-request mutable state (query
params, DTOs, working collections) kept local to method bodies. This is a property of following
standard idiomatic Spring conventions throughout, not something bolted on afterward.

Beyond that baseline, the codebase didn't otherwise call for explicit multithreading — there's no
CPU-bound work in the request path, and the 10,000-employee seed script was already well under a
second running single-threaded (see `docs/performance.md`). `DataSeeder` now parallelizes its
CPU-bound part (generating each chunk's synthetic employee data) across a fixed thread pool via
`ExecutorService`/`CompletableFuture`, while keeping the DB writes sequential per chunk (batch
inserts are I/O-bound and already fast; parallelizing them would just add connection contention).
This is a deliberate demonstration, not a performance necessity, and it surfaced a real bug before
it shipped: `EmployeeSeedGenerator` wraps a `Faker` instance, and DataFaker's `Faker` isn't
documented as safe for concurrent use from multiple threads. Sharing one `Faker` across the thread
pool (as the original single-threaded code did, harmlessly, since only one thread ever touched it)
would have risked corrupted names under real concurrent access. Fixed by giving each parallel task
its own `EmployeeSeedGenerator`/`Faker` instance rather than sharing one — verified afterward with
no shared mutable state across threads, identical deterministic `ACME-000001..010000` employee-code
numbering (computed per-chunk from arithmetic, not a shared counter), and zero corrupted names
across a full 10,000-row re-seed.
