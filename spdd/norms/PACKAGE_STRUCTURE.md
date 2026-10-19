# Norm: Package structure

Where code lives. The organising principle is **package by aggregate first, then by layer**, with the HTTP adapter kept in its own **versioned** tree. Layer responsibilities are defined in [`USE_CASE_IMPLEMENTATION.md`](./USE_CASE_IMPLEMENTATION.md) and [`GENERIC-CRUD-GUIDELINES.md`](./GENERIC-CRUD-GUIDELINES.md); this file only fixes placement.

---

## Canonical tree

```text
<basePackage>
├── <aggregate>/
│   ├── entities/                    persistent model owned by the service
│   ├── repositories/
│   └── services/
│       ├── *UseCasesService         application service (bridge HTTP ↔ use cases)
│       ├── core/                    core services: single entry point to the aggregate's entities
│       └── usecases/<verb>/         one use case slice (command, presenter, ports, adapters, factory)
├── rest/
│   └── v<N>/
│       ├── controllers/             *Controller / *UseCaseController
│       └── resources/<aggregate>/   *Res DTOs and their mappers
│           └── usecases/<verb>/     *CommandRes / *ResultRes of one use case
├── config/                          typed configuration and its validation
├── exceptions/                      the service exception hierarchy
└── utils/                           owned by the template — do not add feature code here
```

`<N>` is the API major version (see [`API_DESIGN.md`](./API_DESIGN.md)).

---

## Placement

| You are adding… | It goes… |
| --- | --- |
| Entity, repository, CRUD or query logic on owned data | `<aggregate>/{entities, repositories, services/core}` |
| A workflow (command, presenter, ports, adapters, factory) | `<aggregate>/services/usecases/<verb>` |
| The bridge between DTOs and use cases | `<aggregate>/services/*UseCasesService` |
| A collaborator used only by one owner (renderer, rule evaluator, mapper) | Next to its owner — it is neither core nor client |
| An outbound integration with a system the service does not own | A **client** — one package per external system, never inside `core` (exact location chosen by the service; see [`INTEGRATIONS.md`](./INTEGRATIONS.md)) |
| A mapper from an inbound third-party payload to domain types | Next to the application service that consumes it |
| Controller | `rest/v<N>/controllers` |
| Request / response DTO | `rest/v<N>/resources/<aggregate>[/usecases/<verb>]` |
| Typed configuration | `config` |

---

## Rules

- **No layer-first top level.** Do not create top-level `services/`, `entities/` or `repositories/` packages shared by all aggregates.
- **No owned entities, no persistence packages.** A service that owns no entities has no `entities`, `repositories` or `core` package.
- **Use cases never reach into `rest`.** Nothing under `<aggregate>/services` depends on `rest/**`, except the `*UseCasesService`, which is the bridge.
- **`utils` is template territory.** Feature code never goes there; template upgrades may overwrite it.
