# AI Prompts & Instructions

Supplements [`ai-usage-log.md`](ai-usage-log.md) (a narrative of *what* was decided and *why*)
with the actual prompt text at the points that shaped the project most — both the user's steering
prompts to Claude Code, and Claude Code's own delegation prompts to sub-agents it dispatched for
larger research/design/audit tasks. A curated, representative set, not a full transcript — chosen
to show how the project was actually driven, not to dump every message.

## Project kickoff & scoping

The project started deliberately open-ended, with the user asking Claude to drive clarification
rather than assume scope:

> I want to create one small project, can you help me with that?

> Whatever we build, we need a draft first, then we'll decide what to do.
> Please ask me questions if you have any doubt.

Scope only became concrete once the user supplied the actual assessment brief - first as a
paraphrase, then as the real PDF:

> Goal: Build employee salary management software for an organization with 10,000 employees.
> User Persona: HR Manager of the org

> I have one document in the Downloads folder, please check that one.

Claude Code read the PDF directly (via its Read tool's PDF support) and identified the hard
technical constraints (Java/Spring/Hibernate/Gradle/JUnit, Angular/TypeScript, a 10,000-employee
seed script, deployment, tests, incremental commits, this exact artifacts requirement) that
weren't present in the earlier paraphrase, and folded them into `REQUIREMENTS.md` before any code
was written.

The target job description - supplied by the user, used to lock the tech stack - was pasted in
full:

> RequirementsWhat You'll BringBackend – Java3+ years of Java development with strong
> fundamentals. Experience with Micronaut or Spring frameworks. [...] Frontend –
> AngularHands-on experience building scalable applications using Angular and TypeScript. [...]
> Good-to-Have SkillsAspect-Oriented Programming (AOP) frameworks. RxJS for reactive programming
> in Angular. [...] Experience with Microsoft Azure. Familiarity with observability and
> monitoring best practices.

(Full text in the conversation history; this JD is what later drove the JD-gap check that added
CI/CD and the seeder's multithreading demonstration - see below.)

## Delegating architecture design (Plan sub-agent)

Rather than design the system alone, Claude Code entered plan mode and delegated the architecture
design to a `Plan` sub-agent, with the full requirements/constraints as context and an explicit
instruction to be opinionated rather than present unresolved options:

> We're building a take-home technical assessment project: "Employee Salary Management Software"
> for HR Manager persona, ACME org, 10,000 employees across multiple countries. [...]
>
> MANDATORY constraints (from an assessment brief, non-negotiable): Backend: Java, Spring Boot
> (chosen over Micronaut), Spring Data JPA/Hibernate, Gradle, JUnit [...] Frontend: Angular +
> TypeScript [...] Database: PostgreSQL [...] Must seed a script generating 10,000 employee
> records [...] Must be fully deployed + working [...] Meaningful unit tests [...] Incremental git
> commits [...] Deliverable artifacts alongside code: requirements doc, planning/design notes,
> architecture diagram, AI prompts/instructions log, trade-off explanations, performance
> considerations.
>
> Please design and return a concrete, opinionated implementation plan covering: 1. Monorepo
> folder structure [...] 3. Data model [...] should Department/Country be normalized tables or
> simple indexed string/enum columns? Give a clear recommendation with reasoning, not both
> options [...] 6. Auth approach [...] recommend simplest defensible approach [...] without
> over-building enterprise auth [...] 11. A concrete milestone/incremental-commit sequence [...]
>
> Return the plan as clear structured sections I can adapt into a final plan document. Be
> opinionated - pick one approach per decision point and justify it briefly, don't present
> unresolved options.

The resulting design became `/Users/brijesh/.claude/plans/cozy-gliding-cray.md`, reviewed and
adapted by Claude Code into the final plan the user approved before any code was written - see
`docs/tradeoffs.md` for the decisions that plan produced.

## Mid-build steering (JD gap check)

After the app was built and deployed, the user drove a direct audit against the job description
rather than accepting the build as finished:

> So, as per the JD, did we miss something?

> So we didn't use multithreading?

> Can we make sure our app is multithreading-friendly?

This is what produced the CI/CD pipeline and the seeder's parallel-generation demonstration (see
below) - both added *after* the app was otherwise complete, in direct response to gaps the user
asked Claude to check for.

## Delegating the thread-safety audit (Explore sub-agent)

Before writing any concurrent code in response to "make sure the app is multithreading friendly,"
Claude Code dispatched an `Explore` sub-agent to audit the existing codebase first, rather than
assume it was safe or add locking defensively without evidence:

> In the Spring Boot backend [...] audit every @Service, @Component, @RestController,
> @Configuration, and filter/servlet class for thread-safety problems that would matter under
> Spring's default singleton-bean, multi-threaded-request-handling model [...]
>
> Specifically look for: 1. Any non-final instance field on a singleton bean that gets mutated
> after construction/injection [...] 2. Any use of a non-thread-safe class held as a shared
> instance field [...] 5. Note whether net.datafaker.Faker (used in EmployeeSeedGenerator, held
> as an instance field) is documented/known to be thread-safe for concurrent use from multiple
> threads - this matters because I'm about to parallelize seed data generation [...]
>
> For each class, report: file path, whether it's safe or not, and if not safe, exactly which
> field/line is the problem and why. [...] Don't fix anything - this is a read-only audit, just
> report findings.

The audit came back clean for every existing bean, but flagged exactly the risk anticipated in the
prompt - `Faker` isn't safe for concurrent use - which shaped the fix (`DataSeeder` giving each
parallel task its own generator instance) before it shipped, rather than discovering the race
after the fact. See `docs/tradeoffs.md`'s "Concurrency" section and `docs/ai-usage-log.md`'s
corresponding entry for what the audit found and how it was applied.

## Verifying the artifacts requirement itself

This document exists because of a direct instruction to check completeness against the brief's
own artifacts list, rather than Claude Code asserting it was done:

> Have we followed the "Artifacts" requirement? Along with your solution, please commit any
> artifacts that help us understand your thinking and approach. Examples might include:
> - Requirements document
> - Planning or design notes
> - Architecture diagrams
> - Prompts or instructions used with AI tools
> - Trade-off explanations
> - Performance considerations

Claude Code checked each example against `git ls-files` directly rather than from memory, found
five of six solidly covered and the sixth ("prompts or instructions") only partially covered by
the narrative `ai-usage-log.md`, said so rather than arguing the narrative log was sufficient, and
wrote this document in response.
