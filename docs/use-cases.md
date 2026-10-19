# Use cases

[Back to README](../README.md)

## Overview

This page shows what the executor can be used for and exactly how to configure it for each case. Each use case has the same shape: what it achieves, the Jira objects it needs, the executor configuration, how completion is detected, and an example run.

| # | Use case | Interface type |
|:--|:--|:--|
| 1 | [Jira Software issues](#use-case-1---jira-software-issues-issue-api) | `ISSUE` |
| 2 | [Jira Service Management requests](#use-case-2---jira-service-management-requests) | `SERVICE_DESK_REQUEST` |
| 3 | [Jira Automation incoming webhook](#use-case-3---jira-automation-incoming-webhook) | `AUTOMATION_WEBHOOK` |
| 4 | [Jira Data Center](#use-case-4---jira-data-center) | any, with Data Center authentication |
| 5 | [Several executors behind one marketplace](#use-case-5---several-executors-behind-one-marketplace) | request filter |

Subscribe and unsubscribe have separate profiles, so you can mix interface types (for example `ISSUE` for subscribe and `SERVICE_DESK_REQUEST` for unsubscribe).

Related: [Jira setup](jira-setup.md) · [Configuration reference](configuration-reference.md) · [Templates](templates.md) · [Concepts](concepts.md)

## Shared assumptions

The examples use:

- site `https://acme.atlassian.net` on **Cloud**, a service account with OAuth client credentials from the environment, API URL `https://api.atlassian.com/ex/jira/<cloudId>`, REST v2, and a filter that handles the `urn:dp:finance:*` data products;
- the correlation field `customfield_10050` (named *Marketplace request id*);
- the global message defaults from [Templates](templates.md#messages) (omit the `messages` block to keep them);
- the completion Automation rule body from [Jira setup, channel A](jira-setup.md#8-completion-channel-a---jira-automation-rule-recommended).

Where to put the YAML: an external `config/application.yml`, see [Configuration reference](configuration-reference.md#where-to-put-the-configuration).

The example request used in the runs:

```json
{
  "v": "1",
  "operation": "MARKETPLACE_SUBSCRIBE",
  "request": {
    "name": "Sales data for Marketing",
    "identifier": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "provider": {
      "dataProductFqn": "urn:dp:finance:sales",
      "dataProductPortsFqn": ["urn:dp:finance:sales:port:orders", "urn:dp:finance:sales:port:customers"]
    },
    "consumer": { "type": "user", "identifier": "jane.doe@acme.com" },
    "requester": { "type": "user", "identifier": "jane.doe@acme.com" },
    "properties": {},
    "startDate": "2026-11-01T00:00:00Z",
    "endDate": "2027-11-01T00:00:00Z"
  }
}
```

## Use case 1 - Jira Software issues (Issue API)

**What it achieves.** The simplest option. Every access request becomes an issue (for example a `Task`) in a project. A team works the issue; closing it with a resolution decides the outcome.

**Jira objects.** The integration identity and permissions ([Jira setup](jira-setup.md)), a project `ACC` with the issue type `Task` whose create screen carries the correlation custom field. Nothing else.

**Executor configuration**

```yaml
jira:
  deployment: CLOUD
  site-url: ${JIRA_SITE_URL}
  api-url: ${JIRA_API_URL}
  api-version: 2
  auth:
    type: OAUTH2_CLIENT_CREDENTIALS
    oauth2:
      client-id: ${JIRA_CLIENT_ID}
      client-secret: ${JIRA_CLIENT_SECRET}
  correlation:
    field: customfield_10050
  filter:
    provider-data-product-fqns:
      - 'urn:dp:finance:.*'
  profiles:
    subscribe:
      type: ISSUE
      project: ACC
      issue-type: Task
      fields:
        summary: "Grant access to {request.provider.dataProductFqn} for {request.consumer.identifier}"
        description: |-
          Marketplace request: {request.identifier}
          Data product: {request.provider.dataProductFqn}
          Ports: {#each request.provider.dataProductPortsFqn}{it}{#if it_hasNext}, {/if}{/each}
          Consumer: {request.consumer.type} {request.consumer.identifier}
          Requested by: {request.requester.identifier}
          Start date: {request.startDate}
          End date: {request.endDate}
      outcome-rules:
        - when:
            statusCategory: "done"
            resolution: '(?i)(Done|Fixed|Resolved|Granted|Revoked)'
          outcome: FULFILLED
        - when:
            statusCategory: "done"
            resolution: "(?i)(Won't Do|Won't Fix|Declined|Rejected|Cannot Reproduce|Duplicate)"
          outcome: REFUSED
        - when:            # fallback: any other completed issue, including an unset resolution
            statusCategory: "done"
          outcome: FAILED
    unsubscribe:
      type: ISSUE
      project: ACC
      issue-type: Task
      fields:
        summary: "Revoke access to {request.provider.dataProductFqn} for {request.consumer.identifier}"
        description: |-
          Marketplace request: {request.identifier}
          Data product: {request.provider.dataProductFqn}
          Ports: {#each request.provider.dataProductPortsFqn}{it}{#if it_hasNext}, {/if}{/each}
          Consumer: {request.consumer.type} {request.consumer.identifier}
          Requested by: {request.requester.identifier}
      outcome-rules:
        - when:
            statusCategory: "done"
            resolution: '(?i)(Done|Fixed|Resolved|Granted|Revoked)'
          outcome: FULFILLED
        - when:
            statusCategory: "done"
            resolution: "(?i)(Won't Do|Won't Fix|Declined|Rejected|Cannot Reproduce|Duplicate)"
          outcome: REFUSED
        - when:
            statusCategory: "done"
          outcome: FAILED
```

Optional additions under a profile:

- `set-reporter: true`: the reporter becomes the marketplace requester (needs *Modify Reporter* and the user search permission).
- `object-fields: {priority: {name: "High"}}` and `list-fields: {labels: ["marketplace"]}`.
- `jira.correlation.label: true`: also add the label `bdmp-<uuid>` (handy for JQL searches).

**Completion.** An Automation rule *Issue transitioned* in project `ACC` with the condition *status category = Done* and the JQL condition `"Marketplace request id" is not EMPTY`, plus the *Send web request* action ([channel A](jira-setup.md#8-completion-channel-a---jira-automation-rule-recommended)). Native-webhook variant: a `jira:issue_updated` webhook with the JQL `project = ACC AND "Marketplace request id" is not EMPTY`, through the [gateway](jira-setup.md#9-completion-channel-b---native-webhook), to `.../webhooks/jira/native-events`. No profile changes are needed: the outcome rules read the same normalised fields.

**Example run**

1. The Marketplace Service posts the example request to `POST /api/v1/up/marketplace-executor/requests` and gets `202`.
2. The executor calls `POST https://api.atlassian.com/ex/jira/<cloudId>/rest/api/2/issue` with:

```json
{
  "fields": {
    "summary": "Grant access to urn:dp:finance:sales for jane.doe@acme.com",
    "description": "Marketplace request: 7c9e6679-7425-40de-944b-e07fc1f90ae7\nData product: urn:dp:finance:sales\nPorts: urn:dp:finance:sales:port:orders, urn:dp:finance:sales:port:customers\nConsumer: user jane.doe@acme.com\nRequested by: jane.doe@acme.com\nStart date: 2026-11-01T00:00:00Z\nEnd date: 2027-11-01T00:00:00Z",
    "project": { "key": "ACC" },
    "issuetype": { "name": "Task" },
    "customfield_10050": "7c9e6679-7425-40de-944b-e07fc1f90ae7"
  }
}
```

3. Jira answers `{"id": "10042", "key": "ACC-42", ...}`. The executor posts to `/requests/7c9e6679-.../results`:

```json
{
  "status": "PENDING",
  "message": "Jira issue ACC-42 opened for MARKETPLACE_SUBSCRIBE of urn:dp:finance:sales (ports: urn:dp:finance:sales:port:orders, urn:dp:finance:sales:port:customers). Awaiting fulfilment: https://acme.atlassian.net/browse/ACC-42",
  "provider": {
    "dataProductFqn": "urn:dp:finance:sales",
    "dataProductPortsFqn": ["urn:dp:finance:sales:port:orders", "urn:dp:finance:sales:port:customers"]
  },
  "createdAt": "2026-10-16T14:02:11Z"
}
```

4. A fulfiller transitions the issue to *Done* with resolution *Done* and comments "Access granted to both ports through group GRP-SALES-READ". The Automation rule sends the event through the gateway. The executor answers `200 {"outcome": "REPORTED", "status": "GRANTED"}` and posts:

```json
{
  "status": "GRANTED",
  "message": "GRANTED through Jira issue ACC-42 (https://acme.atlassian.net/browse/ACC-42) by Mario Rossi: Done / Done. Comment: Access granted to both ports through group GRP-SALES-READ",
  "provider": {
    "dataProductFqn": "urn:dp:finance:sales",
    "dataProductPortsFqn": ["urn:dp:finance:sales:port:orders", "urn:dp:finance:sales:port:customers"]
  },
  "createdAt": "2026-10-16T15:40:03Z"
}
```

With the native channel the event is an abridged `jira:issue_updated` such as:

```json
{
  "webhookEvent": "jira:issue_updated",
  "user": { "displayName": "Mario Rossi" },
  "issue": {
    "key": "ACC-42",
    "id": "10042",
    "fields": {
      "project": { "key": "ACC" },
      "issuetype": { "name": "Task" },
      "status": { "name": "Done", "statusCategory": { "key": "done" } },
      "resolution": { "name": "Done" },
      "customfield_10050": "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    }
  },
  "changelog": { "items": [ { "field": "status", "fieldId": "status", "fromString": "In Progress", "toString": "Done" } ] }
}
```

The payload has no comment list, so the executor reads the last comment from Jira before posting the result. If the issue is resolved as *Won't Do* the same event produces `DENIED` (`Request refused on Jira: issue ACC-42 (...) by ...`). For a `MARKETPLACE_UNSUBSCRIBE` the same completion produces `REVOKED`, and a refusal produces an `ERROR` with the same "refused on Jira" message.

## Use case 2 - Jira Service Management requests

**What it achieves.** Requests are raised in a **JSM service project**, **on behalf of the marketplace requester**, so they appear in the customer portal like any other request and can use the service project's approvals.

**Jira objects**

- A JSM service project and request types for subscribe and unsubscribe. Find their ids with `GET /rest/servicedeskapi/servicedesk` (the `id` of your project) and `GET /rest/servicedeskapi/servicedesk/{serviceDeskId}/requesttype` (the `id` of each request type), or in the portal URL (`/servicedesk/customer/portal/{serviceDeskId}/group/.../create/{requestTypeId}`).
- The correlation field on the request type (Project settings, Request types, fields), or set `correlation-via-edit: true` and give the integration identity *Edit issues*. With the follow-up edit, a failure leaves the request created but uncorrelated: the executor then reports an `ERROR` that names the request, and the request will **not** be completed automatically.
- The requester must be a **customer** of the service project, and the integration identity a **service desk agent**: a `400` or `403` from the create call is reported with this hint. The requester is looked up by email among the Jira users and the executor never falls back to raising the request as itself.
- Add the request types to a portal group so customers see their requests.
- A workflow whose *Declined* status is how an approval is refused: the first rule below turns it into `REFUSED`. Adapt the status names to your workflow.

**Executor configuration**

```yaml
jira:
  deployment: CLOUD
  site-url: ${JIRA_SITE_URL}
  api-url: ${JIRA_API_URL}
  auth:
    type: OAUTH2_CLIENT_CREDENTIALS
    oauth2:
      client-id: ${JIRA_CLIENT_ID}
      client-secret: ${JIRA_CLIENT_SECRET}
  correlation:
    field: customfield_10050
  filter:
    provider-data-product-fqns:
      - 'urn:dp:finance:.*'
  profiles:
    subscribe:
      type: SERVICE_DESK_REQUEST
      service-desk-id: "1"
      request-type-id: "10"
      correlation-via-edit: false        # true when the field is not on the request type
      fields:
        summary: "Grant access to {request.provider.dataProductFqn} for {request.consumer.identifier}"
        description: |-
          Marketplace request: {request.identifier}
          Data product: {request.provider.dataProductFqn}
          Ports: {#each request.provider.dataProductPortsFqn}{it}{#if it_hasNext}, {/if}{/each}
          Consumer: {request.consumer.type} {request.consumer.identifier}
      outcome-rules:
        - when:                          # approval declined in the workflow
            status: "(?i)Declined"
          outcome: REFUSED
        - when:
            statusCategory: "done"
            resolution: '(?i)(Done|Fixed|Resolved|Granted|Revoked)'
          outcome: FULFILLED
        - when:
            statusCategory: "done"
            resolution: "(?i)(Won't Do|Won't Fix|Declined|Rejected|Cannot Reproduce|Duplicate)"
          outcome: REFUSED
        - when:
            statusCategory: "done"
          outcome: FAILED
    unsubscribe:
      type: SERVICE_DESK_REQUEST
      service-desk-id: "1"
      request-type-id: "11"
      fields:
        summary: "Revoke access to {request.provider.dataProductFqn} for {request.consumer.identifier}"
        description: "Marketplace request: {request.identifier}. Data product: {request.provider.dataProductFqn}."
      outcome-rules:
        - when:
            status: "(?i)Declined"
          outcome: REFUSED
        - when:
            statusCategory: "done"
            resolution: '(?i)(Done|Fixed|Resolved|Granted|Revoked)'
          outcome: FULFILLED
        - when:
            statusCategory: "done"
          outcome: FAILED
```

**Completion.** An Automation rule *Issue transitioned* on the service project, condition *status category = Done* **or** *status = Declined* (so declined approvals are reported too), JQL condition `"Marketplace request id" is not EMPTY`, and the *Send web request* action from [channel A](jira-setup.md#8-completion-channel-a---jira-automation-rule-recommended). Native-webhook variant: a `jira:issue_updated` webhook with the JQL `project = SD AND "Marketplace request id" is not EMPTY`, through the [gateway](jira-setup.md#9-completion-channel-b---native-webhook).

**Example run**

1. The Marketplace Service posts a `MARKETPLACE_UNSUBSCRIBE` request (requester `jane.doe@acme.com`) and gets `202`.
2. The executor resolves the requester (`GET /rest/api/2/user/search?query=jane.doe%40acme.com&maxResults=10` returns the `accountId` `5b10ac8d82e05b22cc7d4ef5`) and calls `POST https://api.atlassian.com/ex/jira/<cloudId>/rest/servicedeskapi/request`:

```json
{
  "serviceDeskId": "1",
  "requestTypeId": "11",
  "requestFieldValues": {
    "summary": "Revoke access to urn:dp:finance:sales for jane.doe@acme.com",
    "description": "Marketplace request: 7c9e6679-7425-40de-944b-e07fc1f90ae7. Data product: urn:dp:finance:sales.",
    "customfield_10050": "7c9e6679-7425-40de-944b-e07fc1f90ae7"
  },
  "raiseOnBehalfOf": "5b10ac8d82e05b22cc7d4ef5",
  "isAdfRequest": false
}
```

3. Jira answers `{"issueId": "10077", "issueKey": "SD-7", ...}` and the executor posts a `PENDING` result whose message mentions `SD-7` and its browse link.
4. The agent approves and the request is resolved as *Done*. The Automation rule sends the event and the executor posts `REVOKED`. If the approval is declined the status is `Declined`, the first rule matches, and the result is an `ERROR` whose message states `Request refused on Jira: issue SD-7 (...)`.

## Use case 3 - Jira Automation incoming webhook

**What it achieves.** The executor does not call the Jira API: it calls an **Automation rule** with an *Incoming webhook* trigger, and the rule creates the issue. Use it when the issue must be built by your own Automation logic (routing, extra fields, sub-tasks). The executor holds **only the webhook URL and its secret token** for creation, so no Jira call is made at creation time. The Jira credentials are still required: they are used to read the last comment.

**Executor configuration**

```yaml
jira:
  deployment: CLOUD
  site-url: ${JIRA_SITE_URL}
  api-url: ${JIRA_API_URL}
  auth:
    type: OAUTH2_CLIENT_CREDENTIALS      # still required: used to read the last comment
    oauth2:
      client-id: ${JIRA_CLIENT_ID}
      client-secret: ${JIRA_CLIENT_SECRET}
  correlation:
    field: customfield_10050
  filter:
    provider-data-product-fqns:
      - 'urn:dp:finance:.*'
  profiles:
    subscribe:
      type: AUTOMATION_WEBHOOK
      webhook-url: ${JIRA_AUTOMATION_SUBSCRIBE_URL}
      webhook-token: ${JIRA_AUTOMATION_SUBSCRIBE_TOKEN}
      fields:
        summary: "Grant access to {request.provider.dataProductFqn} for {request.consumer.identifier}"
        description: "Data product {request.provider.dataProductFqn}, ports {#each request.provider.dataProductPortsFqn}{it}{#if it_hasNext}, {/if}{/each}"
      outcome-rules:
        - when:
            statusCategory: "done"
            resolution: '(?i)(Done|Fixed|Resolved|Granted|Revoked)'
          outcome: FULFILLED
        - when:
            statusCategory: "done"
            resolution: "(?i)(Won't Do|Won't Fix|Declined|Rejected|Cannot Reproduce|Duplicate)"
          outcome: REFUSED
        - when:
            statusCategory: "done"
          outcome: FAILED
    unsubscribe:
      type: AUTOMATION_WEBHOOK
      webhook-url: ${JIRA_AUTOMATION_UNSUBSCRIBE_URL}
      webhook-token: ${JIRA_AUTOMATION_UNSUBSCRIBE_TOKEN}
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

The webhook URLs and tokens are secrets: provide them only through the environment or mounted secrets. The executor never logs them and never includes the URL in an error message.

**Jira objects: the creation rule.** For each operation create an Automation rule:

1. **Trigger**: *Incoming webhook*. Copy the generated webhook URL and secret into `webhook-url` and `webhook-token`. The executor sends the secret in the `X-Automation-Webhook-Token` header.
2. **Action**: *Create issue* in the project, with the summary `{{webhookData.fields.summary}}` and the description `{{webhookData.fields.description}}`. The webhook body is described in [API reference](api-reference.md#automation-incoming-webhook-outbound).
3. **Mandatory**: in the same *Create issue* action, **set the correlation custom field to `{{webhookData.correlationId}}`**. Without it the issue has no UUID, no notification can be correlated, and the request stays `PENDING` forever.

The smart-value paths follow the webhook body; check them in the rule audit log. The `request` object carries the full request data, for example `{{webhookData.request.provider.dataProductFqn}}`.

**Completion.** The Automation rule or native webhook of [channel A or B](jira-setup.md#8-completion-channel-a---jira-automation-rule-recommended), scoped to the project where the rule creates the issues. The executor never learns the issue key, so the `PENDING` message says the request was submitted to the Jira Automation rule, and the final message carries the key sent by the completion notification.

**Example run**

1. The Marketplace Service posts the example request and gets `202`.
2. The executor calls the Automation webhook URL (with the token header and no Jira credentials). Automation answers `200`.
3. The executor posts `PENDING` with the message `Request submitted to the Jira Automation rule for MARKETPLACE_SUBSCRIBE of urn:dp:finance:sales (ports: ...). Awaiting fulfilment`.
4. The rule created issue `ACC-43` with the correlation field set. When it is completed, the completion notification arrives and the executor posts `GRANTED through Jira issue ACC-43 (https://acme.atlassian.net/browse/ACC-43) ...`.

## Use case 4 - Jira Data Center

**What it achieves.** The same flows on a self-hosted Jira Data Center (8.14 or later). Differences from Cloud:

- The site URL is also the API base, so `api-url` is not needed.
- The default authentication is a **Personal Access Token**; note that the application default for `jira.auth.type` is `OAUTH2_CLIENT_CREDENTIALS`, so set `PERSONAL_ACCESS_TOKEN` explicitly. A Data Center 11.1+ service account with OAuth client credentials is the alternative (token URL `{site-url}/rest/oauth2/latest/token`).
- Only REST v2. `api-version: 3` stops the executor at startup.
- Requester lookup uses `GET /rest/api/2/user/search?username=<email>`.

**Executor configuration (Issue API)**

```yaml
jira:
  deployment: DATA_CENTER
  site-url: https://jira.acme.internal
  api-version: 2
  auth:
    type: PERSONAL_ACCESS_TOKEN
    pat:
      token: ${JIRA_PAT}
  correlation:
    field: customfield_10050
  profiles:
    subscribe:
      type: ISSUE
      project: ACC
      issue-type: Task
      fields:
        summary: "Grant access to {request.provider.dataProductFqn}"
      outcome-rules:
        - when: { statusCategory: "done", resolution: '(?i)(Done|Fixed)' }
          outcome: FULFILLED
        - when: { statusCategory: "done" }
          outcome: REFUSED
    unsubscribe:
      type: ISSUE
      project: ACC
      issue-type: Task
      fields:
        summary: "Revoke access to {request.provider.dataProductFqn}"
      outcome-rules:
        - when: { statusCategory: "done", resolution: '(?i)(Done|Fixed)' }
          outcome: FULFILLED
        - when: { statusCategory: "done" }
          outcome: FAILED
```

Run it with `JIRA_AUTH_TYPE=PERSONAL_ACCESS_TOKEN` (or set `jira.auth.type` as above), `JIRA_SITE_URL` and `JIRA_PAT`. Setup specifics (PAT creation, webhooks) are in [Jira setup](jira-setup.md#data-center).

## Use case 5 - Several executors behind one marketplace

**What it achieves.** Different data products are handled in different Jira projects or even different Jira instances. Each executor accepts only its own requests.

Several executors can run behind one Marketplace Service, but each request must be accepted by **exactly one** of them. Results carry no executor identity, so two executors accepting the same request would suppress each other's final result (first final status wins). Give each executor an exclusive filter, for example:

```yaml
# executor A
jira:
  filter:
    provider-data-product-fqns: ['urn:dp:finance:.*']
```

```yaml
# executor B
jira:
  filter:
    provider-data-product-fqns: ['(?!urn:dp:finance:).*']
```

A request that no executor accepts is rejected by the Marketplace Service at submission with "No executor was able to handle the request". Every non-matching executor answers `409`, which the Marketplace Service may log: that is expected. Give each executor its own route or key in your gateway configuration (for example `marketplace-executor-jira-finance`), see [Provisioning](provisioning.md#securing-the-notification-endpoints). Filter semantics are in [Configuration reference](configuration-reference.md#request-filter).
