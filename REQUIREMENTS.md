# Requirements: Salary Management Software — ACME Org

**Persona:** HR Manager
**Date:** 2026-08-30

## Problem Statement
ACME org's HR team manages salary data for 10,000 employees across multiple countries via Excel,
which is tedious. The goal is to let the HR Manager manage salary data through web-based software
and be able to answer questions about how the org pays people.

## Goal
Replace ACME's spreadsheet-based salary tracking with a web application that lets the HR Manager
manage salary data for ~10,000 employees across multiple countries, and answer questions about
compensation (e.g., "what's our average salary by department/country?", "who got a raise this
year?", "what's our total payroll cost?").

## Scope & Features (v1)

| Feature | Description |
|---|---|
| Employee salary records | Create, view, edit, deactivate/reactivate employee salary profiles: name, employee ID, department, country, currency, job grade/title, base salary, effective date. |
| Salary history | Track changes to salary over time (raises, adjustments) with effective dates — needed to answer "how has pay changed," not just "what is it now." |
| Bulk import | Import employee/salary data from CSV/Excel, so migrating off spreadsheets isn't a manual re-entry slog. |
| Search & filter | Find employees by name, department, country, salary range, grade. |
| Reporting & analytics | Aggregate views: avg/median salary by department/country/grade, headcount cost totals, pay distribution — the "answer questions about how we pay people" requirement. |
| Export | Export filtered views/reports to CSV for whatever the HR Manager still needs Excel for. |
| Basic access control | Single HR Manager role (or small HR team) with login — not public, not employee-facing. |

## Explicitly Out of Scope (v1) — and why

| Excluded | Reasoning |
|---|---|
| Payroll processing / payslip generation | Actual pay calculation involves per-country tax law, statutory deductions, and compliance — a massive, high-liability scope on its own. v1 is about managing salary data and visibility, not running payroll. |
| Multi-currency conversion / FX rates | Storing each salary in its local currency is enough for v1; live conversion adds a dependency (FX rate feeds) and ambiguity (which rate/date) not needed to answer the stated questions. |
| Employee self-service portal | Persona is the HR Manager only. An employee-facing view is a separate product surface with its own auth/privacy requirements. |
| Approval workflows (raise requests, promotions) | Adds multi-user process/state-machine complexity; v1 assumes HR Manager has authority to edit directly. |
| Integrations with existing HRIS/payroll systems | No system named yet; building an integration without a known target is speculative work. CSV import/export covers the interim need. |
| SSO / enterprise auth | Basic auth is enough for a first version with a small HR user base; SSO is an infra decision for later, tied to ACME's actual identity provider. |
| Compliance/audit certification (SOC2, GDPR tooling, etc.) | Important eventually given multi-country PII, but a certification-grade audit trail is a separate, larger effort from proving the product concept. |

## Technical Constraints
- End-to-end functional software: backend + UI, both required.
- Relational database, seeded via script with **10,000 employee** records (real scale, not a small sample).
- Fully deployed and reachable, with a short video demo.
- Meaningful unit test coverage on core functionality (fast, deterministic).
- Incremental commits showing how the solution evolved.

## Tech Stack
- **Backend:** Java, Spring Boot, Spring Data JPA (Hibernate), Gradle, JUnit — chosen to match the target job description (Java/Spring/Hibernate/JUnit/Gradle).
- **Frontend:** Angular + TypeScript — matches JD (components, state management, data binding, routing, directives, pipes).
- **Database:** PostgreSQL — relational, handles 10,000+ rows comfortably, realistic for a "fully deployed" target.
- **Deployment target:** TBD — decided after the app is functional locally.

## Assumptions
- 10,000 employees is a data-volume/scale target, not a concurrency target — few simultaneous HR users.
- "Multiple countries" affects data fields (currency, country) but not payroll computation in v1.
- Seeded data is synthetic/generated, not real ACME employee data.
