# SPDD Norms — ODM Platform Adapter Marketplace Executor Starter

This folder holds **project-specific engineering norms** for ODM Platform Adapter Marketplace Executor Starter. They describe *how* to implement features in this codebase, not *what* to build (that lives in `spdd/prompt/` and `spdd/analysis/`).

## SPDD artifacts

| Folder | Purpose |
|--------|---------|
| `spdd/analysis/` | Problem analysis, proposals, and testable specs (merged) |
| `spdd/prompt/` | Implementation-ready feature prompts (Requirements, Operations, Norms, Safeguards) |
| `spdd/norms/` | Reusable engineering conventions |

## Who should read this

Agents and developers running **SPDD** (Structured Prompt-Driven Development) commands should load these norms when:

- Drafting or updating a REASONS Canvas prompt (**N — Norms** section)
- Generating or syncing implementation code from a prompt
- Reviewing whether new code matches existing patterns

Treat this directory as the **canonical reference** for ODM Platform Adapter Marketplace Executor Starter conventions referenced by prompts. Prefer linking to a norm file here over duplicating long explanations inside every prompt.

## Architectural principles

These apply to every feature. The dedicated norm files **extend** them; they never contradict them.

- **Dependencies point inward.** HTTP adapter → application service → use case → ports; adapters, core services and clients sit behind the ports. The use case and the domain never depend on HTTP, the framework, persistence or external systems.
- **One model per boundary.** API DTOs stop at the application service. External-system models stop at their client. Only domain types cross layers.
- **One error model.** The service has a single exception hierarchy and a single error contract at the HTTP edge. Errors from external systems are translated where they enter, never passed through. Messages are descriptive and never contain secrets.
- **Observability.** A correlation id flows from the inbound request through the logs to outbound calls. Log business-significant steps and failures, with identifiers; never log secrets, headers or full bodies.

## Which norm file applies?

| You are building… | Read |
|-------------------|------|
| Any new or touched Java feature code | [Architectural principles](#architectural-principles), [`CODE_STRUCTURE.md`](./CODE_STRUCTURE.md), [`PACKAGE_STRUCTURE.md`](./PACKAGE_STRUCTURE.md), [`TESTING.md`](./TESTING.md) |
| REST endpoint with orchestration, ports, commands, presenters (non-trivial workflow) | [`USE_CASE_IMPLEMENTATION.md`](./USE_CASE_IMPLEMENTATION.md) |
| Entity CRUD with optional DTO mapping and/or filtered list APIs | [`GENERIC-CRUD-GUIDELINES.md`](./GENERIC-CRUD-GUIDELINES.md) |
| Both (e.g. use case that delegates persistence to core CRUD services) | **Both** — use cases call core services **via outbound ports**, not directly from the use case class |
| A call to, or a payload from, a system the service does not own | [`INTEGRATIONS.md`](./INTEGRATIONS.md) |
| Work that runs in the background (async calls, fan-out, heavy computation) | [`ASYNC_EXECUTION.md`](./ASYNC_EXECUTION.md) |
| New or changed configuration | [`CONFIGURATION.md`](./CONFIGURATION.md) |
| New or changed public endpoint | [`API_DESIGN.md`](./API_DESIGN.md) |

## Norm index

| File | Topic |
|------|--------|
| [CODE_STRUCTURE.md](./CODE_STRUCTURE.md) | Composed method, method-body line limit (not file), no `*Utils` / `*Helper` extracts |
| [USE_CASE_IMPLEMENTATION.md](./USE_CASE_IMPLEMENTATION.md) | Hexagonal use-case flow: controller → use-cases service → factory → ports/adapters |
| [GENERIC-CRUD-GUIDELINES.md](./GENERIC-CRUD-GUIDELINES.md) | Template-method CRUD (`GenericCrud*` hierarchy), mapping, filtering, hooks, AOP and events |
| [PACKAGE_STRUCTURE.md](./PACKAGE_STRUCTURE.md) | Package by aggregate, versioned HTTP tree, where each kind of class goes |
| [INTEGRATIONS.md](./INTEGRATIONS.md) | Clients per external system, anti-corruption, error translation, resilience |
| [TESTING.md](./TESTING.md) | `*Test` (unit) vs `*IT` (running application), tests per layer, traceability, isolation |
| [ASYNC_EXECUTION.md](./ASYNC_EXECUTION.md) | `AsyncExecutorService` (virtual threads, I/O) vs `PlatformThreadExecutorService` (platform threads, CPU or pinning), concurrency caps, executor configuration |
| [CONFIGURATION.md](./CONFIGURATION.md) | Typed, validated-at-startup configuration; behaviour selected by configuration; safe defaults |
| [API_DESIGN.md](./API_DESIGN.md) | Versioning, breaking changes, OpenAPI contract, uniform responses |

## Keeping norms up to date

Update a norm file when a **reusable pattern** changes (e.g. new use-case package layout, CRUD hook convention). After updating:

1. Adjust affected `spdd/prompt/*.md` **N — Norms** references if needed
2. Use `/spdd-sync` if generated code already exists and the prompt must reflect the new standard

Feature-specific rules (exact error messages, API contracts, permissions for one endpoint) belong in the prompt **Safeguards** section, not in this folder.
