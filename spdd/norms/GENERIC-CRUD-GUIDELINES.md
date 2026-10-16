# Generic CRUD services (template method pattern)

CRUD can be implemented through a small hierarchy of abstract classes that follow the **template method** pattern: the base class defines the **algorithm** (order of steps, transactions, error handling), and subclasses supply **hooks** and **type-specific behavior** by implementing abstract methods and optionally overriding protected hook methods.

Base classes live under `org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.services`.

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
- **`F`** — Search or filter options used to build a `Specification<T>` from `org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.repositories`.

Pick the **shallowest** layer that fits: if the API does not need a separate `R`, you might only extend `GenericCrudServiceImpl`. When list endpoints support query filters and you expose DTOs, use **`GenericMappedAndFilteredCrudServiceImpl`**.

The concrete service is an `@ApplicationScoped` bean. Callers inject that bean. `@Transactional` on the write methods, and the injected `TransactionHandler`, `PageableSortValidator`, and `Event<CrudLifecycleEvent>`, apply on that bean.

---

## What you must implement

### `GenericCrudServiceImpl<T, ID>`

1. **`getRepository()`** — Return a `PagingAndSortingAndSpecificationExecutorRepository<T, ID>` (`org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.repositories`). Extend `EntityManagerRepository`, which implements that interface and supplies CRUD, paging, sorting, and specifications.

2. **`validate(T)`** — Business and field validation before create/overwrite. Throw `BadRequestException`, `NotFoundException`, or `InternalException` on failure.

3. **`reconcile(T)`** — Resolve references, defaults, or denormalized state after validation and before save (e.g. load related entities by foreign key).

Optional **hooks** (empty defaults; override when needed):

- **Read:** `afterFindOne(T, ID)`
- **Create:** `beforeCreation(T)`, `afterCreation(T, T)`, `afterCreationCommit(T)`
- **Update:** `beforeOverwrite(T)`, `afterOverWrite(T, T)`, `afterOverwriteCommit(T)`
- **Delete:** `beforeDelete(ID)`, `afterDelete(ID)`, `afterDeleteCommit(T)` (used with `deleteReturning`)

**Utilities:** `exists(ID)` is protected; `findOne(ID)` throws `NotFoundException` when the row is missing.

**Pagination:** `Page`, `PageRequest`, and `Sort` live in `org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.repositories`. `findAll` requires a `PageRequest`: `page >= 0` and `size > 0`, otherwise `BadRequestException`. Override `getAllowedSortProperties()` when sortable fields must be restricted. `PageableSortValidator` rejects unknown sort paths via the JPA metamodel, including nested and embedded paths. When `getAllowedSortProperties()` returns a set, a property outside that set is rejected before the metamodel is consulted.

**Lifecycle events:** `CrudLifecycleInterceptor` fires `CrudLifecycleEvent` (`CREATED`, `OVERWRITTEN`, `DELETED`) around the write methods. Observers use `@Observes(during = TransactionPhase.AFTER_SUCCESS)`. `HibernateChangeDataCapture` (`org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.database`) fires `EntityChangeEvent` from the Hibernate post-insert, post-update, and post-delete listener. Observers of `EntityChangeEvent` use the same `@Observes(during = TransactionPhase.AFTER_SUCCESS)`.

### `GenericMappedCrudServiceImpl<R, T, ID>`

Adds mapping between API and persistence:

1. **`toRes(T entity)`** — Entity → API resource.
2. **`toEntity(R resource)`** — API resource → entity (for incoming bodies).

Delegation to a dedicated mapper class is common. Mapped operations are:

- `findAllResources` / `findOneResource` / `createResource` / `overwriteResource` / `deleteReturningResource`

Reads run inside `TransactionHandler.runInTransaction(...)` so lazy loading and mapping stay within a transactional boundary. `createResource` and `overwriteResource` call `create` / `overwrite` on the CDI bean so `@Transactional` and the after-commit hooks run.

### `GenericMappedAndFilteredCrudServiceImpl<F, R, T, ID>`

1. **`getSpecFromFilters(F filters)`** — Build a `Specification<T>`. `Specification` is a `@FunctionalInterface` whose method is `toPredicate(Root, CriteriaQuery, CriteriaBuilder)`. Compose smaller specs with `SpecsUtils.combineWithAnd` and `SpecsUtils.combineWithOr`.

2. **`getRepository()`** — Must return `PagingAndSortingAndSpecificationExecutorRepository<T, ID>` so filtered queries are supported. `EntityManagerRepository` already does.

Filtered list entry points:

- `findAllFiltered(PageRequest, F)` — entities
- `findAllResourcesFiltered(PageRequest, F)` — mapped resources

**Note:** Override `findAll` / `findAllResources` only if you need different list behavior (for example, disabling generic pagination for a given aggregate).

---

## Fixed algorithm (template method)

The base class fixes the **order of operations**; subclasses should not change the sequence unless they intentionally override a public method (usually avoided).

**Create** (`@Transactional`): `validate` → `reconcile` → `beforeCreation` → `save` → `afterCreation` → fire `CrudLifecycleEvent.CREATED` → register `afterCreationCommit` to run after `STATUS_COMMITTED`

**Overwrite** (`@Transactional`): `validate` → existence check (`checkExistenceOrThrow`) → `reconcile` → `beforeOverwrite` → `save` → `afterOverWrite` → fire `CrudLifecycleEvent.OVERWRITTEN` → register `afterOverwriteCommit` to run after `STATUS_COMMITTED`

**Delete** (`@Transactional`): existence check → `beforeDelete` → `deleteById` → `afterDelete` → fire `CrudLifecycleEvent.DELETED`

**`deleteReturning`:** `findOne` → map with the provided `Function` → `delete` on the CDI bean → `afterDeleteCommit`

`findOne` loads the row inside `TransactionHandler.runInTransaction`, throws `NotFoundException` when it is missing, then calls `afterFindOne`. `findAll` validates the `PageRequest` and the sort, then queries, inside `TransactionHandler.runInTransaction`.

Create, overwrite, and delete are `@Transactional` on the CDI bean. `afterCreationCommit` and `afterOverwriteCommit` are registered on `TransactionSynchronizationRegistry` and run only when the completion status is `STATUS_COMMITTED`. Read paths in mapped layers use **`TransactionHandler`**.

---

## Transactions: two mechanisms

- **`@Transactional`** on `create`, `overwrite`, and `delete` in `GenericCrudServiceImpl`: the write boundary. The concrete service is an `@ApplicationScoped` bean.
- **`TransactionHandler`** (`@ApplicationScoped`): `runInTransaction` is `REQUIRED`; `runInNewTransaction` is `REQUIRES_NEW`. Mapped layers use `runInTransaction` for reads that map entities to resources.

Use-case work that must be atomic and serializable uses `TransactionalOutboundPort`. `DefaultTransactionalOutboundPortImpl` runs that work with `QuarkusTransaction.joiningExisting()` and executes `SET TRANSACTION ISOLATION LEVEL SERIALIZABLE` on the PostgreSQL connection before the caller’s work.

When one service calls another (e.g. loading a related entity in `reconcile`), use the existing service API and stay aware of transaction boundaries if you need atomicity across multiple aggregates.

---

## CRUD AOP (interceptor wrap)

`GenericCrudServiceImpl` is bound with `@GenericCrudIntercepted`. `CrudLifecycleInterceptor` extends `GenericCrudInterceptor` and fires `CrudLifecycleEvent` after create / overwrite / delete (including the mapped `*Resource` methods).

To wrap the same methods (history, stewardship, emitters, …), subclass `GenericCrudInterceptor`, override `aroundFind` / `aroundCreate` / `aroundOverwrite` / `aroundDelete`, and register the subclass as a CDI interceptor with `@GenericCrudIntercepted` and `@Priority`. Nested proxy calls (for example `createResource` → `create`) skip the `around*` methods so advice runs once.

## JAX-RS resources and public API

- Prefer **`…Resource` methods** for HTTP adapters so the layer works with `R`, not entities.
- If path parameters must **override** fields in the body (e.g. id in URL vs body), set the field from the path, then call the `*Resource` method.
- JAX-RS resources live in the adopting service (`@Path`). Do not put them under a versioned `rest` package. Template inbound HTTP helpers live in `org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.rest`. Outbound HTTP uses `org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.client.RestUtils`.

---

## Example shape (conceptual)

A typical filtered, mapped service:

- `@ApplicationScoped`, extends `GenericMappedAndFilteredCrudServiceImpl<MySearchOptions, MyResource, MyEntity, MyIdType>`.
- Implements `validate`, `reconcile`, `getSpecFromFilters`, `getRepository`, `toRes`, `toEntity`.
- Uses `beforeCreation` / `beforeOverwrite` for rules that depend on persisted state (e.g. uniqueness excluding the current row).
- Builds `Specification` instances from filter fields, composed with `SpecsUtils` AND/OR as needed.

Name filters can use `SpecsUtils.escapeLikeParameter`. Return an allow-list from `getAllowedSortProperties()` when sortable fields must be restricted.

---

## Design tips

1. **Keep `validate` focused** on invariants, format, and required fields; use **`reconcile`** for loading associations and fixing navigable graphs before `save`.

2. **Use hooks** for database-dependent rules (uniqueness excluding self, side effects) so they run in the same transaction as `save` / `delete`.

3. **Repository** must support paging and specifications if you use the filtered base class. Extend `EntityManagerRepository`. Static nested spec helpers on the repository are a common style; `SpecsUtils` combines them with AND/OR.

4. **Throw** `NotFoundException`, `BadRequestException`, `ResourceConflictException`, and `InternalException` (`OdmApiException` in `org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions`). The adopting service maps those exceptions to HTTP status codes.

5. If an aggregate **must not** support generic `findAll(PageRequest)`, override those methods and throw an explicit "not supported" (or equivalent) with a clear message.

6. **`PageableSortValidator`** rejects unknown sort paths via the JPA metamodel, including nested and embedded paths. An allow-list returned from `getAllowedSortProperties()` is checked first.
