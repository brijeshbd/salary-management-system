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
- **M4 (Employee CRUD API + tests)**: two more Boot 4 modularization surprises, same pattern as
  M2's Flyway one. (1) `@AutoConfigureMockMvc` isn't in `spring-boot-test-autoconfigure` anymore -
  it moved to a new `spring-boot-starter-webmvc-test` module, package
  `org.springframework.boot.webmvc.test.autoconfigure` (found by unzipping candidate jars and
  grepping for the class file, since the old import path just silently doesn't exist rather than
  erroring helpfully). (2) Boot 4 pulls in Jackson 3, which renamed its Maven group and base
  package from `com.fasterxml.jackson.*` to `tools.jackson.*` - rather than chase that rename in
  test code, integration tests read response JSON via `JsonPath.read(...)` (already a transitive
  dependency via MockMvc's own `jsonPath()` matcher) instead of `ObjectMapper`, sidestepping the
  question entirely. Also had to pin `org.testcontainers:*` versions explicitly - Boot's BOM
  manages `spring-boot-testcontainers` (the `@ServiceConnection` glue) but not the Testcontainers
  library itself.
  Design notes: employee codes created via the API get an `EMP-` prefix from a dedicated sequence
  (seeded data uses `ACME-`), so the two can never collide regardless of seed size. The list
  endpoint resolves current salary for a whole page in one query (Postgres `DISTINCT ON`, keyed by
  employee id) rather than one query per row, to honor the N+1-avoidance decision in
  `tradeoffs.md`. Added a deliberately permissive `SecurityConfig` placeholder (permit-all) so the
  API is usable before real auth exists - called out in code and here so it doesn't read as an
  oversight; replaced with JWT-based rules in the auth milestone.
  Verified: 6 unit tests (Mockito, no Spring context) + 4 Testcontainers integration tests all
  green; manual smoke test of the full CRUD lifecycle against the real 10,000-row dev database
  (list, create, get, update, soft-delete, 404) all behaved as expected.
- **M5 (salary history, search/filter, CSV import/export, reporting)** - the largest milestone,
  landed as four sub-commits:
  - *Salary history*: straightforward append-only GET/POST, reusing the existing
    current-salary-resolution pattern.
  - *Search/filter*: salary-range filtering can't be a JPA Specification (current salary isn't a
    column), so it's a native query with a `LEFT JOIN LATERAL` instead of a fragile correlated
    max-date subquery - returns ids only, hydrated the same N+1-safe way as the M4 list endpoint.
    Also fixed a test-isolation bug: a shared Testcontainers Postgres instance across test methods
    in one class meant fixture data accumulated across tests; added `@Transactional` (rollback per
    test) to fix it.
  - *CSV import/export*: partial-success import (one bad row doesn't fail the file), reusing
    `EmployeeService.create` per valid row rather than duplicating creation logic.
  - *Reporting*: the one place a product-correctness issue showed up rather than a Spring Boot
    version gotcha. Grouped reports (by department/grade) and org-wide totals deliberately never
    sum across currencies - since FX conversion is out of scope, summing e.g. USD and INR together
    would produce a number with no real meaning, so every aggregate is broken out per currency
    instead (one row per group+currency).
    Manually smoke-testing the `raises since <date>` report against the real 10,000-row dataset
    surfaced a seeding realism bug from M3: `EmployeeSeedGenerator` always dated each employee's
    *current* salary record within the last 0-6 months, so literally every employee looked like
    they'd just gotten a raise - `since=2024-01-01` returned all 10,000 rows, which made the report
    useless for demo purposes. Fixed by widening that window to 0-24 months in
    `EmployeeSeedGenerator`; re-seeded and re-verified the report now differentiates meaningfully
    (10,000 / 4,822 / 1,182 / 380 for cutoffs of 2024-01-01 / 2025-09-01 / 2026-06-01 / 2026-08-01
    respectively). This is exactly the kind of thing that's easy to miss testing against a handful
    of hand-picked fixture rows but jumps out immediately against the full seeded dataset - worth
    noting as a reason to keep smoke-testing new features against the real 10k rows, not just unit/
    integration test fixtures.
  All four sub-commits: 39 tests total (unit + Testcontainers integration), full suite green.
- **M6 (auth)**: replaced the M4 permit-all placeholder with real Spring Security + stateless JWT.
  One more Spring Security 7 (bundled with Boot 4) package move to work around:
  `AuthenticationConfiguration` isn't under `org.springframework.security.authentication.configuration`
  anymore, it moved up a level to
  `org.springframework.security.config.annotation.authentication.configuration` - found the same
  way as the earlier ones (unzip the candidate jar, grep for the class file).
  Design notes: the JWT filter trusts the token's own signed claims (email + role) rather than
  re-querying the database on every request - cheaper, and there's nothing further to look up for
  a single-role app. Login itself does go through the full Spring Security stack
  (`AuthenticationManager` → auto-wired `DaoAuthenticationProvider`, built automatically from the
  single `HrUserDetailsService` + `BCryptPasswordEncoder` beans in context) rather than a hand-rolled
  credential check, for a more faithful demonstration of the pattern the target JD asks for.
  A real HR user is seeded unconditionally on startup (idempotent, any profile) - without it
  nothing could ever log in.
  Test strategy: rather than crafting a real JWT in every existing integration test class, added
  `@WithMockUser(roles = "HR_MANAGER")` at the class level to the five pre-existing IT classes
  (they're testing their own business logic, not auth) and wrote one dedicated `AuthControllerIT`
  that exercises the real login → token → protected-endpoint flow end-to-end, plus wrong-password/
  unknown-email/missing-token/garbage-token failure cases. All 44 tests green on the first run
  after wiring this up; also manually verified the full flow (401 without a token, login, 200 with
  the token, 401 on wrong password) against the real dev database.
- **M7 (frontend scaffold + auth)**: `ng new` generates Angular 22 standalone (no NgModule) by
  default now, matching the plan without needing a flag. Two build errors worth recording: (1)
  the Angular Material schematic (`ng add @angular/material --animations=enabled`) didn't actually
  install `@angular/animations`, which `provideAnimationsAsync()` needs at runtime - the build
  failed with "Could not resolve '@angular/animations/browser'" until it was added explicitly
  (it's flagged deprecated in favor of a newer native `animate.enter`/`animate.leave` API, but
  it's still what Material's own components need today, so this is the correct dependency for
  now). (2) a `readonly form = this.formBuilder.group(...)` field initializer running before
  constructor-parameter properties are assigned is a TypeScript initialization-order error -
  fixed by switching to `inject(FormBuilder)` field-style injection, which resolves before other
  field initializers run.
  Bigger catch from manually verifying the login flow end-to-end (curl, not a real browser -
  browser tooling wasn't available this session): the Angular dev server (localhost:4200) calling
  the API (localhost:8080) is a cross-origin request, and the backend had no CORS configuration at
  all - a real browser would have silently blocked every API call with no error surfaced until
  someone actually opened dev tools. Caught by sending an OPTIONS preflight with curl and an
  `Origin` header and checking for `Access-Control-Allow-Origin` in the response (there wasn't
  one); added a `CorsConfigurationSource` bean (allowed origins from an env-overridable property,
  defaulting to `http://localhost:4200`) and confirmed the header appears on both the preflight
  and the actual request afterward. This only matters for local dev - the deployed stack (M11)
  serves the frontend through nginx same-origin, taking CORS out of the request path entirely -
  but it's exactly the kind of gap that a curl-only backend test suite has no way to catch, since
  CORS is a browser-enforced restriction, not a server-side error the API itself would ever return
  to a non-browser client. Worth flagging as a real limitation of this session's testing: without
  browser tooling, UI behavior (rendering, click-through flows, console errors) is verified by
  code review and build/serve success, not by actually driving the page - the CORS bug is a
  concrete example of the kind of issue that gap can hide until a human opens it in a browser.
- (Further milestones logged here as they land.)
