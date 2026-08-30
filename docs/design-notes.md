# Design Notes

Practical notes on how the codebase is organized and why — a map for a reviewer, distinct from
`architecture.md` (the system diagram) and `tradeoffs.md` (decisions and their reasoning).

## Backend conventions

- **Layering**: Controller → Service → Repository. Controllers never touch repositories directly;
  services never return JPA entities to controllers (always DTOs, mapped by a small dedicated
  `*Mapper` class per feature — no MapStruct, plain Java methods, since the mappings are simple
  enough that generated-code indirection wouldn't earn its keep).
- **No service interfaces**: `EmployeeService`, `SalaryHistoryService`, etc. are concrete classes,
  not `interface` + `Impl` pairs. There's exactly one implementation of each; an interface would
  be pure ceremony (YAGNI).
- **DTOs are Java records**: immutable, no Lombok needed for simple data carriers. Entities use
  Lombok (`@Getter`/`@Setter`/`@Builder`) since JPA requires mutability and no-args constructors
  that records can't provide.
- **Validation**: Jakarta Bean Validation annotations on request DTOs (`@NotBlank`, `@DecimalMin`,
  etc.), enforced automatically via `@Valid` on controller method parameters. CSV import rows are
  validated manually instead (see `CsvImportService`) since there's no framework support for
  validating parsed CSV records the way there is for request bodies.
- **Exceptions**: one `GlobalExceptionHandler` (`@RestControllerAdvice`) maps
  `ResourceNotFoundException` → 404, Bean Validation failures → 400 with per-field messages, Spring
  Security `AuthenticationException` → 401, and anything else → 500. A single consistent
  `ErrorResponse` JSON shape across the whole API.
- **Migrations**: Flyway, one file per schema change (`V1__...sql`, `V2__...sql`, ...), never
  squashed — the migration history is itself a readable record of schema evolution.

## Frontend conventions

- **Standalone components only** — no `NgModule` anywhere in the app; Angular's current default
  and simpler to reason about for a project this size.
- **Signals for local component/service state** (loading flags, current data), **RxJS observables**
  for HTTP calls and event streams (form `valueChanges`, debounced search). Not a hard rule, just
  what fit naturally at each call site.
- **`inject()` over constructor injection** in components that need a field initializer to run
  before the constructor body would otherwise execute (e.g. building a reactive form eagerly) —
  used consistently once needed in one component, for consistency across the codebase rather than
  mixing both styles arbitrarily.
- **Route-level code splitting**: every feature is `loadComponent`-ed. The one non-obvious
  consequence (documented in `docs/ai-usage-log.md`'s M9 entry): a provider needed only by one
  lazy route must be registered on that route's *component*, not in `app.config.ts` or even the
  route's own `providers` array in `app.routes.ts` — that file is always eagerly loaded, so
  anything statically imported there still lands in the main bundle.
- **One shared HTTP interceptor** (`jwt.interceptor.ts`) attaches the bearer token and handles 401
  by logging out and redirecting to `/login` — no per-service auth-header boilerplate.

## Testing conventions

- **Backend**: JUnit 5 + Mockito for pure unit tests (no Spring context — fast, e.g.
  `EmployeeServiceTest`), Testcontainers + real PostgreSQL for anything touching the database or
  the full request pipeline (`*ControllerIT` classes). See `tradeoffs.md` for why Postgres, not
  H2. Integration test classes that seed their own fixture data and share one Testcontainers
  instance per class use `@Transactional` so each test method's changes roll back, rather than
  polluting later tests in the same class (a real bug caught and fixed in M5, see
  `docs/ai-usage-log.md`).
- **Frontend**: minimal — one smoke test (`app.spec.ts`) confirming the root component bootstraps.
  UI behavior for every feature was instead verified visually with a throwaway Playwright script
  (kept out of the repo and out of `package.json` — a one-off verification tool, not a committed
  test suite) against the real running app and the real 10,000-row seeded dataset, since no browser
  extension was available in this session. See `docs/ai-usage-log.md`'s M7–M9 entries for what
  that caught, including two testing-tool quirks and one real data-integrity bug.

## Commit convention

Commits reference their milestone (`feat(M4): ...`, `feat(M8): ...`) per the implementation plan's
12-milestone sequence, so `git log` reads as a trace of how the solution evolved — the assessment
brief's explicit ask, not an incidental side effect.
