# Salary Management System

[![CI](https://github.com/brijeshbd/salary-management-system/actions/workflows/ci.yml/badge.svg)](https://github.com/brijeshbd/salary-management-system/actions/workflows/ci.yml)

Web-based salary management software for an HR Manager, built for ACME org (10,000 employees,
multiple countries). See [`REQUIREMENTS.md`](REQUIREMENTS.md) for goal/scope, and:

- [`docs/architecture.md`](docs/architecture.md) — system diagram, data model, auth flow
- [`docs/design-notes.md`](docs/design-notes.md) — codebase conventions and structure
- [`docs/tradeoffs.md`](docs/tradeoffs.md) — decisions and the reasoning behind them
- [`docs/performance.md`](docs/performance.md) — pagination, indexing, N+1 avoidance, seeding
- [`docs/ai-usage-log.md`](docs/ai-usage-log.md) — how AI tools were used throughout the build
- [`docs/deployment.md`](docs/deployment.md) — deploying to Render

**Stack:** Java 21 / Spring Boot / Spring Data JPA (Hibernate) / Gradle / PostgreSQL (backend),
Angular / TypeScript / Angular Material (frontend).

## Backend — local setup

1. Start Postgres:
   ```
   docker compose up -d postgres
   ```
2. Run the app (dev profile, connects to the Postgres above):
   ```
   cd backend
   SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
   ```
3. Health check: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`

## Backend — tests

```
cd backend
./gradlew test
```

Integration tests use Testcontainers (real PostgreSQL in a disposable container) — Docker must
be running.

## Seeding 10,000 employees

With Postgres running (see above), run the backend with the `seed` profile added:

```
cd backend
SPRING_PROFILES_ACTIVE=dev,seed ./gradlew bootRun
```

This generates 10,000 employees with realistic department/country/grade distributions and 1-4
salary-history records each, via batched JDBC inserts (completes in well under a second locally).
It's idempotent — safe to re-run, it skips if the `employee` table already has rows. Stop the app
(Ctrl+C) once you see `Seeding complete` in the logs; it keeps running as a normal web server
afterwards.

## Authentication

Every endpoint except `POST /api/auth/login` requires a JWT bearer token. One HR Manager account
is seeded automatically on first boot (any profile) — dev-only default credentials:

- Email: `admin@acme.com` (override with `HR_ADMIN_EMAIL`)
- Password: `changeit` (override with `HR_ADMIN_PASSWORD` — **set this for any real deployment**)

```
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@acme.com","password":"changeit"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/employees
```

## Frontend — local setup

With the backend running (see above):

```
cd frontend
npm install
npm start   # ng serve, http://localhost:4200
```

The dev server calls the API directly at `http://localhost:8080/api` (see
`src/environments/environment.ts`) — a cross-origin request in local dev, which is why the backend
has CORS configured for `http://localhost:4200`. Sign in with the default HR admin credentials
above.

## Frontend — tests

```
cd frontend
npm test
```

## Full stack via Docker Compose

No local Java/Node install needed — builds and runs Postgres, the backend, and the frontend
(served by nginx, which reverse-proxies `/api` to the backend — same-origin, so CORS doesn't come
into it here):

```
docker compose up --build
```

- App: http://localhost:8081 (sign in with the default HR admin credentials above)
- API directly: http://localhost:8080
- The backend self-seeds 10,000 employees on first boot (idempotent — safe on every restart)

See [`docs/architecture.md`](docs/architecture.md) for the deployment topology diagram.
