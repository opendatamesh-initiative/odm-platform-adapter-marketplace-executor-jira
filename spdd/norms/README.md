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

## How to use with `/spdd-reasons-canvas`

When running the command, include this folder so the agent loads norms before filling **## Norms**:

```
/spdd-reasons-canvas @spdd/norms/README.md <your business context or @requirement file>
```

**Agent workflow (Norms stage):**

1. **Read** this README and every norm file listed under [Norm files](#norm-files) that applies to the work (see [Which norm file applies?](#which-norm-file-applies)).
2. **Infer scope** from business context: CRUD-only, use-case/orchestration, or both.
3. **Populate `## Norms`** in the output prompt with:
   - Cross-cutting items from [Cross-cutting code norms](#cross-cutting-code-norms) (always relevant for backend features).
   - Norms from the applicable dedicated files (summarized below; cite file names so implementers can open the full guide).
4. **Do not** paste entire norm documents into the canvas—distill **actionable, verifiable** bullets (naming, packages, annotations, boundaries, testing).
5. Keep **Safeguards** separate: norms = *how to build*; safeguards = *boundaries and non-negotiables* from the requirement.

**Output shape:**

```markdown
## Norms
1. Annotation standards: …
2. Dependency injection: …
3. Exception handling: …
4. Data validation: …
5. Logging: …
6. Documentation standards: …
7. [Feature-specific] Use-case boundaries: … (when applicable)
8. [Feature-specific] CRUD template-method hooks: … (when applicable)
```

---

## Which norm file applies?

| You are building… | Read |
|-------------------|------|
| Any new or touched Java feature code | [`CODE_STRUCTURE.md`](./CODE_STRUCTURE.md) |
| REST endpoint with orchestration, ports, commands, presenters (non-trivial workflow) | [`USE_CASE_IMPLEMENTATION.md`](./USE_CASE_IMPLEMENTATION.md) **and** [`CODE_STRUCTURE.md`](./CODE_STRUCTURE.md) |
| Entity CRUD with optional DTO mapping and/or filtered list APIs | [`GENERIC-CRUD-GUIDELINES.md`](./GENERIC-CRUD-GUIDELINES.md) **and** [`CODE_STRUCTURE.md`](./CODE_STRUCTURE.md) |
| Both (e.g. use case that delegates persistence to core CRUD services) | **All three** — use cases call core services **via outbound ports**, not directly from the use case class |

## Norm index

| File | Topic |
|------|--------|
| [CODE_STRUCTURE.md](./CODE_STRUCTURE.md) | Composed method, method-body line limit (not file), no `*Utils` / `*Helper` extracts |
| [USE_CASE_IMPLEMENTATION.md](./USE_CASE_IMPLEMENTATION.md) | Hexagonal use-case flow: JAX-RS resource → use-cases service → factory → ports/adapters |
| [GENERIC-CRUD-GUIDELINES.md](./GENERIC-CRUD-GUIDELINES.md) | Template-method CRUD (`GenericCrud*` hierarchy), mapping, filtering, hooks |

## Keeping norms up to date

Update a norm file when a **reusable pattern** changes (e.g. new use-case package layout, CRUD hook convention). After updating:

1. Adjust affected `spdd/prompt/*.md` **N — Norms** references if needed
2. Use `/spdd-sync` if generated code already exists and the prompt must reflect the new standard

Feature-specific rules (exact error messages, API contracts, permissions for one endpoint) belong in the prompt **Safeguards** section, not in this folder.

---

## Cross-cutting code norms

These apply to most backend work surfaced through REASONS-Canvas. Feature-specific norms in the dedicated files **extend** this list; they do not replace it.

### Layering and responsibilities

- **Thin HTTP adapter:** JAX-RS resources map HTTP only; no business rules. Services (use-case service or CRUD service) own orchestration and mapping at the application boundary.
- **Domain inside the use case package:** Commands and presenters use **entities, value types, or small domain records**—never REST `*Res` types inside `org.opendatamesh.platform.adapter.marketplace.executor.starter.<aggregate>.services.usecases.*`.
- **Core vs use case:** Reusable CRUD/query logic lives in `org.opendatamesh.platform.adapter.marketplace.executor.starter.<aggregate>.services.core.*` (or equivalent). Use cases reach core only through **outbound port implementations**, not by injecting core services into the use case class.
- **Pick the shallowest CRUD base** that fits (`GenericCrudServiceImpl` → mapped → filtered); do not add filter/mapping layers without need.

### CDI and dependency injection

- **`@Path`** on `*UseCaseResource` in the adopting service (next to the aggregate, or another package the service chooses), with `@Produces(MediaType.APPLICATION_JSON)`. Do not put HTTP adapters under a versioned `rest` package (`rest.v1`, `rest.v2`). Template HTTP helpers live in `org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.rest`.
- **`@ApplicationScoped`** on `*UseCasesService` and concrete CRUD service subclasses.
- **`@ApplicationScoped`** on the use-case **factory only** in that slice; outbound port impls are **plain Java** (`new` in the factory). The use case class is plain Java, constructed with `new`.
- **No CDI stereotypes** on use case classes or `*OutboundPortImpl`.
- **Constructor injection** for factories and services; use cases receive dependencies via factory constructor wiring.

### REST resources and OpenAPI

- Request/response DTOs live with the HTTP adapter. Naming: `*CommandRes`, `*ResultRes` (and related `*Res`). Do not put a version segment in the package name.
- Document the JAX-RS resource with `@Tag`, `@Operation`, `@ApiResponses`, `@Parameter` (`io.swagger.v3.oas.annotations`); `@Schema` on DTO fields where useful; `@Hidden` for non-public endpoints.
- Convert `*Res` ↔ entities in the **use cases service** (or CRUD `toRes` / `toEntity`), not in the JAX-RS resource or the use case class.

### Exceptions and API errors

- Throw `NotFoundException`, `BadRequestException`, `ResourceConflictException`, and `InternalException` from `org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions`. They extend `OdmApiException` (`getErrorName()`, `getStatus()`).
- The adopting service maps those exceptions to a consistent HTTP error body; do not leak sensitive internals in messages. Do not put that mapper under `org.opendatamesh.platform.adapter.marketplace.executor.starter.rest`.
- CRUD **`validate` / `reconcile` / hooks** throw exceptions that the mapper maps to stable HTTP status codes.

### Data validation

- **CRUD:** `validate(T)` for invariants and required fields; `reconcile(T)` for loading associations and fixing graphs before save.
- **Use cases:** validate the command early in `execute()`; fail fast with clear exceptions.
- Path parameters that must override body fields: set the field on the DTO, then call the `*Resource` method.

### Transactions

- **Use cases:** use shared `TransactionalOutboundPort` (`doInTransaction`, `doInTransactionWithResults`) for atomic work—do not reimplement transaction plumbing per use case. `DefaultTransactionalOutboundPortImpl` runs the work with `QuarkusTransaction.joiningExisting()` and executes `SET TRANSACTION ISOLATION LEVEL SERIALIZABLE` on the PostgreSQL connection before the caller’s work.
- **CRUD:** the template method in `GenericCrudServiceImpl` runs `create`, `overwrite`, and `delete` as `@Transactional`. `afterCreationCommit` and `afterOverwriteCommit` run from `TransactionSynchronizationRegistry` after `STATUS_COMMITTED`. Mapped reads use `TransactionHandler` (`runInTransaction` is `REQUIRED`, `runInNewTransaction` is `REQUIRES_NEW`) when mapping touches lazy associations.
- **Lifecycle events:** `CrudLifecycleInterceptor` (a `GenericCrudInterceptor`) fires `CrudLifecycleEvent` around create/overwrite/delete. `HibernateChangeDataCapture` fires `EntityChangeEvent` from the post-insert, post-update, and post-delete listener. Observers use `@Observes(during = TransactionPhase.AFTER_SUCCESS)`. Extra CRUD wraps subclass `GenericCrudInterceptor` and bind with `@GenericCrudIntercepted`.
- **Outbound HTTP:** call other services through `RestUtils` (`org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client`). Build with `RestUtilsFactory.getRestUtils()`, `getAuthenticatedRestUtils`, `getAsyncRestUtils`, or `getAuthenticatedAsyncRestUtils`. Identifier path variables use `{id}`. Do not put a version segment in that package.

### Logging

- SLF4J with class-level logger; log at **info/warn** for business-significant steps and failures; **never** log secrets or credentials.
- Prefer structured context (identifiers, operation phase) consistent with surrounding modules. `CorrelationIdFilter` (`org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.rest`) copies the `CORRELATION_ID` request header to MDC key `correlation_id` and clears it after the response.

### Testing

- **Use cases:** add or extend `*UseCaseResourceIT` for full-stack verification of new routes.
- **CRUD / ports:** unit-test complex `validate`/`reconcile`/spec builders or port adapters where payoff is high.
- Trace tests back to requirements when generated from a spec (comment referencing scenario or requirement id).

### Documentation in code

- Keep public API documented via OpenAPI annotations; avoid redundant Javadoc on obvious getters/setters.
- Package-private use case classes and port types unless wider visibility is required.

---

## Norm files

### [`CODE_STRUCTURE.md`](./CODE_STRUCTURE.md)

**Purpose:** Composed method (step-down), method-body line limit (not file length), and no new `*Utils` / `*Helper` extracts.

**When to apply:** All new or touched Java service code (use cases, CRUD services, port impls, application services).

**Full detail:** [CODE_STRUCTURE.md](./CODE_STRUCTURE.md)

---

### [`USE_CASE_IMPLEMENTATION.md`](./USE_CASE_IMPLEMENTATION.md)

**Purpose:** Hexagonal-style use case flow from HTTP through application orchestration to outbound adapters.

**When to apply:** New or changed behavior that is more than CRUD—initialization flows, multi-step orchestration, validation + persistence + side effects (notifications, etc.).

**Flow (must appear in Structure / Operations when relevant):**

```
HTTP → *UseCaseResource → *UseCasesService → *Factory → UseCase
  → OutboundPort(s) → *OutboundPortImpl → core services / clients
```

**Hard boundaries (non-negotiable in generated prompts):**

| Layer | Rule |
|-------|------|
| HTTP `*Res` | Only the JAX-RS resource + use cases service; **never** in `org.opendatamesh.platform.adapter.marketplace.executor.starter.<aggregate>.services.usecases.*` |
| Command | Domain types only (entities, UUIDs, enums, small domain records) |
| Presenter | Domain result types only; service maps to `*ResultRes` |
| Use case class | Implements `UseCase`; package-private; plain Java (`new`); **no** CDI annotations |
| Factory | Sole `@ApplicationScoped` bean in the slice; `build...(Command, Presenter)` returns `UseCase`; wires ports with `new` |
| `*OutboundPortImpl` | Plain Java; collaborators via constructor from factory |

**Package layout per use case** (`org.opendatamesh.platform.adapter.marketplace.executor.starter.<aggregate>.services.usecases.<name>`):

| Artifact | Naming / role |
|----------|----------------|
| `<Name>.java` | Use case; `execute()` orchestration |
| `<Name>Command.java` | Input record |
| `<Name>Presenter.java` | Output boundary interface |
| `<Name>Factory.java` | Composition root |
| `*OutboundPort.java` / `*OutboundPortImpl.java` | Port + adapter |

**Shared types:** `org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.usecases.UseCase`, `UseCasePresenter`, `TransactionalOutboundPort` (implemented by `DefaultTransactionalOutboundPortImpl`).

**Checklist for new use cases** (use in Operations section as task order):

1. Command + presenter (domain only) in `usecases.<name>`.
2. Outbound port interfaces + plain impls.
3. `@ApplicationScoped` factory with `new` for impls and the use case.
4. `*UseCasesService` method: `*CommandRes` → command, factory + `execute()`, presenter → `*ResultRes`.
5. `*CommandRes` / `*ResultRes` next to the HTTP adapter (no versioned `rest` package).
6. `*UseCaseResource` endpoint (`@Path`) + OpenAPI.
7. `*UseCaseResourceIT`.

**Full detail:** [USE_CASE_IMPLEMENTATION.md](./USE_CASE_IMPLEMENTATION.md)

---

### [`GENERIC-CRUD-GUIDELINES.md`](./GENERIC-CRUD-GUIDELINES.md)

**Purpose:** Template-method CRUD for JPA entities with optional API mapping and specification-based filtering.

**When to apply:** Standard create/read/update/delete (and filtered lists) on aggregates exposed as REST resources.

**Class hierarchy (choose one):**

| Layer | Types | Use when |
|-------|--------|----------|
| Entity CRUD | `GenericCrudService<T, ID>` / `GenericCrudServiceImpl` | Persistence only, no separate API type `R` |
| + mapping | `GenericMappedCrudService<R, T, ID>` / `...Impl` | Expose `*Resource` methods (`findOneResource`, `createResource`, …) |
| + filtering | `GenericMappedAndFilteredCrudService<F, R, T, ID>` / `...Impl` | List endpoints with filter object `F` → `Specification<T>` |

**Must implement (subclass):**

- `getRepository()` — `PagingAndSortingAndSpecificationExecutorRepository`, by extending `EntityManagerRepository`.
- `validate(T)` — invariants before create/overwrite.
- `reconcile(T)` — associations and defaults before save.
- Mapped: `toRes(T)`, `toEntity(R)`.
- Filtered: `getSpecFromFilters(F)` — compose specs with `SpecsUtils` (often AND).

**Template method order (do not reorder without overriding intentionally):**

- **Create:** `validate` → `reconcile` → `beforeCreation` → `save` → `afterCreation` → `CREATED` → `afterCreationCommit` (after `STATUS_COMMITTED`)
- **Overwrite:** `validate` → existence → `reconcile` → `beforeOverwrite` → `save` → `afterOverWrite` → `OVERWRITTEN` → `afterOverwriteCommit` (after `STATUS_COMMITTED`)
- **Delete:** existence → `beforeDelete` → `deleteById` → `afterDelete` → `DELETED`

**Hooks (override when needed):** `afterFindOne`, `beforeCreation`, `afterCreation`, `beforeOverwrite`, `afterOverWrite`, `beforeDelete`, `afterDelete`, `afterDeleteCommit`, etc.

Paging types are `Page`, `PageRequest`, and `Sort` in `org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.repositories`. `PageableSortValidator` rejects unknown sort paths via the JPA metamodel, including nested and embedded paths.

**HTTP:** Prefer `*Resource` methods; path id overrides body before the call. The JAX-RS resource is `@Path` in the adopting service, not under a versioned `rest` package.

**Design tips for Norms / Safeguards:**

- Keep `validate` strict; put association loading in `reconcile`.
- Use hooks for DB-dependent rules (uniqueness excluding self) in the same transaction as `save`.
- If `findAll(PageRequest)` is not allowed for an aggregate, override and throw explicit not-supported.
- Repository must support specifications when using the filtered base class.

**Full detail:** [GENERIC-CRUD-GUIDELINES.md](./GENERIC-CRUD-GUIDELINES.md)

---

## Combining norms in one feature

Typical pattern when a canvas covers both CRUD and a workflow:

1. **Structure:** CRUD service in core; use case package with ports whose impls delegate to `*CrudService` / `*Service`.
2. **Operations:** CRUD tasks follow hook and `*Resource` naming; use case tasks follow the 7-step checklist.
3. **Norms:** Merge cross-cutting list + CRUD template-method bullets + use-case boundary table.
4. **Safeguards:** Requirement-specific constraints (performance, security, idempotency)—not duplicated from norm files unless they encode a global rule.

---

## Package index

| File | Topic |
|------|--------|
| [`README.md`](./README.md) | This index, cross-cutting norms, SPDD integration |
| [`CODE_STRUCTURE.md`](./CODE_STRUCTURE.md) | Composed method, method-body line limit (not file), no `*Utils` / `*Helper` extracts |
| [`USE_CASE_IMPLEMENTATION.md`](./USE_CASE_IMPLEMENTATION.md) | Use case hexagon, JAX-RS resource, ports, factory, testing |
| [`GENERIC-CRUD-GUIDELINES.md`](./GENERIC-CRUD-GUIDELINES.md) | Generic CRUD template method, mapping, filtering, transactions |

When adding a new norm file, document it in [Norm files](#norm-files) and extend [Which norm file applies?](#which-norm-file-applies).
