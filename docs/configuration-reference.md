# Configuration reference

[Back to README](../README.md)

## Overview

The executor is configured entirely through properties: where Jira is, how to log in, which requests to accept, what issue to create for each operation, and how to read the answer. Simple values (URLs, credentials) come from environment variables. Issue profiles and outcome rules are structured and go in a YAML file.

All keys live under the `jira` prefix (YAML, kebab-case) unless stated otherwise. Any key can be overridden by an environment variable using the standard Quarkus mapping (upper case, dots and dashes become underscores); the explicit variables below are the ones referenced in `application.yml`.

Related: [Provisioning](provisioning.md) · [Use cases](use-cases.md) · [Templates](templates.md) · [Troubleshooting](troubleshooting.md)

## Where to put the configuration

The packaged `src/main/resources/application.yml` contains the connection, authentication and message defaults, but **no issue profiles**. You must provide `jira.profiles.subscribe` and `jira.profiles.unsubscribe` yourself. If they are missing, the executor refuses to start with a "required property ... not found" error. Put them (and any other overrides) in an external file:

- `config/application.yml` in the executor's working directory (the easiest choice with a container volume mount),
- or any path passed with `QUARKUS_CONFIG_LOCATIONS=/path/file.yml` (or `-Dquarkus.config.locations=...`).

Why no packaged profiles? Quarkus merges configuration sources entry by entry: list items by index and map entries by key. Default `fields` or `outcome-rules` shipped in the jar would be merged into yours instead of being replaced, so a rule of yours could silently inherit a packaged condition and never match. Scalar values can still be overridden safely through environment variables.

Secrets (`JIRA_CLIENT_SECRET`, `JIRA_API_TOKEN`, `JIRA_PAT` and the Automation webhook token) must come from the environment or from mounted secrets, never from a committed file.

## Connection and authentication

| Property | Environment variable | Description | Default | Required |
|:--|:--|:--|:--|:--|
| `jira.deployment` | `JIRA_DEPLOYMENT` | `CLOUD` or `DATA_CENTER` | `CLOUD` | no |
| `jira.site-url` | `JIRA_SITE_URL` | Site base URL, used for browse links and, on Data Center, as the API base. `https` (`http` only for `http://localhost...`) | none | **yes** |
| `jira.api-url` | `JIRA_API_URL` | API base URL. Cloud with OAuth must use `https://api.atlassian.com/ex/jira/<cloudId>` | the site URL | Cloud + OAuth |
| `jira.api-version` | `JIRA_API_VERSION` | `2` or `3`. Platform paths are `/rest/api/{version}/...`, JSM paths are `/rest/servicedeskapi/...`. `3` is Cloud only | `2` | no |
| `jira.auth.type` | `JIRA_AUTH_TYPE` | `OAUTH2_CLIENT_CREDENTIALS`, `API_TOKEN` (Cloud) or `PERSONAL_ACCESS_TOKEN` (Data Center) | `OAUTH2_CLIENT_CREDENTIALS` | no |
| `jira.auth.oauth2.client-id` | `JIRA_CLIENT_ID` | OAuth client id | empty | with OAuth |
| `jira.auth.oauth2.client-secret` | `JIRA_CLIENT_SECRET` | OAuth client secret | empty | with OAuth |
| `jira.auth.oauth2.token-url` | none | Token endpoint override | Cloud `https://auth.atlassian.com/oauth/token`; Data Center `{site-url}/rest/oauth2/latest/token` | no |
| `jira.auth.oauth2.scope` | none | OAuth scope, omitted from the token request when not set | none | no |
| `jira.auth.api-token.email` | `JIRA_API_TOKEN_EMAIL` | Account email, sent as `Authorization: Basic base64(email:token)` | empty | with `API_TOKEN` |
| `jira.auth.api-token.token` | `JIRA_API_TOKEN` | Cloud API token | empty | with `API_TOKEN` |
| `jira.auth.pat.token` | `JIRA_PAT` | Data Center Personal Access Token, sent as `Authorization: Bearer <pat>` | empty | with `PERSONAL_ACCESS_TOKEN` |
| `jira.correlation.field` | `JIRA_CORRELATION_FIELD` | Text custom field that stores the request UUID, `customfield_<digits>` | none | **yes** |
| `jira.correlation.label` | none | Also add the label `bdmp-<uuid>` to the issue | `false` | no |
| `jira.webhook.extra-fields.<name>` | none | Extra normalised event field read from the native payload with a JSON Pointer | none | no |
| `jira.rate-limit.max-attempts` | none | Attempts for an issue creation answered with HTTP `429` | `3` | no |
| `jira.rate-limit.backoff` | none | First wait, doubled after every attempt | `2s` | no |

Authentication is resolved once, at startup, per deployment:

| Deployment | Default | Alternative |
|:--|:--|:--|
| **Cloud** | Service account, OAuth 2.0 client credentials (token cached until `expires_in`) | API token (`API_TOKEN`). `PERSONAL_ACCESS_TOKEN` is **not** available |
| **Data Center** | Personal Access Token | Data Center 11.1+ service account (client credentials). REST v2 only |

Note that the application default for `jira.auth.type` is `OAUTH2_CLIENT_CREDENTIALS` on both deployments: on Data Center, set `JIRA_AUTH_TYPE=PERSONAL_ACCESS_TOKEN` explicitly.

## Marketplace Service

| Property | Environment variable | Description | Default | Required |
|:--|:--|:--|:--|:--|
| `odm.product-plane.marketplace-service.active` | none | Enables the Marketplace Service client | `true` | no |
| `odm.product-plane.marketplace-service.address` | `ODM_PRODUCT_PLANE_MARKETPLACE_SERVICE_ADDRESS` | Marketplace Service base URL | `http://localhost:8085` | no |
| `odm.product-plane.marketplace-service.result-delivery.max-attempts` | none | Attempts to post a result | `5` | no |
| `odm.product-plane.marketplace-service.result-delivery.backoff` | none | Wait between attempts | `1s` | no |

Retry behaviour is described in [Concepts](concepts.md#rate-limiting-and-delivery-retries).

## Server and runtime

| Property | Description | Default |
|:--|:--|:--|
| `quarkus.http.port` | HTTP port | `9019` |
| `quarkus.config.locations` (`QUARKUS_CONFIG_LOCATIONS`) | Extra configuration files | none |
| `quarkus.swagger-ui.path` / `quarkus.smallrye-openapi.path` | Swagger UI and OpenAPI paths | `/swagger-ui`, `/api-docs` |
| `quarkus.qute.strict-rendering`, `quarkus.qute.property-not-found-strategy` | Must stay `false` and `noop` | preconfigured |

## Request filter

`jira.filter.*` decides which requests this executor handles. Every value is a **list of regular expressions** that must **fully match** (`(?i)` makes a pattern case-insensitive). A criterion that is not configured matches everything; an empty filter handles every request.

| Key | Matched against |
|:--|:--|
| `jira.filter.operations` | `MARKETPLACE_SUBSCRIBE` / `MARKETPLACE_UNSUBSCRIBE` |
| `jira.filter.provider-data-product-fqns` | `request.provider.dataProductFqn` |
| `jira.filter.port-fqns` | **Every** port in `request.provider.dataProductPortsFqn` (a request without ports never matches a configured port criterion) |
| `jira.filter.consumer-types` | `request.consumer.type` |
| `jira.filter.consumer-identifiers` | `request.consumer.identifier` |
| `jira.filter.requester-identifiers` | `request.requester.identifier` |
| `jira.filter.properties.<key>` | `request.properties.<key>` (a single regex per key; a missing property does not match) |

**All configured criteria must match (AND)**; within a list, **any** pattern may match (OR). For ports, **every** requested port must match some pattern, because a request that only partly belongs to this executor cannot be partially granted: it is **declined as a whole**.

```yaml
jira:
  filter:
    operations: ["MARKETPLACE_.*"]
    provider-data-product-fqns:
      - 'urn:dp:finance:.*'
      - 'urn:dp:risk:.*'
    consumer-types: ['(?i)user|team']
    properties:
      costCenter: 'FIN-.*'
```

A declined request gets `409` and produces no side effect. If **no** executor accepts a request, the Marketplace Service rolls the submission back with "No executor was able to handle the request": the request fails fast instead of staying `PENDING` forever. The filter is re-applied when a notification arrives (`NOT_HANDLED_BY_EXECUTOR`). In an environment variable, a comma separates list items; if a regex contains a comma, configure it in the YAML file. For running several executors, see [Use case 5](use-cases.md#use-case-5---several-executors-behind-one-marketplace).

## Issue profiles

One profile per operation: `jira.profiles.subscribe.*` (for `MARKETPLACE_SUBSCRIBE`) and `jira.profiles.unsubscribe.*` (for `MARKETPLACE_UNSUBSCRIBE`). Both are required.

| Key (under `profiles.<op>`) | Default | Description |
|:--|:--|:--|
| `type` | **required** | `ISSUE`, `SERVICE_DESK_REQUEST` or `AUTOMATION_WEBHOOK` |
| `project`, `issue-type` | none | `ISSUE`: project key and issue type name, both **required** |
| `service-desk-id`, `request-type-id` | none | `SERVICE_DESK_REQUEST`: both **required** |
| `webhook-url`, `webhook-token` | none | `AUTOMATION_WEBHOOK`: both **required**, the URL must be https (or `http://localhost`). Treat both as secrets |
| `correlation-via-edit` | `false` | `SERVICE_DESK_REQUEST` only: set the correlation field with a follow-up `PUT` after the request is created, instead of at creation |
| `set-reporter` | `false` | `ISSUE` only: set the issue reporter to the marketplace requester |
| `fields.<name>` | none | Field name to [template](templates.md#issue-content). Rendered to a JSON string |
| `object-fields.<name>.<entry>` | none | Field that is a JSON object, one template per entry |
| `list-fields.<name>` | none | Field that is a JSON array of strings, one template per item |
| `rich-text-fields` | `[description]` | With `api-version: 3`, the rendered values of these fields are converted to ADF |
| `outcome-rules[n].when.<field>` | none | Condition: normalised event field to regex (full match) |
| `outcome-rules[n].outcome` | none | `FULFILLED`, `REFUSED` or `FAILED` |
| `outcome-rules[n].message` | none | Optional message template for this rule only |
| `messages.pending` / `fulfilled` / `refused` / `failed` / `technical-error` | none | Optional per-profile message overrides |

See [Concepts](concepts.md#outcome-rules) for how outcome rules are evaluated and [Templates](templates.md#messages) for the global messages.

### Messages

| Property | Environment variable | Default |
|:--|:--|:--|
| `jira.messages.pending` / `fulfilled` / `refused` / `failed` / `technical-error` | none | see [Templates](templates.md#messages) |
| `jira.messages.closing-user.include` | `JIRA_MESSAGES_INCLUDE_CLOSING_USER` | `true` |
| `jira.messages.closing-user.field` | none | `actor` |

## Startup validation

The executor **refuses to start with an invalid configuration** and prints a numbered list of problems (`Invalid Jira executor configuration:`). Secrets are never printed. The checks are:

- `jira.site-url` and `jira.api-url` must be valid https URLs (http only for `http://localhost`).
- `jira.api-version` must be 2 or 3; 3 is not allowed on Data Center.
- `PERSONAL_ACCESS_TOKEN` is not allowed on Cloud.
- Credentials are required for the chosen auth type (OAuth client id and secret; API token email and token; PAT token).
- On Cloud with OAuth, `jira.api-url` must start with `https://api.atlassian.com/ex/jira/`.
- `jira.correlation.field` must match `customfield_<digits>`.
- Per profile: `project` and `issue-type` (`ISSUE`), `service-desk-id` and `request-type-id` (`SERVICE_DESK_REQUEST`), `webhook-url` and `webhook-token` (`AUTOMATION_WEBHOOK`).
- No profile may declare a [reserved field](concepts.md#reserved-fields).
- Each profile needs at least one outcome rule, at least one `FULFILLED`, and every rule a non-empty `when`.
- Every regex (filter and outcome rules) must compile, and every template must parse.
