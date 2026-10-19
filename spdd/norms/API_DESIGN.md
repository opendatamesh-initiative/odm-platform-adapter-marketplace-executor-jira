# Norm: API design and versioning

How the public HTTP API is shaped and how it evolves.

---

## Versioning

- **The major version appears in the path and in the package:** `/api/v<N>/…` is served by `rest.v<N>`.
- **A new major version only for breaking changes:** removing or renaming an endpoint, field or enum value; changing a field's type or meaning; making an optional input required; changing status codes or the error contract. Additive changes (new endpoints, new optional fields, new enum values clients are told to tolerate) stay in the current version.
- **Versions coexist.** A previous version keeps its own controllers and DTOs until it is retired; it is not rewritten in terms of the new one's DTOs.
- **Deprecate before removing.** A version or endpoint is marked deprecated in the API documentation before it is removed.

## Contract

- **Every public endpoint is documented in OpenAPI** (operation, parameters, request and response schemas, every status code it can return). Non-public endpoints are hidden from the documentation.
- **Uniform responses.** All endpoints share one error contract and the same conventions for paging, identifiers and timestamps.
- **The API speaks DTOs, never entities.** `*Res` types are the contract; persistence and domain types never cross the HTTP boundary.
- **Thin controllers.** A controller maps HTTP to one application-service call; it holds no business rules.

## Contracts owned by others

- When an endpoint receives a payload defined by another system, that payload follows its owner's contract, not this norm. It is mapped at the edge (see [`INTEGRATIONS.md`](./INTEGRATIONS.md)).

---

**Quarkus:** controllers are JAX-RS `@Path` classes; OpenAPI uses the MicroProfile OpenAPI annotations (SmallRye).
