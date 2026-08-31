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
- **M8 (employee list, detail/edit, salary adjustment)**: the browser-tooling gap from M7 got
  solved properly here rather than worked around again - installed Playwright (kept out of
  `package.json`, it's a one-off verification tool, not a committed test) and Chromium locally,
  giving real screenshots and DOM assertions instead of curl-only guesswork. That immediately paid
  for itself:
  - Login → employee list rendered correctly against the real 10,000-row dataset first try
    (paginator read "1-20 of 10000", correct per-currency formatting across USD/CAD/GBP/EUR/INR,
    zero console errors).
  - Clicking into the employee detail page, recording a salary adjustment, and editing the profile
    all *appeared* to fail at first - the base-salary field kept showing "Required" even after
    `page.fill()` set it. Root-caused through several rounds of isolation (an isolated fill worked,
    the same fill inside the full flow didn't; direct Angular `FormControl` inspection showed the
    value really was empty, not just visually so): it was two independent, non-app issues stacked
    together. First, Vite's dev-server dependency pre-bundler discovered the datepicker/dialog
    modules for the first time mid-test and force-reloaded the page, wiping in-progress form state
    - a one-time dev-server cold-start cost that doesn't exist in a production build. Second, and
    the more durable finding: Playwright's `page.fill()` sets the DOM value directly rather than
    dispatching real keystrokes, and this input has a custom `appNumericOnly` directive listening
    for `beforeinput` - the two don't compose, so Angular's own value-accessor sync ends up
    reverting the field a few dozen milliseconds after `fill()` returns. Confirmed by switching to
    `page.keyboard.type()` (real keystrokes), which worked immediately and was verified against
    the live Angular `FormControl` value, not just the DOM. This is a testing-tool quirk, not an
    app bug - a real user typing into the field is unaffected - but it's exactly the kind of false
    negative that would have wasted time if not run down properly; recorded here so it isn't
    rediscovered from scratch in a later milestone that touches numeric inputs.
  Verified end-to-end via screenshots: recording an adjustment correctly appended a new (dated,
  reasoned) salary-history row while leaving the prior row's own currency untouched (a Canadian
  employee's history correctly shows one CAD row and one newly-added USD row - proof the
  per-record currency model, not a single employee-level currency, is actually working), and
  editing the profile correctly updated the header and persisted.
  Rounding out the milestone: the add-employee form (auto-defaulting currency from the selected
  country, only when the user hasn't already touched currency themselves) and the CSV import
  screen both verified end-to-end with Playwright too - a 3-row test CSV with one deliberately
  invalid country produced exactly "3 total / 2 succeeded / 1 failed" with the specific row number
  and message surfaced in the UI, matching the backend's partial-success design from M5. All test
  data created during verification (employees, imported rows) was deleted from the dev database
  afterward so it doesn't pollute later screenshots or the eventual demo video.
- **M9 (reports dashboard)**: loaded the project's `dataviz` skill before writing any chart code,
  per its own trigger condition. Its procedure is built around multi-series categorical palettes;
  this dashboard's two charts (headcount by country, pay-distribution counts) are both
  single-series bar charts where the x-axis already carries identity, so a categorical rainbow per
  bar would be redundant noise rather than signal - the skill's own "sequential = one hue" rule
  covers exactly this case, so both charts use one consistent, validated hue (slot 1 from the
  skill's reference palette) rather than running the full multi-hue validator, which is scoped to
  a problem (distinguishing many series) this dashboard doesn't have.
  Two things worth recording:
  1. *Bundle-size lesson, two attempts to get right.* Registering `provideCharts()` in
     `app.config.ts` pulled chart.js into the main bundle for all 5 routes, tripping the 500KB
     budget warning. Moving it to the `reports` route's own `providers` array didn't fix it either
     - `app.routes.ts` is always eagerly loaded (only the lazy component import inside a route is
     deferred), so a static top-level `import` anywhere in that file still lands in the main
     chunk regardless of which array it's referenced from. The fix was registering the provider
     inside `ReportsDashboardComponent`'s own `@Component` decorator instead, since that file is
     the thing actually behind the lazy `loadComponent()` boundary. Bundle dropped from 588KB back
     to 376KB (under budget), with chart.js correctly isolated to a 223KB chunk that only loads
     when a user visits `/reports`.
  2. *A real bug caught by charting, not a charting bug.* The initial "Headcount by Country" chart
     showed two separate "CA" bars. Not an app defect - during the M8 salary-adjustment dialog
     test, a $999,999 *USD* raise had been recorded on a Canadian (CAD) employee and never cleaned
     up afterward (M8's cleanup only deleted employees *created* during testing, not a mutated
     salary record on an *existing* seeded employee) - since the by-country report groups by
     (country, currency), that one stray record was enough to split Canada into two rows. Fixed by
     deleting the stray record and restoring the employee's name (also overwritten by that same
     earlier test) directly in the dev database, then re-verified the chart showed exactly 7
     country bars. Worth calling out as the general lesson, not just the specific fix: any
     manual-verification step that mutates an *existing* seeded record (not just ones it creates)
     needs the same disciplined cleanup, since aggregate reports will surface that pollution far
     more visibly than a CRUD screen would.
  Also verified the currency-switch on the pay-distribution chart re-renders with an entirely
  different bucket scale for INR vs USD (correctly not sharing an axis - see the skill's "one
  axis" rule) - reinforcing why per-currency reporting, not a single blended chart, is the right
  call here.
- **M10 (docs)**: consolidated `architecture.md` (with Mermaid diagrams — GitHub renders these
  natively, no separate diagramming tool needed), `design-notes.md`, and `performance.md` from
  decisions that had mostly already been made and recorded (in commit messages, code comments, and
  this log) throughout M1-M9, rather than designed fresh at the end. Re-read `tradeoffs.md` and
  this log in full for consistency before writing the new docs; no corrections needed.
- **M11 (dockerize)**: multi-stage Dockerfiles for both backend (Gradle build → JRE runtime) and
  frontend (`npm run build` → nginx). Two adjustments made proactively rather than discovered by
  failure: (1) used `-alpine` JRE/JDK base images specifically so `wget` (via BusyBox) is available
  for the backend's Docker healthcheck without installing anything extra — checked this before
  writing the healthcheck rather than after it failed. (2) `docker-compose`'s `backend` service
  runs with `SPRING_PROFILES_ACTIVE=docker,seed` permanently, not just for an initial run — since
  `DataSeeder` and `HrUserSeeder` are both idempotent, leaving `seed` always active makes the whole
  stack self-seeding on any restart with no separate manual step, which is exactly what "verified
  from a clean `docker-compose up`" should mean.
  Verified genuinely from a clean state — `docker compose down -v` (destroying the postgres volume)
  before `docker compose up --build`, not just a restart of already-warm containers. All three
  services came up healthy; confirmed via curl that nginx correctly proxies both
  `/api/auth/login` and `/actuator/health` to the backend container; confirmed via Playwright
  against the actual served-by-nginx frontend (port 8081, not the dev server) that login, the
  employee list (10,000 rows, self-seeded automatically), and the reports dashboard all render
  with zero console errors — the same verification bar as every UI milestone before it, now
  against the artifact that will actually ship.
- **M12 (deploy, in progress)**: Fly.io was the first choice (deploys Docker containers directly,
  matching the existing Dockerfiles most closely) but `fly launch` failed immediately with
  "requested machine count exceeds organization limit" - a brand-new account has a 0-machine
  limit until a payment method is on file, even for free-tier usage. Surfaced this to the user
  rather than working around it (there's no workaround short of adding a card), and switched to
  Render on their choice, which needs no card for web services or its 90-day free Postgres.
  Render's Blueprint (`render.yaml`) needed two real adjustments beyond just pointing at the
  existing Dockerfiles, both made *before* attempting a deploy by checking Render's actual
  documented behavior (via WebFetch) rather than guessing and iterating against a slow
  build-and-deploy cycle:
  1. Render provides Postgres as a single `DATABASE_URL` connection string
     (`postgres://user:pass@host:port/db`), not separate host/user/password env vars the way
     `docker-compose`'s `postgres` service does - and Spring's `spring.datasource.url` needs a
     `jdbc:postgresql://` URL with credentials as separate properties. Added
     `backend/docker-entrypoint.sh`, which parses `DATABASE_URL` (when present - local
     `docker-compose` doesn't set it, so the existing `docker` profile's hardcoded datasource is
     untouched) into the three Spring properties before exec-ing the JVM. Verified the parsing
     logic in isolation against a realistic connection string before wiring it into the Dockerfile.
  2. Render (like Fly) gives the backend and frontend separate public HTTPS URLs rather than a
     shared private Docker network, so nginx's `proxy_pass http://backend:8080` (correct for
     `docker-compose`) wouldn't resolve at all. Made the backend target configurable: renamed
     `nginx.conf` to `nginx.conf.template` (official nginx image auto-envsubst's `*.template`
     files at container start) with a `${BACKEND_URL}` placeholder, and set
     `NGINX_ENVSUBST_FILTER=^BACKEND_URL$` in the Dockerfile - a documented but easy-to-miss
     requirement, since unscoped `envsubst` would also blank out nginx's own `$host`/
     `$remote_addr`/etc. (they look like shell variables too, and are unset in the container's
     real environment). `docker-compose.yml` now passes `BACKEND_URL=http://backend:8080`
     explicitly; `render.yaml` passes the deployed backend's public URL instead. Re-verified the
     entire local stack end-to-end after both changes (`docker compose down -v` +
     `docker compose up --build`, confirmed via `docker exec` that only `$BACKEND_URL` was
     substituted in the rendered nginx config, and a full login round-trip through nginx) before
     considering this deploy-ready - a regression here would have broken every environment, not
     just the new one.
  Render Blueprint deployment itself requires connecting a GitHub account and applying the
  Blueprint from Render's dashboard - steps only the account owner can take. `docs/deployment.md`
  documents the exact manual steps, including verifying/correcting the `CORS_ALLOWED_ORIGINS`/
  `BACKEND_URL` env vars against Render's actually-assigned service hostnames (the render.yaml
  values are a same-as-service-name best guess, since Render's Blueprint spec has no clean way to
  reference one service's URL from another's env var when both are freshly created together).
- **JD gap check (post-M12, pre-deploy)**: the user asked whether Redis could be added anywhere -
  answered directly that it shouldn't be, since it would contradict two already-documented
  decisions (no report caching, stateless JWT specifically to avoid needing shared session state)
  for no problem the app actually has. That prompted a broader "did we miss anything from the JD"
  check, which surfaced two real gaps against explicit "must-have" lines: no CI/CD pipeline, and
  no demonstrated multithreading (only `ThreadLocalRandom` in the seeder, which is a thread-safe
  RNG, not multithreading itself). On "make sure the app is multithreading-friendly," rather than
  assume either way, ran a dedicated audit sub-agent across every Spring-managed bean checking for
  mutable-after-construction fields, unsynchronized shared collections, or other race conditions
  under Tomcat's default multi-threaded request handling. Result: clean - every bean already
  follows `private final`-fields-via-constructor-injection, so nothing needed fixing there. The
  audit's one actionable finding was specifically about the *planned* change, not an existing bug:
  `EmployeeSeedGenerator`'s `Faker` field isn't documented as thread-safe for concurrent use, which
  would matter the moment seed generation was parallelized. Implemented `DataSeeder`'s
  `ExecutorService`/`CompletableFuture`-based parallel chunk generation with that fix already
  applied (one `EmployeeSeedGenerator` per task, never shared across threads) rather than
  discovering the race after the fact. Verified against a real re-seed: full test suite still
  green, identical deterministic `ACME-000001..010000` numbering (no gaps, no duplicates - computed
  per-chunk from arithmetic rather than a shared mutable counter), zero corrupted names, consistent
  ~550ms total time (expected - this was a demonstration of correct concurrency, not a performance
  fix, since generation was never the bottleneck; documented honestly as such in
  `docs/tradeoffs.md` rather than overclaiming a speedup that didn't materialize).
- **CI/CD (closing the last JD gap)**: added `.github/workflows/ci.yml` - three jobs (backend
  tests via Testcontainers, which just work on GitHub-hosted runners since Docker is already
  present; frontend unit tests + production build; both Dockerfiles built to validate the
  deployable artifacts on every push, not just the one time this was checked manually locally).
  Verified via `gh run watch` on the real push, not just that the YAML was well-formed: all three
  jobs passed on the first run (`docker-build` 1m30s, `backend` 1m47s including Testcontainers,
  `frontend` 27s) - a workflow file is worth nothing until it's been seen to actually pass.
- (Further milestones logged here as they land.)
