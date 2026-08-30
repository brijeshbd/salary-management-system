# AI Usage Log

This project was built with Claude Code (Anthropic's agentic CLI) as the primary development
tool. This log records how it was used and the reasoning behind key decisions it made or helped
make, updated as the build progresses (not written retroactively at the end).

## Requirements & scoping

- Started from an open-ended "help me build a project" conversation; Claude Code asked
  clarifying questions rather than assuming scope.
- Once the actual assessment brief (PDF) was provided, Claude Code read it, identified the hard
  technical constraints (Java/Spring/Hibernate/Gradle/JUnit, Angular/TypeScript, 10,000-employee
  seed script, deployment, tests, incremental commits, supporting artifacts) that weren't in the
  initial verbal description, and folded them into `REQUIREMENTS.md`.
- The target job description (Java/Spring or Micronaut, Hibernate, Gradle, JUnit, Angular/RxJS)
  was supplied by the user and used to make concrete stack choices (Spring Boot over Micronaut,
  Gradle over Maven, PostgreSQL) — decisions were presented as trade-offs (pros/cons) for the
  user to pick from, rather than silently chosen.

## Architecture design

- Before writing code, Claude Code entered plan mode and delegated architecture design to a
  sub-agent, given the full requirements/constraints as context, tasked with producing an
  opinionated (single-recommendation-per-decision) design: data model, migration strategy, API
  surface, auth approach, seeding strategy, testing strategy, frontend structure, performance
  considerations, and a 12-milestone incremental build sequence.
- The resulting design was reviewed, adapted into an implementation plan, and approved by the
  user before any code was written.
- Key decisions this produced, with their reasoning, are tracked in [`tradeoffs.md`](tradeoffs.md).

## Build process

- **M1 (backend scaffold)**: generated via Spring Initializr (`start.spring.io`), then
  hand-edited to add JWT (jjwt), DataFaker, Testcontainers, and Actuator dependencies not
  covered by the initializer's dependency picker. Verified end-to-end (Postgres via Docker
  Compose, `bootRun`, `/actuator/health` returns UP) before committing.
- **M2 (entities + migrations)**: hit a real Spring Boot 4 gotcha worth recording — Boot 4 split
  autoconfiguration per-starter (unlike Boot 3's all-in-one `spring-boot-autoconfigure`). An
  earlier edit had kept only `org.flywaydb:flyway-database-postgresql` (the Postgres-specific
  Flyway extension) and dropped `org.springframework.boot:spring-boot-starter-flyway`, which is
  what actually carries Flyway's Spring Boot autoconfiguration in v4. Result: Flyway silently
  never ran (zero log output, no error) and Hibernate's `ddl-auto=validate` failed with "missing
  table [employee]" since no migrations had applied. Diagnosed by running with `--debug` to get
  the conditional-evaluation report, confirming `FlywayAutoConfiguration` wasn't activating, then
  fixing the dependency. Re-verified: migrations `V1`-`V4` apply cleanly, all planned indexes and
  unique constraints exist in Postgres, health check still green.
- **M3 (seeding)**: also caught, while writing this milestone, that `application-dev.yml`'s JDBC
  URL used `rewriteBatchedStatements=true` — that's the MySQL driver's batch-rewrite property, not
  PostgreSQL's (`reWriteBatchedInserts`, capital W). Fixed before it could silently no-op batching
  performance. Generator (`EmployeeSeedGenerator`) builds each employee's full salary history
  backward from a target "current" salary (computed from a country/grade band) so history is
  internally consistent, and inserts go through raw `JdbcTemplate` batches (multi-row `INSERT ...
  RETURNING id` for employees, `batchUpdate` for salary records) rather than JPA saves in a loop.
  Verified: 10,000 employees + 25,204 salary records seeded in 517ms; distributions checked via
  SQL (headcount/avg-salary by country, by department, by grade; salary-history depth spread
  1-4 per employee) — all matched the intended weights; re-running the profile correctly no-ops
  (idempotency check).
- (Further milestones logged here as they land.)
