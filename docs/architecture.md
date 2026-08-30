# Architecture

## System Overview

```mermaid
flowchart LR
    subgraph Browser
        SPA["Angular SPA<br/>(standalone components)"]
    end

    subgraph "Backend (Spring Boot)"
        API["REST API<br/>/api/**"]
        JWT["JWT Auth Filter"]
        SVC["Services<br/>(employee, salary, reporting,<br/>import/export, auth)"]
    end

    DB[("PostgreSQL")]

    SPA -- "HTTPS + Bearer JWT" --> API
    API --> JWT
    JWT --> SVC
    SVC -- "Spring Data JPA / JdbcTemplate" --> DB
```

Two independently deployable artifacts (see `docker-compose.yml`): a Spring Boot API and an
Angular single-page app served by nginx, which also reverse-proxies `/api` to the backend in the
deployed stack (see `docs/performance.md` and the CORS note in `docs/tradeoffs.md` — CORS only
matters in local dev, where the two run on different ports).

## Data Model

```mermaid
erDiagram
    EMPLOYEE ||--o{ SALARY_RECORD : "has history"
    EMPLOYEE {
        bigint id PK
        varchar employee_code UK
        varchar first_name
        varchar last_name
        varchar department
        varchar country
        varchar job_grade
        boolean active
        timestamp created_at
        timestamp updated_at
    }
    SALARY_RECORD {
        bigint id PK
        bigint employee_id FK
        numeric base_salary
        varchar currency
        date effective_date
        varchar reason
        timestamp created_at
    }
    HR_USER {
        bigint id PK
        varchar email UK
        varchar password_hash
        varchar role
    }
```

`SalaryRecord` is append-only and deliberately **unidirectional** (no `salaryRecords` collection on
`Employee`) — see `docs/tradeoffs.md` for why, and `docs/performance.md` for how "current salary"
is resolved without an N+1 query. `HrUser` has no foreign-key relationship to `Employee` — it's
authentication, not a domain entity.

## Backend Package Structure (package-by-feature)

```
com.acme.salary
├── employee/          Employee entity, repository, service, controller, search, DTOs
├── salary/             SalaryRecord entity, repository, history service/controller, DTOs
├── auth/               HrUser, JWT filter/service, login controller, user seeder
├── importexport/       CSV import/export services + controller
├── reporting/           Aggregation queries + controller
├── seed/                Synthetic 10k-employee data generator (seed profile only)
├── config/              Security config, JPA auditing config
└── common/              Shared exception handling, PageResponse envelope
```

Each feature package is internally layered (entity → repository → service → controller), but
packages are organized by feature, not by layer — keeps a vertical slice (e.g. everything about
salary history) navigable as a unit. See `docs/design-notes.md` for the full layering convention.

## Authentication Flow

```mermaid
sequenceDiagram
    participant U as HR Manager (browser)
    participant A as Angular SPA
    participant B as Spring Boot API
    participant D as PostgreSQL

    U->>A: enters email/password
    A->>B: POST /api/auth/login
    B->>D: look up HrUser by email
    B->>B: AuthenticationManager verifies password (BCrypt)
    B-->>A: { token, expiresAt } (JWT, 8h expiry)
    A->>A: store token (localStorage) + auth signal
    A->>B: subsequent requests, Authorization: Bearer <token>
    B->>B: JwtAuthenticationFilter validates signature + expiry,<br/>trusts claims (no DB round-trip)
    B-->>A: 200 (or 401 if token missing/invalid/expired)
```

## Frontend Structure

```
frontend/src/app/
├── core/
│   ├── auth/       AuthService (signal-backed), authGuard, jwt.interceptor
│   ├── api/         EmployeeService, SalaryService, ImportExportService, ReportingService
│   └── models/      TypeScript interfaces mirroring backend DTOs
├── features/
│   ├── login/
│   ├── employees/    list, detail, form, salary-adjustment-dialog
│   ├── import/       csv-import
│   └── reports/       reports-dashboard
└── shared/
    ├── pipes/         currencyByCode
    ├── directives/    numericOnly
    ├── components/    confirm-dialog
    └── utils/          date formatting
```

Standalone components throughout (no NgModules), lazy-loaded per route via `loadComponent`.
State is plain services + Angular signals/RxJS, not NgRx — see `docs/tradeoffs.md`.

## Deployment Topology (M11/M12)

```mermaid
flowchart TB
    subgraph "docker-compose"
        NGINX["nginx<br/>(serves Angular build,<br/>proxies /api)"]
        BACKEND["backend container<br/>(Spring Boot)"]
        PG[("postgres container")]
    end
    Internet -->|":80"| NGINX
    NGINX -->|"/api/**"| BACKEND
    BACKEND --> PG
```
