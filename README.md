# Salary Management System

Web-based salary management software for an HR Manager, built for ACME org (10,000 employees,
multiple countries). See [`REQUIREMENTS.md`](REQUIREMENTS.md) for goal/scope, and
[`docs/`](docs/) for architecture, trade-offs, performance notes, and the AI-usage log.

**Stack:** Java 21 / Spring Boot / Spring Data JPA (Hibernate) / Gradle / PostgreSQL (backend),
Angular / TypeScript (frontend, added in a later milestone).

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
