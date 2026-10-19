# Generic CRUD services (template method pattern)

CRUD can be implemented through a small hierarchy of abstract classes that follow the **template method** pattern: the base class defines the **algorithm** (order of steps, transactions, error handling), and subclasses supply **hooks** and **type-specific behavior** by implementing abstract methods and optionally overriding protected hook methods.

---

## Class hierarchy

| Layer | Interface | Implementation | Purpose |
| ----- | --------- | -------------- | ------- |
| Entity CRUD | `GenericCrudService<T, ID>` | `GenericCrudServiceImpl<T, ID>` | Persistence for JPA entities `T` with id `ID`. |
| + API mapping | `GenericMappedCrudService<R, T, ID>` | `GenericMappedCrudServiceImpl<R, T, ID>` | Adds REST/DTO type `R`: map `R ↔ T` and expose `*Resource` methods. |
| + filtering | `GenericMappedAndFilteredCrudService<F, R, T, ID>` | `GenericMappedAndFilteredCrudServiceImpl<F, R, T, ID>` | Adds filter object `F` and specification-based list queries. |

**Type parameters**

- **`T`** — JPA entity.
- **`ID`** — Primary key type (`Serializable`, e.g. `Long`, `String`).
- **`R`** — API resource / DTO returned to callers.
- **`F`** — Search or filter options used to build a `Specification<T>` (the template's own functional interface in `utils.repositories`).

Pick the **shallowest** layer that fits: if the API does not need a separate `R`, you might only extend `GenericCrudServiceImpl`. When list endpoints support query filters and you expose DTOs, use **`GenericMappedAndFilteredCrudServiceImpl`**.

---

## What you must implement

### `GenericCrudServiceImpl<T, ID>`

1. **`getRepository()`** — Return a repository that supports CRUD, paging/sorting, and specifications (extend `EntityManagerRepository`, which implements `PagingAndSortingAndSpecificationExecutorRepository`).

2. **`validate(T)`** — Business and field validation before create/overwrite. Throw your domain or API-layer exceptions on failure.

3. **`reconcile(T)`** — Resolve references, defaults, or denormalized state after validation and before save (e.g. load related entities by foreign key).

Optional **hooks** (empty defaults; override when needed):

- **Read:** `afterFindOne(T, ID)`
- **Create:** `beforeCreation(T)`, `afterCreation(T, T)`, `afterCreationCommit(T)`
- **Update:** `beforeOverwrite(T)`, `afterOverWrite(T, T)`, `afterOverwriteCommit(T)`
- **Delete:** `beforeDelete(ID)`, `afterDelete(ID)`, `afterDeleteCommit(T)` (used with `deleteReturning`)

**Utilities:** `exists(ID)` is protected; `checkExistenceOrThrow(ID)` and `findOne(ID)` typically throw a not-found exception when the row is missing.

### `GenericMappedCrudServiceImpl<R, T, ID>`

Adds mapping between API and persistence:

1. **`toRes(T entity)`** — Entity → API resource.
2. **`toEntity(R resource)`** — API resource → entity (for incoming bodies).

Delegation to a dedicated mapper class is common. Mapped operations are:

- `findAllResources` / `findOneResource` / `createResource` / `overwriteResource` / `deleteReturningResource`

### `GenericMappedAndFilteredCrudServiceImpl<F, R, T, ID>`

1. **`getSpecFromFilters(F filters)`** — Build a `Specification<T>`, often by composing smaller specs (e.g. AND-combining a list of predicates).

2. **`getRepository()`** — May be declared again in this class so the implementation satisfies the abstract method from the filtered layer (same bean as in `GenericCrudServiceImpl`).

**Note:** `findAllFiltered` runs `getRepository().findAll(spec, pageRequest)`. Override `findAll` / `findAllResources` only if you need different list behavior (for example, disabling generic pagination for a given aggregate).

---

## Fixed algorithm (template method)

The base class fixes the **order of operations**; subclasses should not change the sequence unless they intentionally override a public method (usually avoided).

**Create:** `validate` → `reconcile` → `beforeCreation` → `save` → `afterCreation` → (after transaction) → `afterCreationCommit`

**Overwrite:** `validate` → existence check → `reconcile` → `beforeOverwrite` → `save` → `afterOverWrite` → `afterOverwriteCommit`

**Delete:** existence check → `beforeDelete` → `deleteById` → `afterDelete`

**`deleteReturning`:** `findOne` → map with provided `Function` → `delete` → `afterDeleteCommit`

Create/overwrite/delete are **`@Transactional`** on the CDI bean, so the transactional boundary is explicit inside the generic implementation. Read paths may use a small **`TransactionHandler`** (or equivalent) with `@Transactional` for mapped/filtered reads so lazy loading and mapping run inside a transactional boundary when needed.

---

## Transactions: two mechanisms

- **`@Transactional`** (on `create` / `overwrite` / `delete` in `GenericCrudServiceImpl`): used for writes in the generic CRUD flow.
- **`TransactionHandler`** (or similar) in mapped layers: transactional wrapper for read operations that map entities to resources.

When one service calls another (e.g. loading a related entity in `reconcile`), use the existing service API and stay aware of transaction boundaries if you need atomicity across multiple aggregates.

---

## Cross-cutting CRUD behaviour (AOP and events)

The template wraps every generic CRUD service in **one interception point** around four method groups — **find**, **create**, **overwrite**, **delete** (mapped `*Resource` variants included) — and publishes two kinds of events.

**Pick the mechanism by scope:**

| Concern | Mechanism |
| --- | --- |
| Rule of **one aggregate** that must run in the same transaction as `save` / `delete` | CRUD **hook** on that service (`beforeCreation`, `afterOverWrite`, …) |
| Concern that applies to **every** CRUD service the same way (history, audit, stewardship, emitters) | **CRUD wrap**: a subclass of the template's wrap type, one per concern |
| Reaction **outside** the CRUD operation (notify, sync, invalidate) | **Event observer** |

**Events:**

- `CrudLifecycleEvent` (`CREATED`, `OVERWRITTEN`, `DELETED`) is fired by the template's own wrap after a CRUD write: an **operation-level** signal.
- `EntityChangeEvent` is fired by `HibernateChangeDataCapture` on every insert, update and delete, including those not made through a CRUD service: an **entity-level** signal.
- Observers that cause side effects outside the database run **after commit**, so a rolled-back write never leaks.

**Rules:**

- A wrap holds **no business policy** and does not change arguments or results. Policy belongs in hooks or use cases.
- A wrap runs **once per external call**: nested calls (`createResource` → `create`) are not advised again.
- Several wraps on the same methods declare an **explicit order**.
- Use cases and outbound port implementations are **never** wrapped. Use cases reach cross-cutting behaviour through ports.

**Quarkus:** subclass `GenericCrudInterceptor`, override `aroundFind` / `aroundCreate` / `aroundOverwrite` / `aroundDelete`, annotate it `@Interceptor @GenericCrudIntercepted` and order it with `@Priority`. Events are fired with CDI `Event<T>`; after-commit observers use `@Observes(during = TransactionPhase.AFTER_SUCCESS)`.

---

## Controllers and public API

- Prefer **`…Resource` methods** for HTTP adapters so the layer works with `R`, not entities.
- If path parameters must **override** fields in the body (e.g. id in URL vs body), override the `*Resource` method, set the field from the path, then call `super`.

---

## Example shape (conceptual)

A typical filtered, mapped service:

- Extends `GenericMappedAndFilteredCrudServiceImpl<MySearchOptions, MyResource, MyEntity, MyIdType>`.
- Implements `validate`, `reconcile`, `getSpecFromFilters`, `getRepository`, `toRes`, `toEntity`.
- Uses `beforeCreation` / `beforeOverwrite` for rules that depend on persisted state (e.g. uniqueness excluding the current row).
- Builds `Specification` instances from filter fields, composed with AND/OR as needed.

---

## Design tips

1. **Keep `validate` focused** on invariants, format, and required fields; use **`reconcile`** for loading associations and fixing navigable graphs before `save`.

2. **Use hooks** for database-dependent rules (uniqueness excluding self, side effects) so they run in the same transaction as `save` / `delete`.

3. **Repository** must support paging and specifications if you use the filtered base class; static nested `Spec` helpers on the repository are a common style.

4. **Throw** exceptions that your global handler maps to HTTP status codes consistently.

5. If an aggregate **must not** support generic `findAll(PageRequest)`, override those methods and throw an explicit “not supported” (or equivalent) with a clear message.
