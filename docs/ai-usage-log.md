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
- The resulting design was reviewed, adapted into `/Users/brijesh/.claude/plans/cozy-gliding-cray.md`
  (implementation plan), and approved by the user before any code was written.
- Key decisions this produced, with their reasoning, are tracked in [`tradeoffs.md`](tradeoffs.md).

## Build process

- Milestone 1 (backend scaffold): generated via Spring Initializr (`start.spring.io`), then
  hand-edited to add JWT (jjwt), DataFaker, Testcontainers, and Actuator dependencies not
  covered by the initializer's dependency picker. Verified end-to-end (Postgres via Docker
  Compose, `bootRun`, `/actuator/health` returns UP) before committing.
- (Further milestones logged here as they land.)
