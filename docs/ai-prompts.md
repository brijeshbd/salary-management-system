# AI Prompts & Instructions

This supplements [`ai-usage-log.md`](ai-usage-log.md), which is a narrative of what was decided
and why, with the actual prompt text at the points that shaped the project most - both the
steering prompts given to Claude Code, and the delegation prompts Claude Code sent to its own
sub-agents for larger research, design, and audit tasks. It's a curated, representative set, not
a full transcript - chosen to show how the project was actually driven rather than to dump every
message.

## Project kickoff & scoping

The project started deliberately open-ended, with Claude asked to drive clarification instead of
assuming scope:

> I want to create one small project, can you help me with that?

> Whatever we build, we need a draft first, then we'll decide what to do.
> Please ask me questions if you have any doubt.

Scope only became concrete once the actual assessment brief showed up - first as a paraphrase,
then as the real PDF:

> Goal: Build employee salary management software for an organization with 10,000 employees.
> User Persona: HR Manager of the org

> I have one document in the Downloads folder, please check that one.

Claude Code read the PDF directly through its Read tool's PDF support and pulled out the hard
technical constraints - Java/Spring/Hibernate/Gradle/JUnit, Angular/TypeScript, a 10,000-employee
seed script, deployment, tests, incremental commits, and this exact artifacts requirement - none
of which had come through in the earlier paraphrase. Those went straight into `REQUIREMENTS.md`
before any code was written.

The target job description, used to lock the tech stack, was pasted in full:

> RequirementsWhat You'll BringBackend – Java3+ years of Java development with strong
> fundamentals. Experience with Micronaut or Spring frameworks. [...] Frontend –
> AngularHands-on experience building scalable applications using Angular and TypeScript. [...]
> Good-to-Have SkillsAspect-Oriented Programming (AOP) frameworks. RxJS for reactive programming
> in Angular. [...] Experience with Microsoft Azure. Familiarity with observability and
> monitoring best practices.

(Full text is in the conversation history; this JD is what later drove the JD-gap check that
added CI/CD and the seeder's multithreading demonstration - see below.)

## Delegating architecture design (Plan sub-agent)

Rather than design the system alone, Claude Code went into plan mode and handed the architecture
design to a `Plan` sub-agent, giving it the full requirements and constraints as context along
with an explicit instruction to be opinionated instead of presenting unresolved options:

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

The resulting design became `/Users/brijesh/.claude/plans/cozy-gliding-cray.md`, which Claude Code
reviewed and adapted into the final plan before any code was written - see `docs/tradeoffs.md`
for the decisions that plan produced.

## Mid-build steering (JD gap check)

After the app was built and deployed, the user pushed for a direct audit against the job
description rather than accepting the build as finished:

> So, as per the JD, did we miss something?

> So we didn't use multithreading?

> Can we make sure our app is multithreading-friendly?

That's what produced the CI/CD pipeline and the seeder's parallel-generation demonstration (see
below) - both added after the app was otherwise complete, in direct response to gaps the user
asked Claude to check for.

## Delegating the thread-safety audit (Explore sub-agent)

Before writing any concurrent code in response to "make sure the app is multithreading friendly,"
Claude Code sent an `Explore` sub-agent to audit the existing codebase first, instead of assuming
it was safe or bolting on defensive locking without evidence:

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

The audit came back clean for every existing bean, but flagged exactly the risk it was asked to
check for: `Faker` isn't safe for concurrent use, which shaped the fix - `DataSeeder` giving each
parallel task its own generator instance - before it ever shipped, instead of a race being
discovered after the fact. See the "Concurrency" section of `docs/tradeoffs.md` and the matching
entry in `docs/ai-usage-log.md` for what the audit found and how it was applied.

## Verifying the artifacts requirement itself

This document exists because of a direct instruction to check completeness against the brief's
own artifacts list, rather than Claude asserting it was already done:

> Have we followed the "Artifacts" requirement? Along with your solution, please commit any
> artifacts that help us understand your thinking and approach. Examples might include:
> - Requirements document
> - Planning or design notes
> - Architecture diagrams
> - Prompts or instructions used with AI tools
> - Trade-off explanations
> - Performance considerations

Claude Code checked each item against `git ls-files` directly instead of relying on memory, found
five of six solidly covered and the sixth - prompts or instructions - only partially covered by
the narrative `ai-usage-log.md`. Rather than argue the narrative log was good enough on its own,
it said so plainly and wrote this document in response.
