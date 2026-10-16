# ODM Platform Marketplace Executor Starter

> A starter project for building a **Marketplace Executor** for the [Open Data Mesh Platform](https://dpds.opendatamesh.org/) —
> grant and revoke access to data products on your enterprise tools.

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=flat-square" alt="License: Apache 2.0"></a>
</p>

<p align="center">
  <a href="https://openjdk.org/"><img src="https://img.shields.io/badge/Java-21-ED8B00.svg?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21"></a>
  <a href="https://quarkus.io/"><img src="https://img.shields.io/badge/Quarkus-3.40-4695EB.svg?style=flat-square&logo=quarkus&logoColor=white" alt="Quarkus 3.40"></a>
</p>

<p align="center">
  <a href="#what-the-executor-does">What the executor does</a> ·
  <a href="#quick-start-local">Quick start</a> ·
  <a href="#executor-api">Executor API</a> ·
  <a href="#implement-your-executor">Implement your executor</a> ·
  <a href="#contributing">Contributing</a>
</p>

---

## What the executor does

The Marketplace Executor is the component that bridges the **Data Product Catalog** and your enterprise tools. It manages access to the data products listed in the catalog, turning the catalog into a true **Data Product marketplace**.

- The **Marketplace Service** receives an access request (subscribe or unsubscribe) and forwards it to the executor.
- The executor **grants or revokes access** on the target tool. The complexity of orchestrating multiple calls is delegated to it.
- The executor **reports the outcome** back to the Marketplace Service, asynchronously.

```mermaid
sequenceDiagram
  participant M as Marketplace Service
  participant E as Marketplace Executor
  participant T as Enterprise tool
  M->>E: POST /requests (subscribe / unsubscribe)
  E-->>M: 202 Accepted
  E->>T: grant or revoke access
  E->>M: POST /requests/{identifier}/results
```

This starter is stateless (no database) and is meant to be **forked and customized**.

---

## Quick start (local)

**Requirements:** Java **21** · Maven **3.9+** (or the included `./mvnw`)

```bash
git clone https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-starter.git
cd odm-platform-adapter-marketplace-executor-starter

./mvnw quarkus:dev
```

The executor listens on port **9019** (`quarkus.http.port` in `application.yml`). When it is up:

| | Endpoint |
|:--|:---------|
| **Swagger UI** | [http://localhost:9019/swagger-ui](http://localhost:9019/swagger-ui) |
| **OpenAPI** | [http://localhost:9019/api-docs](http://localhost:9019/api-docs) |

Other builds:

```bash
./mvnw package                  # JVM build, runner in target/quarkus-app/
./mvnw -Pnative package         # native build (Mandrel or GraalVM 21)
./mvnw test                     # tests (outbound calls are mocked)
```

---

## Executor API

The Marketplace Service calls a single endpoint:

```
POST {executorBaseUrl}/api/v1/up/marketplace-executor/requests
```

It answers **202 Accepted** and processes the request asynchronously. An invalid request returns **400** with an error body.

<details open>
<summary><strong>Request</strong></summary>

<br>

```json
{
  "v": "1",
  "operation": "MARKETPLACE_SUBSCRIBE | MARKETPLACE_UNSUBSCRIBE",
  "request": {
    "name": "",
    "identifier": "",
    "provider": {
      "dataProductFqn": "",
      "dataProductPortsFqn": [""]
    },
    "consumer": {
      "type": "dataproduct | user | team",
      "identifier": "fqn | email | ?"
    },
    "requester": {
      "type": "user",
      "identifier": "email"
    },
    "properties": {},
    "startDate": "Date",
    "endDate": "Date"
  }
}
```

</details>

<details open>
<summary><strong>Result sent to the Marketplace Service</strong></summary>

<br>

Once processed, the executor posts the result to
`{marketplaceServiceAddress}/api/v1/pp/marketplace/requests/{identifier}/results`:

```json
{
  "status": "PENDING | GRANTED | DENIED | REVOKED | ERROR",
  "message": "",
  "provider": {
    "dataProductFqn": "",
    "dataProductPortsFqn": [""]
  },
  "createdAt": "Date"
}
```

</details>

| Status | Meaning |
|:-------|:--------|
| `PENDING` | The grant process has started but needs additional work |
| `GRANTED` | The permissions have been correctly granted |
| `DENIED` | The grant process has been denied |
| `REVOKED` | The permissions have been correctly revoked |
| `ERROR` | An error occurred during the process |

---

## Implement your executor

1. Fork this repository
2. Implement `MarketplaceAccessOutboundPortImpl` (package `marketplace.services.usecases.processrequest`) with the grant and revoke logic of your enterprise tool
3. Set the Marketplace Service address in `src/main/resources/application.yml`:

```yaml
odm:
  product-plane:
    marketplace-service:
      active: true
      address: http://localhost:8085
```

4. Build and deploy your custom executor

**Implementation notes**

- Each request has a unique `identifier` generated by the Marketplace Service
- Keep detailed logs of the resources created during the access grant process
- Implement error handling and rollback as needed
- Access can be time-bound through `startDate` and `endDate`

**Code layout**

| Package | Role |
|:--------|:-----|
| `rest` | JAX-RS resource and error mapping |
| `marketplace.services` | Use cases service: maps API resources to domain commands |
| `marketplace.services.usecases.processrequest` | Use case, ports, adapters, factory |
| `utils`, `exceptions` | Shared utilities from the template |

Conventions live in [`spdd/norms/`](spdd/norms/README.md).

---

## Related links

- [Open Data Mesh Architecture](https://platform.opendatamesh.org/architecture/)
- [ODM Initiative Discussion](https://github.com/orgs/opendatamesh-initiative/discussions/1)

---

## Contributing

This is a starter project intended to be forked and customized.

1. Fork the repository and create a feature branch
2. Make your changes with clear commits
3. Open a pull request against the main branch

Questions or proposals → [ODM Initiative Discussion](https://github.com/orgs/opendatamesh-initiative/discussions/1).

---

## License

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

Part of the [Open Data Mesh Initiative](https://github.com/opendatamesh-initiative).
