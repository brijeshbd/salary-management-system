# Performance Considerations

Consolidates the performance-relevant decisions made throughout the build, most of which are
also called out inline where they're implemented.

## Pagination

Every list/search endpoint (`GET /api/employees`) uses Spring Data `Pageable` server-side — the
API never returns the full employee table in one response, and the Angular employee list uses
`mat-paginator` synced to the backend's `page`/`size`/`totalElements`, never fetching more than one
page (default 20 rows) at a time. At 10,000 employees this is the difference between a ~few-KB
response and a multi-MB one on every list view.

## Indexing

Explicit Flyway-managed indexes (not left to `ddl-auto`, see `V4__add_indexes.sql`):

| Index | Purpose |
|---|---|
| `employee(employee_code)` unique | Business-key lookups, enforces uniqueness |
| `employee(department)` | Filter/group-by in search and reporting |
| `employee(country)` | Filter/group-by in search and reporting |
| `employee(job_grade)` | Filter/group-by in search and reporting |
| `employee(active)` partial (`WHERE active`) | Most queries default to active-only |
| `salary_record(employee_id, effective_date DESC)` | The one that matters most — see below |

## Avoiding N+1 on "current salary"

An employee's current salary is never stored on `Employee` (see `tradeoffs.md`) — it's resolved
per-employee as "the `SalaryRecord` with the latest `effective_date <= today`." Naively resolving
this per row of a paginated list would mean 1 query for the page + N queries for each employee's
history (classic N+1). Instead:

- **Single-employee views** (detail page) use a dedicated derived query,
  `findFirstByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc`, which the composite
  index above turns into an index-only lookup.
- **Paginated/filtered lists** batch-resolve current salary for the whole page in one query —
  `SalaryRecordRepository.findCurrentSalaries(List<Long> employeeIds)` uses Postgres `DISTINCT ON`
  to return exactly one row per employee in a single round trip, then the service joins that map
  back onto the page in memory.
- **Search/filter with salary-range criteria** goes further: since "current salary" isn't a
  column, filtering by it can't be a JPA Specification without either a fragile correlated
  max-date subquery or reintroducing N+1. It's a native query with a `LEFT JOIN LATERAL` instead,
  computed in the database in one pass.
- `SalaryRecord → Employee` is **unidirectional** (no `salaryRecords` collection on `Employee`) —
  there's no entity-graph path by which fetching an employee could accidentally lazy-load its full
  history.

## Reporting

- Aggregations (avg/median/total/headcount) run as `GROUP BY` queries in Postgres, including
  `percentile_cont()` for median — never pulled into application memory to compute in Java.
- **No caching layer.** At 10,000 employees / ~25-30k salary records, indexed aggregation queries
  run in low milliseconds. A cache would need invalidation on every salary write to stay correct,
  which is real complexity to solve a latency problem that doesn't exist at this scale (see
  `tradeoffs.md`).
- **Pay-distribution bucketing happens in Java**, not a SQL `width_bucket()` query: fetching one
  currency's raw salary values (at most a few thousand rows) and bucketing them in the service is
  simpler to read and test, and no slower at this volume.
- Reports **never sum across currencies** — a correctness property, not a performance one, but
  worth repeating here since it shapes the query design: every aggregate groups by `(dimension,
  currency)`, so a department paid in three currencies produces three rows rather than requiring
  (impossible) currency conversion.

## Seeding 10,000 employees

Seeding uses raw `JdbcTemplate` batches, not JPA `save()` in a loop:

- Employees: one multi-row `INSERT ... VALUES (...), (...), ... RETURNING id` per 1,000-row chunk.
- Salary history: `JdbcTemplate.batchUpdate` per chunk.
- `reWriteBatchedInserts=true` on the Postgres JDBC URL — the single biggest lever for batch
  insert throughput on Postgres specifically (a MySQL-only property, `rewriteBatchedStatements`,
  was mistakenly used at first and caught before it could silently no-op — see
  `docs/ai-usage-log.md`).

Result: 10,000 employees + ~25,000 salary records seed in well under a second locally (~500ms).

## CSV import

Streamed via Apache Commons CSV rather than loading the whole file into memory, and each valid
row is created through the normal `EmployeeService.create()` path in its own transaction — a
failure on row *N* doesn't roll back rows already created. This trades a small amount of per-row
transaction overhead for correct partial-success semantics, an acceptable trade given import
volume here is "migrating a department's worth of employees off a spreadsheet" (tens to low
hundreds of rows), not the 10,000-row seed script's scale.

## Employee code generation

New employee codes come from a Postgres `SEQUENCE` (`employee_code_seq`), not an
application-level `SELECT MAX(...) + 1` — the sequence is safe under concurrent creates without
extra locking; a max-plus-one query would have a race condition between two simultaneous creates.

## Frontend

- Server-side pagination (above) is mirrored on the client — the employee list never holds more
  than one page of rows in memory.
- The filter form debounces input by 300ms before firing a search request, so typing a name
  doesn't send a request per keystroke.
- Chart.js (~220KB) is isolated to the `/reports` route's lazy chunk via a component-level
  provider (see `docs/design-notes.md`) — the other four routes never download it. This dropped
  the initial bundle from 588KB to 376KB, back under the build's 500KB warning budget.
- HikariCP connection pool defaults are left as-is — reasonable given the stated assumption of a
  small number of concurrent HR users, not a public-traffic API.
