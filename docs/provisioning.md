# Provisioning

[Back to README](../README.md)

## Overview

This page explains how to build the executor, run it, connect it to the rest of the platform and check that it works. The executor is a standard Quarkus service: one process, no database, port 9019. It needs to reach Jira and the Marketplace Service, and it must be reachable by the Marketplace Service and by the gateway that authenticates Jira ([Securing the notification endpoints](#securing-the-notification-endpoints)).

The repository does not ship a Dockerfile, Helm chart or Kubernetes manifests. Run the build output directly, or wrap it in your own container image.

Related: [Jira setup](jira-setup.md) · [Configuration reference](configuration-reference.md) · [Troubleshooting](troubleshooting.md)

## Requirements

- Java **21** and Maven **3.9+** (or the included `./mvnw`). The project uses Quarkus 3.40.1.
- Jira Cloud or Data Center 8.14+ ([prepared as in Jira setup](jira-setup.md)).
- A running Marketplace Service (default address `http://localhost:8085`).
- An authenticating gateway or reverse proxy in front of the executor's webhook endpoints, for the notification leg (see [Securing the notification endpoints](#securing-the-notification-endpoints)).

## Build

```bash
./mvnw package                  # JVM build, runner in target/quarkus-app/
./mvnw -Pnative package         # native build (Mandrel or GraalVM 21)
./mvnw test                     # tests (outbound calls are mocked)
```

The JVM build is started with `java -jar target/quarkus-app/quarkus-run.jar`.

## Configure

1. Set the environment variables:

| Environment variable | Purpose |
|:--|:--|
| `JIRA_DEPLOYMENT` | `CLOUD` (default) or `DATA_CENTER` |
| `JIRA_SITE_URL` | **Required.** Site base URL |
| `JIRA_API_URL` | API base URL. **Required on Cloud with OAuth** (`https://api.atlassian.com/ex/jira/<cloudId>`) |
| `JIRA_API_VERSION` | `2` (default) or `3` (Cloud only) |
| `JIRA_AUTH_TYPE` | `OAUTH2_CLIENT_CREDENTIALS` (default), `API_TOKEN` or `PERSONAL_ACCESS_TOKEN` |
| `JIRA_CLIENT_ID`, `JIRA_CLIENT_SECRET` | OAuth 2.0 client credentials |
| `JIRA_API_TOKEN_EMAIL`, `JIRA_API_TOKEN` | Cloud API token credentials |
| `JIRA_PAT` | Data Center Personal Access Token |
| `JIRA_CORRELATION_FIELD` | **Required.** Id of the correlation custom field |
| `JIRA_MESSAGES_INCLUDE_CLOSING_USER` | `false` to keep the Jira actor out of result messages (default `true`) |
| `ODM_PRODUCT_PLANE_MARKETPLACE_SERVICE_ADDRESS` | Marketplace Service base URL |
| `QUARKUS_CONFIG_LOCATIONS` | Path of an extra configuration file |

2. Provide the issue profiles (`jira.profiles.subscribe` and `jira.profiles.unsubscribe`) in an external file: `config/application.yml` in the working directory, or the path in `QUARKUS_CONFIG_LOCATIONS`. Start from the profile of the [use case](use-cases.md) that matches your Jira practice. Use case 1 (Issue API) works as-is on a project that has a `Task` issue type.

Full details: [Configuration reference](configuration-reference.md). Secrets must come from the environment or mounted secrets, never from a committed file.

Local run, for example on Cloud:

```bash
export JIRA_DEPLOYMENT=CLOUD
export JIRA_SITE_URL=https://acme.atlassian.net
export JIRA_API_URL=https://api.atlassian.com/ex/jira/<cloudId>
export JIRA_AUTH_TYPE=OAUTH2_CLIENT_CREDENTIALS
export JIRA_CLIENT_ID=<service account client id>
export JIRA_CLIENT_SECRET=<service account client secret>
export JIRA_CORRELATION_FIELD=customfield_10050
export ODM_PRODUCT_PLANE_MARKETPLACE_SERVICE_ADDRESS=http://localhost:8085

./mvnw quarkus:dev
```

The executor **refuses to start with an invalid configuration** and prints a numbered list of problems; see [startup validation](configuration-reference.md#startup-validation).

## Deploy

Any way of running a Java 21 process works (VM, container, Kubernetes). Things to provide:

- Port **9019** (`quarkus.http.port`).
- The environment variables and secrets above, the profile YAML as a mounted file, and the working directory or `QUARKUS_CONFIG_LOCATIONS` pointing at it.
- Outbound access to Jira (or `api.atlassian.com` and `auth.atlassian.com` on Cloud) and to the Marketplace Service.
- **Network isolation**: the executor's endpoints have no authentication of their own. It must be reachable **only by the Marketplace Service and by the authenticating gateway**, never directly from Jira or the internet; otherwise anyone who can reach it can forge final results. The gateway may forward the caller's headers; the executor ignores them. The executor never logs or echoes request headers, bodies or tokens; keep it that way in any proxy or access log you add in front of it.

## Registering with the Marketplace Service

The Marketplace Service must know the executor so that it forwards requests to `POST /api/v1/up/marketplace-executor/requests`, and the executor must know the Marketplace Service (`ODM_PRODUCT_PLANE_MARKETPLACE_SERVICE_ADDRESS`) to post results to `/api/v1/pp/marketplace/requests/{uuid}/results`. Register the executor's base URL in the Marketplace Service according to its own documentation; this repository does not contain that step. Requests the executor's filter does not accept are answered `409`.

## Securing the notification endpoints

The two notification endpoints (`.../webhooks/jira/issue-events` and `.../webhooks/jira/native-events`) have **no authentication of their own**: anyone who can reach them can forge a final result for a request. They must therefore never be exposed to Jira or the internet directly. Put an authenticating gateway or reverse proxy in front of them, with these properties:

- It authenticates Jira (or the component that relays Jira's calls).
- It forwards **only** the two webhook paths. In particular `POST .../requests` must not be reachable through it, because it would let a caller inject access requests bypassing the Marketplace Service.
- The executor is reachable only by the Marketplace Service and by this gateway (network policy or firewall).
- For **native Jira webhooks**, the gateway also verifies the `X-Hub-Signature` HMAC, because the executor does not.

Choose one of the options below.

### Option A: your own authenticating gateway

Any API gateway, reverse proxy or small function works. Authenticate the caller with whatever fits your environment (API key, bearer token, mTLS, IP allow-list combined with a secret), allow-list the two webhook paths and forward to the executor.

- **Jira Automation rule** (channel A): the rule can send a fixed secret header (marked *Hidden*) that the gateway checks. See [Jira setup](jira-setup.md#8-completion-channel-a---jira-automation-rule-recommended).
- **Native webhook** (channel B): Jira can only sign the call. The gateway verifies the signature, adds the credentials expected by your gateway or upstream and forwards. A reference sketch and a security checklist are in [Jira setup](jira-setup.md#9-completion-channel-b---native-webhook).
- Never log headers, bodies or tokens, and keep the gateway's secrets in a secret store.

### Option B: blindata-agent

blindata-agent is a ready-made option. Its ODM proxy authenticates and authorizes the notification endpoints, so the executor needs no changes. Native webhooks still need the signature-verifying gateway of option A in front of it.

1. **Register the executor in the ODM proxy configuration.** Add the executor's base URL to `servicesURLs`, with a key of the form `marketplace-executor-<suffix>`, for example `marketplace-executor-jira`:

```json
{
  "servicesURLs": {
    "marketplace-executor-jira": "http://marketplace-executor-jira.internal:9019"
  }
}
```

The key is a contract: it is the `servicesURLs` key, the `{service}` segment of the proxy URL, and it selects the agent's generic marketplace-executor authorization filter. Any family name works as long as it starts with `marketplace-executor`. The agent forwards `/agent-api/v1/odm/proxy/{configUuid}/{service}/**` to that base URL, keeping the rest of the path and the query string.

2. **The URLs Jira (or your gateway) calls:**

```
https://<agent-host>/agent-api/v1/odm/proxy/<configUuid>/marketplace-executor-jira/api/v1/up/marketplace-executor/webhooks/jira/issue-events
https://<agent-host>/agent-api/v1/odm/proxy/<configUuid>/marketplace-executor-jira/api/v1/up/marketplace-executor/webhooks/jira/native-events
```

`<configUuid>` is the identifier of the ODM proxy configuration in the Blindata platform.

3. **A dedicated Blindata application user.** Create an application user (API key) for Jira with the **`MARKETPLACE_EDITOR`** permission and nothing else. The caller sends its credentials in the `X-BD-User` and `X-BD-ApiKey` headers.

4. **Authorization.** The agent's generic `marketplace-executor` filter allows a caller with `MARKETPLACE_EDITOR` to **`POST` only inside the executors' webhooks namespace `.../api/v1/up/marketplace-executor/webhooks/**`**, which holds both Jira endpoints. Every other method and path of the executor, notably `POST .../requests` (which would let a caller inject access requests bypassing the Marketplace Service), is answered with `403`. **Never use `DATAOPS_ADMIN` for the Jira identity**: it bypasses the filter and can reach every executor path. The filter is delivered with the agent and must be released together with this executor. The agent needs no Jira-specific code.

5. **Smoke test through the agent** (harmless: an unknown UUID is acknowledged and ignored):

```bash
curl -i -X POST "https://<agent-host>/agent-api/v1/odm/proxy/<configUuid>/marketplace-executor-jira/api/v1/up/marketplace-executor/webhooks/jira/issue-events" \
  -H "Content-Type: application/json" \
  -H "X-BD-User: <application user>" -H "X-BD-ApiKey: <api key>" \
  -d '{"correlationId":"00000000-0000-0000-0000-000000000000","fields":{"statusCategory":"done"}}'
# -> 200 {"outcome":"IGNORED","reason":"UNKNOWN_REQUEST"}
```

A `403` means the application user lacks `MARKETPLACE_EDITOR`, or the route points to a different service key.

## Verifying the installation

1. **The service is up.** Swagger UI at `http://localhost:9019/swagger-ui` and OpenAPI at `http://localhost:9019/api-docs`.
2. **The notification route works.** The smoke test is harmless: an unknown UUID is acknowledged and ignored. Call it through your gateway, with the credentials it expects:

```bash
curl -i -X POST "https://<gateway-host>/<gateway-path>/api/v1/up/marketplace-executor/webhooks/jira/issue-events" \
  -H "Content-Type: application/json" \
  -H "<gateway credential headers>" \
  -d '{"correlationId":"00000000-0000-0000-0000-000000000000","fields":{"statusCategory":"done"}}'
# -> 200 {"outcome":"IGNORED","reason":"UNKNOWN_REQUEST"}
```

A `401` or `403` comes from the gateway (wrong credentials, or the caller is not authorized for the webhook path); a `200 IGNORED` proves the whole chain works.

3. **End to end.** Submit a request from the marketplace for a data product the filter accepts. Check that an issue is created, that the request shows `PENDING` with the issue link, then complete the issue and check the final status.

## Operations

- **Logs.** See [Troubleshooting](troubleshooting.md#logs). The request UUID and the issue key appear in every business log line.
- **Secret rotation.**
  - *OAuth service account*: create or regenerate the client secret, update `JIRA_CLIENT_SECRET` and restart the executor. Tokens already issued are cached until their expiry.
  - *API token* (Cloud): create the new token before the old one expires (at most 365 days), update `JIRA_API_TOKEN`, restart the executor, then revoke the old token.
  - *Personal Access Token* (Data Center): create the new token, update `JIRA_PAT`, restart, then revoke the old one.
  - *Automation webhook token*: regenerate it in the rule and update the `webhook-token` source.
  - Between the rotation and the restart, issue creation fails with an `ERROR` message that names an authentication failure.
  - The credentials Jira uses towards your gateway are rotated on the gateway side and in the Automation rule's hidden header (or in the signature-verifying gateway's secret store).
- **Coexisting executors.** See [Use case 5](use-cases.md#use-case-5---several-executors-behind-one-marketplace).
