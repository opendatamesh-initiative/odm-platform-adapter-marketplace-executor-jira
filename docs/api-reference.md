# API reference

[Back to README](../README.md)

## Overview

The executor exposes three HTTP endpoints. One is called by the Marketplace Service to hand over a request; the other two are called by Jira (through an authenticating gateway, see [Securing the notification endpoints](provisioning.md#securing-the-notification-endpoints)) to say that an issue has changed. The executor in turn calls the Marketplace Service to report results.

Swagger UI is at `http://localhost:9019/swagger-ui` and the OpenAPI document at `http://localhost:9019/api-docs`.

Related: [Concepts](concepts.md) · [Jira setup](jira-setup.md) · [Configuration reference](configuration-reference.md)

## `POST /api/v1/up/marketplace-executor/requests`

Called by the Marketplace Service. Answers **202 Accepted** and works asynchronously. Other answers:

| Status | Meaning |
|:--|:--|
| `400` | Invalid request (missing fields, `startDate` after `endDate`) |
| `409` | `RequestNotHandledException`: the request does not match this executor's [request filter](configuration-reference.md#request-filter). Nothing is created on Jira and nothing is reported. The Marketplace Service counts it as "not handled by this executor" |
| `500` | Unexpected error |

Request:

```json
{
  "v": "1",
  "operation": "MARKETPLACE_SUBSCRIBE | MARKETPLACE_UNSUBSCRIBE",
  "request": {
    "name": "",
    "identifier": "marketplace request UUID",
    "provider": {
      "dataProductFqn": "",
      "dataProductPortsFqn": [""]
    },
    "consumer": {
      "type": "USER | TEAM | DATA_PRODUCT | APPLICATION",
      "identifier": "email | team id | fqn | application id"
    },
    "requester": {
      "type": "USER",
      "identifier": "email"
    },
    "properties": {},
    "startDate": "Date",
    "endDate": "Date"
  }
}
```

The consumer and requester types are the Marketplace Service enum values, in upper case. The Marketplace Service rejects any other value when it receives the executor's result (a lower-case `user` makes every result delivery fail with a `500`, so the request stays without a result). The executor itself compares the requester type ignoring case.

### Result sent to the Marketplace Service

The executor posts results to `{marketplace-service.address}/api/v1/pp/marketplace/requests/{identifier}/results`. The `identifier` is the Marketplace Service internal UUID, which is also the correlation id stored on the Jira issue. To decide whether a final result already exists it reads `GET /api/v1/pp/marketplace/executor-responses?accessRequestUuid={uuid}`, and it reads the request itself with `GET /api/v1/pp/marketplace/requests/{uuid}`.

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

## `POST /api/v1/up/marketplace-executor/webhooks/jira/issue-events`

The Jira Automation channel. This is a fixed contract: your Automation rule must send exactly this shape. `fields` is a free map of strings; the outcome rules and the message templates read from it.

```json
{
  "correlationId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "fields": {
    "key": "ACC-42",
    "id": "10042",
    "project": "ACC",
    "issueType": "Task",
    "status": "Done",
    "statusCategory": "done",
    "resolution": "Done",
    "actor": "Mario Rossi",
    "lastComment": "Access granted to both ports through group GRP-SALES-READ"
  }
}
```

| Field | Meaning | Automation smart value |
|:--|:--|:--|
| `correlationId` | The marketplace request UUID, read back from the correlation custom field | `{{issue.customfield_10050}}` (your field id) |
| `key` | Issue key, used for the final message and link | `{{issue.key}}` |
| `id` | Issue id | `{{issue.id}}` |
| `project` | Project key | `{{issue.project.key}}` |
| `issueType` | Issue type name | `{{issue.issueType.name}}` |
| `status` | Status name | `{{issue.status.name}}` |
| `statusCategory` | `new`, `indeterminate` or `done` | `{{issue.status.statusCategory.key}}` (verify on your instance) |
| `resolution` | Resolution name, empty when unset | `{{issue.resolution.name}}` |
| `actor` | Display name of who triggered the change (omitted from templates when closing-user inclusion is disabled) | `{{initiator.displayName}}` (verify on your instance) |
| `lastComment` | Text of the latest comment. **Optional**: when absent, the executor reads it from Jira | `{{issue.comments.last.body.jsonEncode}}` (verify on your instance) |

Jira Automation smart values vary with the product version and the rule trigger. Test the ones marked "verify" with the rule's audit log before relying on them. If `lastComment` cannot be produced by the rule, leave it out and the executor reads the last comment from Jira. The `{...}` expressions in the executor configuration are Qute templates, evaluated by the executor; the `{{...}}` expressions in an Automation rule are smart values, evaluated by Jira. Never mix them.

## `POST /api/v1/up/marketplace-executor/webhooks/jira/native-events`

The native Jira webhook channel. The body is the **raw Jira webhook payload** (`jira:issue_updated`), as Jira sends it. The executor reduces it to the normalised fields above and ignores (and never logs) the rest:

| Normalised field | Native payload source |
|:--|:--|
| `correlationId` | `issue.fields.<correlation field>` |
| `key`, `id` | `issue.key`, `issue.id` |
| `project`, `issueType` | `issue.fields.project.key`, `issue.fields.issuetype.name` |
| `status`, `statusCategory` | `issue.fields.status.name`, `issue.fields.status.statusCategory.key` |
| `resolution` | `issue.fields.resolution.name` |
| `actor` | `user.displayName` |
| `lastComment` | last element of `issue.fields.comment.comments` (ADF bodies are flattened to text). Absent when the payload has no comment list |
| *extra* | `jira.webhook.extra-fields`: field name to [JSON Pointer](https://datatracker.ietf.org/doc/html/rfc6901) into the payload |

An event is a **status transition** only when the payload has a `changelog.items` entry whose `fieldId` or `field` is `status`. Anything else (an edit, a comment) is ignored with `NOT_A_STATUS_TRANSITION`, **before** any call to the Marketplace Service or Jira.

## Answers of the notification endpoints

| Status | Body | Meaning |
|:--|:--|:--|
| `200` | `{"outcome": "REPORTED", "status": "GRANTED"}` | The final result was posted to the Marketplace Service (`status` is the status reported) |
| `200` | `{"outcome": "IGNORED", "reason": "..."}` | Acknowledged, nothing was posted. See the reasons below |
| `500` | error body | The Marketplace Service (lookup or result delivery) failed. Native webhooks are **retried by Jira** (up to 5 times); a Jira Automation rule does **not** retry: check the rule audit log and re-run it |
| `400` | error body | Malformed JSON only |

| `IGNORED` reason | Why |
|:--|:--|
| `NOT_A_STATUS_TRANSITION` | The event did not change the status (native channel, or an Automation rule not fired on a transition) |
| `MISSING_CORRELATION` | No correlation value (the issue is not a marketplace issue, or the correlation field was not read back) |
| `UNKNOWN_REQUEST` | The Marketplace Service does not know that UUID |
| `NOT_HANDLED_BY_EXECUTOR` | The request does not match this executor's filter |
| `FINAL_RESULT_ALREADY_REPORTED` | A final result already exists for the request (first final status wins) |
| `NON_TERMINAL_STATE` | No outcome rule matched the fields: the issue is not in a final state |

## Automation incoming webhook (outbound)

With the `AUTOMATION_WEBHOOK` interface the executor calls your Automation rule. Headers: `Content-Type: application/json`, `Accept: application/json` and `X-Automation-Webhook-Token`. Body:

```json
{
  "correlationId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "operation": "MARKETPLACE_SUBSCRIBE",
  "request": { "identifier": "...", "provider": { "dataProductFqn": "...", "dataProductPortsFqn": ["..."] }, "consumer": {}, "requester": {} },
  "fields": { "summary": "...", "description": "..." }
}
```
