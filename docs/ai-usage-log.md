# AI Usage Log

This project was built with Claude Code (Anthropic's agentic CLI) as the primary development
tool. This log tracks how it was used and the reasoning behind the key decisions made along the
way. It was kept up to date as the build progressed, not written after the fact. For the actual
prompt text at the points that shaped the project most - both the steering prompts given to
Claude Code and the delegation prompts it sent to its own sub-agents - see
[`ai-prompts.md`](ai-prompts.md).

## Requirements & scoping

- Started as an open-ended "help me build a project" conversation. Claude Code asked clarifying
  questions instead of guessing at scope.
- Once the actual assessment brief (a PDF) was handed over, Claude Code read it directly,
  pulled out the hard technical constraints (Java/Spring/Hibernate/Gradle/JUnit, Angular/
  TypeScript, a 10,000-employee seed script, deployment, tests, incremental commits, supporting
  artifacts) that hadn't come through in the earlier verbal description, and folded all of it
  into `REQUIREMENTS.md`.
- The target job description (Java/Spring or Micronaut, Hibernate, Gradle, JUnit, Angular/RxJS)
  came from the user and was used to lock in the stack. Choices like Spring Boot over Micronaut,
  Gradle over Maven, and PostgreSQL were laid out with their trade-offs for the user to sign off
  on, rather than picked silently.

## Architecture design

- Before any code was written, Claude Code went into plan mode and handed the architecture design
  to a sub-agent, giving it the full set of requirements and constraints and asking it to come
  back opinionated - one recommendation per decision, not a menu of options. That covered the
  data model, migration strategy, API surface, auth approach, seeding strategy, testing strategy,
  frontend structure, performance considerations, and a 12-milestone build sequence.
- The design that came back was reviewed, adjusted into a concrete implementation plan, and
  approved by the user before a single line of code went in.
- The decisions that plan produced, along with the reasoning behind each, live in
  [`tradeoffs.md`](tradeoffs.md).

## Build process

- **M1 (backend scaffold)**: generated through Spring Initializr (`start.spring.io`), then hand
  edited to add JWT (jjwt), DataFaker, Testcontainers, and Actuator - dependencies the initializer's
  picker doesn't cover directly. Checked end to end (Postgres via Docker Compose, `bootRun`,
  `/actuator/health` returning UP) before the first commit.
- **M2 (entities + migrations)**: ran into a real Spring Boot 4 gotcha here. Boot 4 splits
  autoconfiguration per starter now, instead of Boot 3's single `spring-boot-autoconfigure`
  jar. An earlier edit had kept only `org.flywaydb:flyway-database-postgresql` (the Postgres
  extension) and dropped `org.springframework.boot:spring-boot-starter-flyway`, which is actually
  what carries Flyway's Spring Boot autoconfiguration in v4. The result: Flyway silently never
  ran - no log output, no error - and Hibernate's `ddl-auto=validate` failed with "missing table
  [employee]" because no migration had ever applied. Found it by running with `--debug` to pull
  the conditional-evaluation report, which showed `FlywayAutoConfiguration` wasn't activating,
  then added the missing starter. Re-checked afterward: migrations `V1`-`V4` applied cleanly, all
  the planned indexes and unique constraints existed in Postgres, and the health check was still
  green.
- **M3 (seeding)**: also caught, while working on this milestone, that `application-dev.yml`'s
  JDBC URL had `rewriteBatchedStatements=true` set - that's the MySQL driver's batch-rewrite flag,
  not PostgreSQL's (`reWriteBatchedInserts`, capital W). Fixed before it could quietly no-op batch
  performance. The generator (`EmployeeSeedGenerator`) builds each employee's full salary history
  backward from a target "current" salary computed from a country/grade band, so the history stays
  internally consistent, and everything inserts through raw `JdbcTemplate` batches - multi-row
  `INSERT ... RETURNING id` for employees, `batchUpdate` for salary records - instead of JPA saves
  in a loop. Verified: 10,000 employees plus 25,204 salary records seeded in 517ms. Distributions
  were checked with SQL (headcount and avg salary by country, department, and grade; salary-history
  depth spread across 1-4 records per employee), all matching the intended weights. Re-running the
  profile correctly no-ops on a second pass.
- **M4 (Employee CRUD API + tests)**: two more Boot 4 modularization surprises, same flavor as
  M2's Flyway issue. First, `@AutoConfigureMockMvc` isn't in `spring-boot-test-autoconfigure`
  anymore - it moved into a new `spring-boot-starter-webmvc-test` module, under the package
  `org.springframework.boot.webmvc.test.autoconfigure`. Found that by unzipping candidate jars and
  grepping for the class file, since the old import just silently fails to exist rather than
  erroring with anything useful. Second, Boot 4 pulls in Jackson 3, which renamed its Maven group
  and base package from `com.fasterxml.jackson.*` to `tools.jackson.*`. Rather than chase that
  rename through test code, the integration tests read response JSON with `JsonPath.read(...)`
  (already a transitive dependency via MockMvc's own `jsonPath()` matcher) instead of
  `ObjectMapper`, sidestepping the problem entirely. Also had to pin `org.testcontainers:*`
  versions explicitly, since Boot's BOM manages `spring-boot-testcontainers` (the
  `@ServiceConnection` glue) but not the Testcontainers library itself.
  A couple of design notes from this milestone: employee codes created through the API get an
  `EMP-` prefix off a dedicated sequence, while seeded data uses `ACME-`, so the two can never
  collide no matter how large the seed gets. The list endpoint resolves current salary for a
  whole page in one query (Postgres `DISTINCT ON`, keyed by employee id) instead of one query per
  row, in line with the N+1-avoidance decision in `tradeoffs.md`. A deliberately permissive
  `SecurityConfig` placeholder (permit-all) went in too, so the API would be usable before real
  auth existed - called out here and in code so it doesn't get mistaken for an oversight later; it
  was replaced with JWT-based rules in the auth milestone.
  Verified: 6 unit tests (Mockito, no Spring context) plus 4 Testcontainers integration tests all
  green, and a manual smoke test of the full CRUD lifecycle (list, create, get, update, soft
  delete, 404) against the real 10,000-row dev database behaved exactly as expected.
- **M5 (salary history, search/filter, CSV import/export, reporting)**: the largest milestone,
  landed as four sub-commits.
  - *Salary history* was a straightforward append-only GET/POST, reusing the existing
    current-salary-resolution pattern.
  - *Search/filter*: salary-range filtering can't be expressed as a JPA Specification since
    current salary isn't an actual column, so it's a native query using `LEFT JOIN LATERAL`
    instead of a fragile correlated max-date subquery. It returns ids only and hydrates them the
    same N+1-safe way as the M4 list endpoint. Also fixed a test-isolation bug here: a shared
    Testcontainers Postgres instance across test methods in one class meant fixture data kept
    piling up across tests, so `@Transactional` (rollback per test) was added to fix it.
  - *CSV import/export* supports partial success - one bad row doesn't fail the whole file - and
    reuses `EmployeeService.create` per valid row instead of duplicating creation logic.
  - *Reporting* is where a real product-correctness issue turned up, rather than another Spring
    Boot version gotcha. Grouped reports (by department, by grade) and org-wide totals
    deliberately never sum across currencies, since FX conversion is out of scope - summing USD
    and INR together, for example, would produce a number that means nothing. Every aggregate is
    instead broken out per currency, one row per group-and-currency pair.
    Smoke-testing the "raises since <date>" report against the real 10,000-row dataset surfaced a
    seeding realism bug left over from M3: `EmployeeSeedGenerator` always dated each employee's
    current salary record within the last 0-6 months, so effectively every employee looked like
    they'd just gotten a raise. `since=2024-01-01` returned all 10,000 rows, which made the report
    useless as a demo. Fixed by widening that window to 0-24 months in `EmployeeSeedGenerator`,
    then re-seeded and re-verified: the report now differentiates meaningfully - 10,000 / 4,822 /
    1,182 / 380 for cutoffs of 2024-01-01, 2025-09-01, 2026-06-01, and 2026-08-01 respectively.
    This is the kind of thing that's easy to miss against a handful of hand-picked fixture rows
    but jumps out immediately at the full seeded scale, and it's a good argument for smoke-testing
    new features against the real 10k rows rather than just unit and integration test fixtures.
  All four sub-commits combined: 39 tests, full suite green.
- **M6 (auth)**: replaced the M4 permit-all placeholder with real Spring Security and stateless
  JWT. One more Spring Security 7 (bundled with Boot 4) package move to work around here:
  `AuthenticationConfiguration` isn't under `org.springframework.security.authentication.configuration`
  anymore - it moved up a level to
  `org.springframework.security.config.annotation.authentication.configuration`. Found the same
  way as the earlier moves: unzip the candidate jar, grep for the class file.
  A couple of design choices worth spelling out: the JWT filter trusts the token's own signed
  claims (email and role) instead of re-querying the database on every request, which is cheaper
  and there's nothing further to look up for a single-role app anyway. Login itself does go
  through the full Spring Security stack - `AuthenticationManager` to an auto-wired
  `DaoAuthenticationProvider`, built automatically from the `HrUserDetailsService` and
  `BCryptPasswordEncoder` beans already in context - rather than a hand-rolled credential check,
  to give a more faithful demonstration of the pattern the target JD asks for. A real HR user gets
  seeded unconditionally on startup (idempotent, any profile), since without it nobody could ever
  log in.
  For tests, rather than crafting a real JWT in every existing integration test class,
  `@WithMockUser(roles = "HR_MANAGER")` was added at the class level to the five pre-existing IT
  classes (they test their own business logic, not auth), and one dedicated `AuthControllerIT`
  exercises the real login-to-token-to-protected-endpoint flow end to end, plus the
  wrong-password, unknown-email, missing-token, and garbage-token failure cases. All 44 tests
  passed on the first run after wiring this up. The full flow was also checked by hand against the
  real dev database: 401 without a token, login succeeds, 200 with the token, 401 on a wrong
  password.
- **M7 (frontend scaffold + auth)**: `ng new` generates Angular 22 standalone components (no
  NgModule) by default now, which matched the plan without needing a flag. Two build errors are
  worth recording here. First, the Angular Material schematic
  (`ng add @angular/material --animations=enabled`) didn't actually install `@angular/animations`,
  which `provideAnimationsAsync()` needs at runtime - the build failed with "Could not resolve
  '@angular/animations/browser'" until it was added by hand. (It's flagged deprecated in favor of
  a newer native `animate.enter`/`animate.leave` API, but it's still what Material's own
  components need today, so it's the right dependency for now.) Second, a
  `readonly form = this.formBuilder.group(...)` field initializer running before
  constructor-parameter properties are assigned is a TypeScript initialization-order error - fixed
  by switching to `inject(FormBuilder)` field-style injection, which resolves before other field
  initializers run.
  The bigger catch came from manually verifying the login flow end to end with curl, since no
  browser tooling was available this session. The Angular dev server (localhost:4200) calling the
  API (localhost:8080) is a cross-origin request, and the backend had no CORS configuration at
  all - in a real browser, every API call would have been silently blocked with nothing surfaced
  until someone opened dev tools. That was caught by sending an OPTIONS preflight with curl and an
  `Origin` header and checking for `Access-Control-Allow-Origin` in the response - there wasn't
  one. A `CorsConfigurationSource` bean went in (allowed origins from an env-overridable property,
  defaulting to `http://localhost:4200`), and the header showed up correctly on both the preflight
  and the actual request afterward. This only matters for local dev, since the deployed stack
  (M11) serves the frontend through nginx same-origin and takes CORS out of the picture entirely -
  but it's exactly the kind of gap a curl-only backend test suite has no way to catch, because
  CORS is a browser-enforced restriction rather than a server-side error the API would ever
  return to a non-browser client. Worth flagging as a real limitation of this session's testing:
  without browser tooling, UI behavior - rendering, click-through flows, console errors - could
  only be checked by code review and a successful build/serve, not by actually driving the page.
  The CORS bug is a concrete example of exactly the kind of issue that gap can hide until someone
  opens it in a real browser.
- **M8 (employee list, detail/edit, salary adjustment)**: the browser-tooling gap from M7 got
  solved properly here instead of worked around again - Playwright (kept out of `package.json`,
  since it's a one-off verification tool rather than a committed test) and Chromium were installed
  locally, giving real screenshots and DOM assertions instead of curl-only guesswork. That paid off
  immediately:
  - Login through to the employee list rendered correctly against the real 10,000-row dataset on
    the first try - the paginator read "1-20 of 10000", per-currency formatting was correct across
    USD/CAD/GBP/EUR/INR, and there were zero console errors.
  - Clicking into the employee detail page, recording a salary adjustment, and editing the profile
    all appeared to fail at first - the base-salary field kept showing "Required" even after
    `page.fill()` had set it. Tracking this down took a few rounds of isolation: an isolated fill
    worked fine, but the same fill inside the full flow didn't, and inspecting the live Angular
    `FormControl` confirmed the value really was empty, not just visually so. It turned out to be
    two unrelated issues stacked on top of each other. First, Vite's dev-server dependency
    pre-bundler discovered the datepicker and dialog modules for the first time mid-test and force
    reloaded the page, wiping in-progress form state - a one-time dev-server cold-start cost that
    doesn't exist in a production build. Second, and the more durable finding: Playwright's
    `page.fill()` sets the DOM value directly instead of dispatching real keystrokes, and this
    particular input has a custom `appNumericOnly` directive listening for `beforeinput` - the two
    don't compose, so Angular's own value-accessor sync ends up reverting the field a few dozen
    milliseconds after `fill()` returns. Switching to `page.keyboard.type()` (real keystrokes)
    confirmed this - it worked immediately, verified against the live `FormControl` value rather
    than just the DOM. This was a testing-tool quirk, not an app bug - a real user typing into the
    field is completely unaffected - but it's exactly the kind of false negative that would have
    burned time if not run to ground properly, so it's recorded here rather than left to be
    rediscovered in some later milestone that touches numeric inputs.
  Verified end to end via screenshots: recording an adjustment correctly appended a new, dated,
  reasoned salary-history row while leaving the prior row's own currency untouched - a Canadian
  employee's history correctly shows one CAD row and one newly added USD row, proof that the
  per-record currency model (not a single employee-level currency) actually works. Editing the
  profile correctly updated the header and persisted.
  Rounding out the milestone: the add-employee form (which auto-defaults currency from the
  selected country, but only when the user hasn't already touched currency themselves) and the CSV
  import screen were both verified end to end with Playwright too. A 3-row test CSV with one
  deliberately invalid country produced exactly "3 total / 2 succeeded / 1 failed," with the
  specific row number and message surfaced in the UI, matching the backend's partial-success
  design from M5. All test data created during verification - employees, imported rows - was
  deleted from the dev database afterward so it wouldn't pollute later screenshots or the eventual
  demo video.
- **M9 (reports dashboard)**: loaded the project's `dataviz` skill before writing any chart code,
  per its own trigger condition. That skill's procedure is built around multi-series categorical
  palettes, but this dashboard's two charts (headcount by country, pay-distribution counts) are
  both single-series bar charts where the x-axis already carries identity - a categorical rainbow
  per bar would just be noise, not signal. The skill's own "sequential = one hue" rule covers
  exactly this case, so both charts use one consistent, validated hue (slot 1 from the skill's
  reference palette) rather than running the full multi-hue validator, which is scoped to a
  problem - distinguishing many series - this dashboard doesn't actually have.
  Two things stood out here. First, a bundle-size lesson that took two attempts to get right:
  registering `provideCharts()` in `app.config.ts` pulled chart.js into the main bundle for all
  five routes, tripping the 500KB budget warning. Moving it into the `reports` route's own
  `providers` array didn't fix it either, because `app.routes.ts` is always eagerly loaded - only
  the lazy component import inside a route is actually deferred, so a static top-level `import`
  anywhere in that file still lands in the main chunk no matter which array references it. The
  fix was registering the provider inside `ReportsDashboardComponent`'s own `@Component` decorator
  instead, since that's the file actually sitting behind the lazy `loadComponent()` boundary. The
  bundle dropped from 588KB back to 376KB, under budget, with chart.js correctly isolated to a
  223KB chunk that only loads when someone visits `/reports`.
  Second, a real bug that charting exposed rather than caused: the initial "Headcount by Country"
  chart showed two separate "CA" bars. That wasn't an app defect - during the M8 salary-adjustment
  dialog test, a $999,999 USD raise had been recorded on a Canadian (CAD) employee and never
  cleaned up afterward, since M8's cleanup only deleted employees created during testing, not a
  mutated salary record on an existing seeded employee. Since the by-country report groups by
  (country, currency), that one stray record was enough to split Canada into two rows. Fixed by
  deleting the stray record and restoring the employee's name (also overwritten by that same
  earlier test) directly in the dev database, then re-verified the chart showed exactly 7 country
  bars. The general lesson here matters more than the specific fix: any manual-verification step
  that mutates an *existing* seeded record, not just ones it creates, needs the same disciplined
  cleanup, because aggregate reports will surface that kind of pollution far more visibly than a
  CRUD screen ever would.
  Also confirmed the currency switch on the pay-distribution chart re-renders with an entirely
  different bucket scale for INR versus USD, correctly not sharing an axis (the skill's "one axis"
  rule) - which reinforces why per-currency reporting, not a single blended chart, is the right
  call here.
- **M10 (docs)**: pulled together `architecture.md` (with Mermaid diagrams - GitHub renders these
  natively, so no separate diagramming tool was needed), `design-notes.md`, and `performance.md`
  from decisions that had mostly already been made and recorded along the way - in commit
  messages, code comments, and this log - across M1 through M9, rather than designed fresh at the
  end. `tradeoffs.md` and this log were both re-read in full for consistency before writing the
  new docs, and no corrections were needed.
- **M11 (dockerize)**: multi-stage Dockerfiles for both backend (Gradle build to JRE runtime) and
  frontend (`npm run build` to nginx). Two adjustments were made proactively here rather than
  discovered by failure. First, `-alpine` JRE/JDK base images were chosen specifically so `wget`
  (via BusyBox) is available for the backend's Docker healthcheck without installing anything
  extra - checked this before writing the healthcheck, not after it failed. Second,
  `docker-compose`'s `backend` service runs with `SPRING_PROFILES_ACTIVE=docker,seed`
  permanently, not just for an initial run, since `DataSeeder` and `HrUserSeeder` are both
  idempotent - leaving `seed` always active makes the whole stack self-seeding on any restart with
  no separate manual step, which is what "verified from a clean `docker-compose up`" should
  actually mean.
  Verified genuinely from a clean state: `docker compose down -v` (destroying the postgres volume)
  before `docker compose up --build`, not just a restart of already-warm containers. All three
  services came up healthy. Curl confirmed nginx correctly proxies both `/api/auth/login` and
  `/actuator/health` to the backend container, and a Playwright pass against the actual
  served-by-nginx frontend (port 8081, not the dev server) confirmed login, the employee list
  (10,000 rows, self-seeded automatically), and the reports dashboard all render with zero console
  errors - the same verification bar as every UI milestone before it, now against the artifact
  that will actually ship.
- **M12 (deploy, in progress)**: Fly.io was the first choice, since it deploys Docker containers
  directly and matched the existing Dockerfiles most closely, but `fly launch` failed immediately
  with "requested machine count exceeds organization limit" - a brand-new account has a 0-machine
  limit until a payment method is on file, even for free-tier usage. Rather than work around it (
  there isn't one short of adding a card), this was surfaced directly, and the deploy target
  switched to Render on the user's choice, which needs no card for web services or its 90-day free
  Postgres.
  Render's Blueprint (`render.yaml`) needed two real adjustments beyond just pointing at the
  existing Dockerfiles, both made before attempting a deploy by checking Render's actual
  documented behavior (via WebFetch) rather than guessing and iterating against a slow
  build-and-deploy cycle.
  First, Render provides Postgres as a single `DATABASE_URL` connection string
  (`postgres://user:pass@host:port/db`), not the separate host/user/password env vars
  `docker-compose`'s `postgres` service uses - and Spring's `spring.datasource.url` needs a
  `jdbc:postgresql://` URL with credentials as separate properties. `backend/docker-entrypoint.sh`
  parses `DATABASE_URL` (only when present - local `docker-compose` doesn't set it, so the
  existing `docker` profile's hardcoded datasource is untouched) into the three Spring properties
  before exec-ing the JVM. The parsing logic was verified in isolation against a realistic
  connection string before it was wired into the Dockerfile.
  Second, Render (like Fly) gives the backend and frontend separate public HTTPS URLs instead of a
  shared private Docker network, so nginx's `proxy_pass http://backend:8080` (correct for
  `docker-compose`) wouldn't resolve at all there. The backend target was made configurable
  instead: `nginx.conf` became `nginx.conf.template` (the official nginx image auto-envsubst's
  `*.template` files at container start) with a `${BACKEND_URL}` placeholder, and
  `NGINX_ENVSUBST_FILTER=^BACKEND_URL$` was set in the Dockerfile - a documented but easy-to-miss
  requirement, since an unscoped `envsubst` would also blank out nginx's own `$host`,
  `$remote_addr`, and similar variables, since they look like shell variables too and are unset in
  the container's real environment. `docker-compose.yml` now passes
  `BACKEND_URL=http://backend:8080` explicitly, and `render.yaml` passes the deployed backend's
  public URL instead. The entire local stack was re-verified end to end after both changes
  (`docker compose down -v` plus `docker compose up --build`, confirming via `docker exec` that
  only `$BACKEND_URL` was substituted in the rendered nginx config, and a full login round trip
  through nginx) before this was considered deploy-ready - a regression here would have broken
  every environment, not just the new one.
  The Render Blueprint deployment itself requires connecting a GitHub account and applying the
  Blueprint from Render's dashboard - steps only the account owner can take. `docs/deployment.md`
  documents the exact manual steps, including verifying and correcting the
  `CORS_ALLOWED_ORIGINS`/`BACKEND_URL` env vars against Render's actually-assigned service
  hostnames, since the `render.yaml` values are a same-as-service-name best guess - Render's
  Blueprint spec has no clean way to reference one service's URL from another's env var when both
  are freshly created together.
- **JD gap check (post-M12, pre-deploy)**: the user asked whether Redis could fit in anywhere. The
  answer was a direct no - it would contradict two decisions already made and documented (no
  report caching, stateless JWT specifically to avoid needing shared session state) for a problem
  the app doesn't actually have. That question led to a broader "did we miss anything from the JD"
  check, which turned up two real gaps against explicit must-have lines: no CI/CD pipeline, and no
  demonstrated multithreading (the seeder used `ThreadLocalRandom`, which is a thread-safe RNG,
  not multithreading itself). On "make sure the app is multithreading-friendly," rather than
  assume either way, a dedicated audit sub-agent went through every Spring-managed bean checking
  for mutable-after-construction fields, unsynchronized shared collections, or other race
  conditions under Tomcat's default multi-threaded request handling. The result came back clean -
  every bean already follows the pattern of `private final` fields set through constructor
  injection, so nothing needed fixing there. The one actionable finding was about the planned
  change, not an existing bug: `EmployeeSeedGenerator`'s `Faker` field isn't documented as
  thread-safe for concurrent use, which would matter the moment seed generation was parallelized.
  `DataSeeder`'s `ExecutorService`/`CompletableFuture`-based parallel chunk generation was
  implemented with that fix already built in - one `EmployeeSeedGenerator` per task, never shared
  across threads - rather than discovering the race after the fact. Verified against a real
  re-seed: the full test suite stayed green, the deterministic `ACME-000001..010000` numbering had
  no gaps or duplicates (computed per-chunk from arithmetic, not a shared mutable counter), no
  names came out corrupted, and the total time stayed around 550ms. That last point is worth being
  honest about: this was a demonstration of correct concurrency, not a performance fix, since
  generation was never the bottleneck to begin with - `docs/tradeoffs.md` says so directly instead
  of overclaiming a speedup that never materialized.
- **CI/CD (closing the last JD gap)**: `.github/workflows/ci.yml` added three jobs - backend tests
  via Testcontainers (which just work on GitHub-hosted runners since Docker is already present),
  frontend unit tests plus a production build, and both Dockerfiles built to validate the
  deployable artifacts on every push instead of only the one time this was checked manually. This
  was verified with `gh run watch` against a real push, not just by eyeballing that the YAML was
  well formed: all three jobs passed on the first run (`docker-build` in 1m30s, `backend` in 1m47s
  including Testcontainers, `frontend` in 27s). A workflow file isn't worth much until it's
  actually been seen to pass.
- **M12 (deploy, verified)**: the user deployed the Render Blueprint. Both guessed cross-service
  URLs (`salary-mgmt-backend`/`salary-mgmt-frontend.onrender.com`) turned out correct on the first
  try, confirmed directly with curl against both the backend's own health endpoint and the
  frontend's nginx-proxied one, both returning `{"status":"UP"}`. One real friction point:
  `HR_ADMIN_PASSWORD` used `generateValue: true`, and the user couldn't locate the generated value
  in Render's dashboard. Rather than iterate on dashboard-navigation instructions, the actual cause
  got fixed - switched to a fixed `value: changeit`, the same credential already documented
  publicly in `README.md` for local dev. This app has no real secret behind that login (synthetic
  seed data, no real PII), so a Render-generated mystery value was solving a problem that didn't
  need solving and just added friction for anyone who needs to demo the app later.
  That fix alone didn't unblock login, though. `HrUserSeeder` is deliberately idempotent - it skips
  seeding if an HR user row already exists - so the already-deployed database still had the
  original Render-generated password hash regardless of what `render.yaml` now said. That's
  correct behavior for the seeder's actual job, but it's an operational gap for changing a
  credential after first deploy. This was diagnosed by testing login and getting a clean 401 (not
  a timeout or a 500), which pointed at "wrong password in the DB" rather than "app
  misconfigured." Fixed directly: connected to Render's Postgres through its external connection
  string (`docker run --rm postgres:16-alpine psql "$URL"`, no local psql client needed),
  generated a real bcrypt hash for `changeit` using the project's own `spring-security-crypto` jar
  via `jshell` (which guarantees the exact same hashing the app itself uses, instead of an ad-hoc
  script that might not match `BCryptPasswordEncoder`'s defaults), and updated the stored row
  directly. The general fix is documented in `docs/deployment.md` for anyone who runs into the
  same idempotent-seeder-vs-changed-credential gap later.
  Final verification happened against the actual live URL, not as a stand-in for "should work":
  login succeeds, an authenticated request confirms all 10,000 self-seeded employees are present
  through both the direct backend URL and the frontend's nginx-proxied path, and a Playwright pass
  against the real `https://salary-mgmt-frontend.onrender.com` shows the employee list and
  reports dashboard - including the currency charts - rendering correctly with zero console
  errors. Same screenshot-based bar used for every UI milestone since M8, just now against the
  artifact a reviewer will actually open.
- **Login/employee-list redesign and Incubyte brand alignment**: the user flagged the login
  screen's layout as cramped - the title, sign-in form, and labels all crowded together - and
  separately asked for the post-login employee list to look "attractive... short and sweet and
  simple and sobar" instead of the flat, borderless layout it started with. Both were redesigned
  around a shared card-based visual language (white cards, rounded corners, soft shadow) over a
  light background wash, checked with Playwright screenshots against the real running app before
  and after each change. The user then asked for the whole app's color and typography to match
  `incubyte.co` specifically, so the live site's actual CSS was fetched directly rather than
  guessed at, pulling out its real palette (deep teal `#014D43` primary, a chartreuse accent) and
  font pairing (Fraunces for headings, Inclusive Sans for body text). That got mapped onto Angular
  Material's M3 theming API: the closest built-in palette by hue, `cyan-palette` (whose tone 30,
  `#004f4f`, is nearly identical to the brand teal), supplies well-formed supporting tones, with a
  direct `primary`/`on-primary` override pinning the exact brand hex for whatever's actually
  visible. The chart bar color was re-picked from the same hue family and run through the
  project's `dataviz` skill validator instead of just eyeballed - the two darkest brand-teal tones
  both failed the chroma-floor check (they read as gray), so the darkest tone that actually passed
  was used instead. One real bug turned up along the way: the same request's search field had a
  label too long for its box, which rendered as clipped, overlapping text - fixed by shortening
  the label and widening the field. A separate follow-up ("too much scrolling" on Reports) led to
  discovering that the Recent Raises table was silently rendering over 5,000 unpaginated rows on
  one long page - the dashboard was split into tabs and client-side pagination was added to that
  table.
- **Reactivating a deactivated employee (a real gap, reported by the user)**: the user asked
  "once we update with Inactive it can not be active??" after noticing the detail page only ever
  showed a Deactivate button and no way back. Checking the backend directly, rather than assuming,
  confirmed there genuinely was no reactivate path anywhere - backend or frontend - just a one-way
  soft delete. `POST /api/employees/{id}/reactivate` was added along with a symmetric unit test
  next to the existing `deactivate_setsEmployeeInactive` test, and a Reactivate button now shows
  in place of Deactivate once an employee is inactive. Verified end to end with Playwright against
  the real app: Active, then Deactivate, then Inactive, then Reactivate, then Active again, with
  zero console errors throughout.
- **Report CSV export (closing a real requirements gap)**: the user asked "reports should be
  downloaded right, based on their requirements?" Checking `REQUIREMENTS.md` directly confirmed it
  explicitly promises "Export filtered views/reports to CSV," but only the employee list actually
  had that button - all five Reports views had no download capability at all. One export endpoint
  was added per report (headcount by country, pay distribution, by department, by grade, recent
  raises), each honoring the same filter the screen is currently showing - the raises export, for
  example, respects whatever "since" date is selected, not just today's default. This reused the
  existing `CsvExportService` pattern (Apache Commons CSV, one method per data shape) rather than
  inventing something new, and pulled the previously duplicated `downloadBlob()` helper out into a
  shared frontend util now that a second feature needs it. One real bug surfaced during manual
  verification: the very first test run came back with a 500, which turned out not to be a code
  defect but a stale backend process left running on port 8080 from earlier testing, still serving
  old code. After killing it and restarting clean, all five exports were re-verified end to end
  through Playwright - real file downloads, correct filenames, correct CSV headers and data -
  before the fix was trusted.
- (Further milestones logged here as they land.)
