# Examples

[Back to README](../../README.md)

## Overview

The executor always does the same thing: a marketplace request becomes a Jira issue, and the way the issue is completed goes back to the marketplace. What changes from company to company is **how the issue is created**. This page shows, for each [scenario](../../README.md#1-pick-your-scenario), what travels on the wire, plus the scenarios that apply to all of them: Jira Data Center and several executors behind one marketplace.

## Contents

- [What every scenario has in common](#what-every-scenario-has-in-common)
- [Shared assumptions](#shared-assumptions)
- [Jira issue](#jira-issue)
- [Service Management request](#service-management-request)
- [Automation rule](#automation-rule)
- [Jira Data Center](#jira-data-center)
- [Several executors behind one marketplace](#several-executors-behind-one-marketplace)

Subscribe and unsubscribe have separate profiles, so you can mix interface types (for example `ISSUE` for subscribe and `SERVICE_DESK_REQUEST` for unsubscribe).

## What every scenario has in common

1. **Jira objects**: an integration identity and its permissions, the correlation custom field, plus the objects of the interface ([Jira setup](jira-setup.md)).
2. **Executor configuration**: connection and authentication, a request filter, and one profile per operation with `type`, the interface settings, `fields` and `outcome-rules` ([Configuration reference](configuration-reference.md)).
3. **Deployment**: the executor on a platform that keeps the CPU after the response and is reachable only internally ([which platforms fit](deployment.md#which-platforms-fit)).
4. **A gateway** that authenticates Jira's callback and forwards only the webhook paths ([Securing the notification endpoints](jira-setup.md#8-securing-the-notification-endpoints)).
5. **A completion Automation rule** (or a native webhook) that calls the executor when the issue is completed, and **outcome rules** that turn the status and resolution into `FULFILLED`, `REFUSED` or `FAILED`.
6. **Registration** of the executor in the Marketplace Service.

## Shared assumptions

The examples use:

- site `https://acme.atlassian.net` on **Cloud**, a service account with OAuth client credentials from the environment, API URL `https://api.atlassian.com/ex/jira/<cloudId>`, REST v2, and a filter that handles the `urn:dp:finance:*` data products;
- the correlation field `customfield_10050` (named *Marketplace request id*);
- the global message defaults from [Templates](templates.md#messages) (omit the `messages` block to keep them);
- the completion Automation rule body from [Jira setup, channel A](jira-setup.md#9-completion-channel-a---jira-automation-rule-recommended);
- the executor deployed as `jira-executor`.

Where to put the YAML: an external `config/application.yml`, see [Configuration reference](configuration-reference.md#where-to-put-the-configuration).

### The example request

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
    "consumer": { "type": "USER", "identifier": "jane.doe@acme.com" },
    "requester": { "type": "USER", "identifier": "jane.doe@acme.com" },
    "properties": {},
    "startDate": "2026-11-01T00:00:00Z",
    "endDate": "2027-11-01T00:00:00Z"
  }
}
```

## Jira issue

Setup: [Scenario: Jira issue](../scenarios/issue.md).

### Example run

1. The Marketplace Service posts the [example request](#the-example-request) to `POST /api/v1/up/marketplace-executor/requests` and gets `202`.
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

### Optional profile additions

Under a profile:

- `set-reporter: true`: the reporter becomes the marketplace requester (needs *Modify Reporter* and the user search permission).
- `object-fields: {priority: {name: "High"}}` and `list-fields: {labels: ["marketplace"]}`.
- `jira.correlation.label: true`: also add the label `bdmp-<uuid>` (handy for JQL searches).

## Service Management request

Setup: [Scenario: Service Management request](../scenarios/service-desk.md).

### Example run

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

### Notes

- The requester is looked up by email among the Jira users ([Requester resolution](concepts.md#requester-resolution-on-behalf-of)); the executor never raises the request as itself.
- The `Declined` rule comes **first**, so a declined approval is reported even if the status is in the *done* category.
- With `correlation-via-edit: true` the executor sets the correlation field with a follow-up edit. A failed edit leaves the request created but uncorrelated; the executor reports an `ERROR` that names it and that request is **not** completed automatically.

## Automation rule

Setup: [Scenario: your own Automation rule](../scenarios/automation-webhook.md).

The executor does not call the Jira API to create the issue: it calls a **Jira Automation rule** with an *Incoming webhook* trigger, and the rule creates the issue. For creation the executor holds **only the webhook URL and its secret token**; the Jira credentials are still required, to read the last comment when the issue is completed. The executor never learns the key of the issue the rule creates, so the `PENDING` message says the request was submitted to the Automation rule, and the final message carries the key sent by the completion notification.

### Example run

1. The Marketplace Service posts the [example request](#the-example-request) and gets `202`.
2. The executor calls the Automation webhook URL (with the token header and no Jira credentials). Automation answers `200`.
3. The executor posts `PENDING` with the message `Request submitted to the Jira Automation rule for MARKETPLACE_SUBSCRIBE of urn:dp:finance:sales (ports: ...). Awaiting fulfilment`.
4. The rule created issue `ACC-43` with the correlation field set. When it is completed, the completion notification arrives and the executor posts `GRANTED through Jira issue ACC-43 (https://acme.atlassian.net/browse/ACC-43) ...`.

The full body the executor sends to the rule is in the [API reference](api-reference.md#automation-incoming-webhook-outbound). `fields` reach the rule as `{{webhookData.fields.<name>}}`; `correlationId` is reserved and always sent. Incoming webhook triggers consume your plan's automation usage: check the limits for the expected volume.

## Jira Data Center

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

**Deployment.** Any [platform](deployment.md#which-platforms-fit) applies, with these differences:

- Data Center usually lives inside your network: the executor needs a **private network path** to the Data Center host. For example Direct VPC egress plus Cloud VPN or Interconnect on Cloud Run ([Egress](deploy-cloud-run.md#egress)), cluster egress on Kubernetes, a VNet-integrated environment with VPN or ExpressRoute on Azure Container Apps.
- Set `JIRA_DEPLOYMENT=DATA_CENTER`, `JIRA_AUTH_TYPE=PERSONAL_ACCESS_TOKEN` and `JIRA_SITE_URL`; no `JIRA_API_URL`.
- Store the token in the platform's secret store and expose it as `JIRA_PAT`, instead of the client id and secret.
- Data Center sends native webhooks and Automation calls from inside your network, so the gateway only needs to be reachable from there.

## Several executors behind one marketplace

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

A request that no executor accepts is rejected by the Marketplace Service at submission with "No executor was able to handle the request". Every non-matching executor answers `409`, which the Marketplace Service may log: that is expected. Give each executor its own route or key in your gateway configuration (for example `marketplace-executor-jira-finance`), see [Securing the notification endpoints](jira-setup.md#8-securing-the-notification-endpoints). Deploy each executor as its own service (for example `jira-executor-finance`) with its own profiles file, on any [supported platform](deployment.md). Filter semantics are in [Configuration reference](configuration-reference.md#request-filter).

---
[Back to README](../../README.md) | [Jira setup](jira-setup.md) | [Deployment](deployment.md)
