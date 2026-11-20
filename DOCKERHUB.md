# ODM Platform Marketplace Executor for Jira

The Jira executor is a marketplace executor adapter of [Open Data Mesh Platform](https://github.com/opendatamesh-initiative) V2. The [Marketplace Server](https://hub.docker.com/r/opendatamesh/odm-platform-pp-marketplace) forwards each access request (subscribe or unsubscribe) to it; the executor opens a Jira issue, a Jira Service Management request or a call to your own Jira Automation rule, and reports `PENDING` with the issue link. When the issue is completed in Jira, a callback tells the executor, which maps the Jira status and resolution to `GRANTED`, `DENIED`, `REVOKED` or `ERROR` and sends the result back to the Marketplace. It is stateless (no database) and ships as a Quarkus native executable that starts in well under a second, so it can scale to zero.

- **Source code:** [opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira](https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira)
- **Documentation:** [README](https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira/blob/main/README.md)
- **Issues:** [GitHub issues](https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira/issues)

## Tags

Each [GitHub release](https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira/releases) publishes one image tagged with its version, without the `v` prefix:

```bash
docker pull opendatamesh/odm-platform-adapter-marketplace-executor-jira:<version>
```

There is no `latest` tag: always pin a version. The same image is also published to `ghcr.io/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira`.

## Requirements

| Dependency | Required | Notes |
| --- | --- | --- |
| [Marketplace Server](https://hub.docker.com/r/opendatamesh/odm-platform-pp-marketplace) | Yes | Lists the executor in `odm.utility-plane.marketplace-executors` and receives its results. |
| Jira Cloud or Jira Data Center 8.14+ | Yes | A correlation text custom field and an identity the executor logs in with. See [Jira setup](https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira/blob/main/docs/reference/jira-setup.md). |
| Authenticating gateway | Yes, to receive completions | The executor's endpoints have no authentication of their own: Jira calls them through a gateway, such as blindata-agent. See [Securing the notification endpoints](https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira/blob/main/docs/reference/jira-setup.md#8-securing-the-notification-endpoints). |

## Quick start

The issue profiles (`jira.profiles.subscribe` and `jira.profiles.unsubscribe`) are required and are not packaged in the image: without them the executor doesn't start. Put them in a YAML file, for example `application.yml`, with a profile like this one, which opens a `Task` in the `ACC` project:

```yaml
jira:
  profiles:
    subscribe:
      type: ISSUE
      project: ACC
      issue-type: Task
      fields:
        summary: "Grant access to {request.provider.dataProductFqn} for {request.consumer.identifier}"
      outcome-rules:                 # first match wins
        - when:
            statusCategory: "done"
            resolution: '(?i)(Done|Fixed|Resolved|Granted|Revoked)'
          outcome: FULFILLED
        - when:
            statusCategory: "done"
            resolution: "(?i)(Won't Do|Declined|Rejected)"
          outcome: REFUSED
        - when:
            statusCategory: "done"
          outcome: FAILED
    unsubscribe:
      type: ISSUE
      project: ACC
      issue-type: Task
      fields:
        summary: "Revoke access to {request.provider.dataProductFqn} for {request.consumer.identifier}"
      outcome-rules:
        - when:
            statusCategory: "done"
            resolution: '(?i)(Done|Fixed|Resolved|Granted|Revoked)'
          outcome: FULFILLED
        - when:
            statusCategory: "done"
          outcome: FAILED
```

Then start the executor on Jira Cloud with a service account (OAuth 2.0 client credentials), with the file mounted where the executor reads it:

```bash
docker run --name odm-executor-jira -p 9019:9019 \
  -e JIRA_SITE_URL=https://example.atlassian.net \
  -e JIRA_API_URL=https://api.atlassian.com/ex/jira/<cloudId> \
  -e JIRA_CLIENT_ID=<client id> \
  -e JIRA_CLIENT_SECRET=change-me \
  -e JIRA_CORRELATION_FIELD=customfield_10050 \
  -e ODM_PRODUCT_PLANE_MARKETPLACE_SERVICE_ADDRESS=http://marketplace.example.com:8080 \
  -v "$(pwd)/application.yml:/work/config/application.yml:ro" \
  opendatamesh/odm-platform-adapter-marketplace-executor-jira:<version>
```

For an API token use `-e JIRA_AUTH_TYPE=API_TOKEN -e JIRA_API_TOKEN_EMAIL=... -e JIRA_API_TOKEN=...`; on Data Center use `-e JIRA_DEPLOYMENT=DATA_CENTER -e JIRA_AUTH_TYPE=PERSONAL_ACCESS_TOKEN -e JIRA_PAT=...`. The executor validates its configuration on startup and, if it's invalid, exits with a numbered list of problems (read it with `docker logs`).

When it's up, the API is documented at `http://localhost:9019/swagger-ui` (OpenAPI at `/api-docs`). It exposes:

- `POST /api/v1/up/marketplace-executor/requests`, called by the Marketplace Server.
- `POST /api/v1/up/marketplace-executor/webhooks/jira/issue-events` (Jira Automation rule) and `POST /api/v1/up/marketplace-executor/webhooks/jira/native-events` (Jira native webhook), called by Jira through the gateway.

Register the executor in the Marketplace Server with its internal address, for example `{ "name": "jira", "address": "http://executor-jira.example.com:9019", "active": true }`. Keep it reachable only by the Marketplace Server and by the gateway.

## Environment variables

### Image

The image defines no environment variables of its own. Every setting is passed as an environment variable or in the mounted configuration file: an environment variable wins over the file.

### Application

Every property can be set in the configuration file or as an environment variable, using the Quarkus mapping (upper case, dots and dashes become underscores: `jira.rate-limit.max-attempts` becomes `JIRA_RATE_LIMIT_MAX_ATTEMPTS`). Some properties also have a short variable name, shown in brackets.

| Property | Description | Default |
| --- | --- | --- |
| `jira.deployment` (`JIRA_DEPLOYMENT`) | `CLOUD` or `DATA_CENTER`. | `CLOUD` |
| `jira.site-url` (`JIRA_SITE_URL`) | Jira site URL, used for issue links and, on Data Center, as the API base. Must be `https` (`http` only for `http://localhost`). | none, required |
| `jira.api-url` (`JIRA_API_URL`) | API base URL. On Cloud with OAuth it must be `https://api.atlassian.com/ex/jira/<cloudId>`. | the site URL |
| `jira.api-version` (`JIRA_API_VERSION`) | Jira REST API version, `2` or `3` (`3` on Cloud only). | `2` |
| `jira.auth.type` (`JIRA_AUTH_TYPE`) | `OAUTH2_CLIENT_CREDENTIALS`, `API_TOKEN` (Cloud only) or `PERSONAL_ACCESS_TOKEN` (Data Center only). On Data Center set it explicitly. | `OAUTH2_CLIENT_CREDENTIALS` |
| `jira.auth.oauth2.client-id` (`JIRA_CLIENT_ID`) | OAuth client id. Required with OAuth. | none |
| `jira.auth.oauth2.client-secret` (`JIRA_CLIENT_SECRET`) | OAuth client secret. Required with OAuth. | none |
| `jira.auth.oauth2.token-url` | OAuth token endpoint. | Cloud: `https://auth.atlassian.com/oauth/token`; Data Center: `<site-url>/rest/oauth2/latest/token` |
| `jira.auth.oauth2.scope` | OAuth scope, omitted from the token request when not set. | none |
| `jira.auth.api-token.email` (`JIRA_API_TOKEN_EMAIL`) | Account email. Required with `API_TOKEN`. | none |
| `jira.auth.api-token.token` (`JIRA_API_TOKEN`) | Cloud API token. Required with `API_TOKEN`. | none |
| `jira.auth.pat.token` (`JIRA_PAT`) | Data Center Personal Access Token. Required with `PERSONAL_ACCESS_TOKEN`. | none |
| `jira.correlation.field` (`JIRA_CORRELATION_FIELD`) | Text custom field that stores the marketplace request id, as `customfield_<digits>`. | none, required |
| `jira.correlation.label` | Also add the label `bdmp-<request id>` to the issue. | `false` |
| `jira.rate-limit.max-attempts` | Attempts to create an issue when Jira answers HTTP `429`. | `3` |
| `jira.rate-limit.backoff` | First wait after a `429`, doubled after every attempt. | `2s` |
| `jira.filter.*` | Regular expressions that select the requests this executor handles (operations, data products, ports, consumers, requesters, properties). Requests that don't match are refused with `409`. | handles every request |
| `jira.profiles.subscribe` / `jira.profiles.unsubscribe` | Issue profile of each operation: `type` (`ISSUE`, `SERVICE_DESK_REQUEST` or `AUTOMATION_WEBHOOK`), target, templated fields and outcome rules. Set them in the configuration file. | none, required |
| `jira.messages.*` | Templates of the messages sent to the Marketplace with each result. | built-in |
| `jira.messages.closing-user.include` (`JIRA_MESSAGES_INCLUDE_CLOSING_USER`) | `false` keeps the Jira user who completed the issue out of the messages. | `true` |
| `odm.product-plane.marketplace-service.address` | Base URL of the Marketplace Server, where results are sent. | `http://localhost:8085` |
| `odm.product-plane.marketplace-service.result-delivery.max-attempts` | Attempts to send a result to the Marketplace. | `5` |
| `odm.product-plane.marketplace-service.result-delivery.backoff` | Wait between result delivery attempts. | `1s` |
| `quarkus.config.locations` (`QUARKUS_CONFIG_LOCATIONS`) | Path of the configuration file, when it isn't mounted at `/work/config/application.yml`. | none |
| `quarkus.http.port` | HTTP port. | `9019` |

The full list, with the profile and filter keys and the startup checks, is in [Configuration reference](https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira/blob/main/docs/reference/configuration-reference.md). Keep secrets in environment variables or mounted secrets, never in a committed file.

## Ports and volumes

- **Port:** `9019` (HTTP), or the value of `quarkus.http.port`.
- **Volumes:** none. Mount the configuration file at `/work/config/application.yml` (the working directory is `/work`), or anywhere with `QUARKUS_CONFIG_LOCATIONS`. The process runs as user `1001`, so mounted files must be readable by it.

## Learn more

- [Scenarios](https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira/blob/main/README.md#1-pick-your-scenario): [Jira issue](https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira/blob/main/docs/scenarios/issue.md), [Service Management request](https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira/blob/main/docs/scenarios/service-desk.md), [Automation rule](https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira/blob/main/docs/scenarios/automation-webhook.md)
- [Deployment](https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira/blob/main/docs/reference/deployment.md), with quickstarts for Cloud Run, Knative, KEDA and Azure Container Apps
- [Concepts](https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira/blob/main/docs/reference/concepts.md) and [Templates](https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira/blob/main/docs/reference/templates.md)
- [API reference](https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira/blob/main/docs/reference/api-reference.md) and [Troubleshooting](https://github.com/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira/blob/main/docs/reference/troubleshooting.md)

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
