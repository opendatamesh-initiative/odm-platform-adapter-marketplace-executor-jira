# Troubleshooting

[Back to README](../../README.md)

## Overview

Most problems fall into three groups: the executor does not start (configuration), an issue is not created (Jira side), or the request stays `PENDING` after the issue is completed (the completion notification did not arrive or was ignored). This page tells you where to look for each.

Related: [Build and run](build-and-run.md) · [Jira setup](jira-setup.md) · [Configuration reference](configuration-reference.md) · [API reference](api-reference.md)

## Logs

Look for the request UUID and the issue key, they appear in every business log line:

| Log line | Meaning |
|:--|:--|
| `Opened Jira issue <key> for request <uuid> operation <op>` (`via automation` instead of the key for the Automation kind) | Leg 1 succeeded |
| `PENDING result for request <uuid> not delivered; Jira issue <key> is open` | The (best effort) `PENDING` post failed; the issue exists and the notification will still work |
| `Unable to open Jira issue for request <uuid>: <cause>` | The issue could not be opened; an `ERROR` result is posted |
| `Jira issue event for request <uuid> ignored: <REASON>` | Notification acknowledged without a result |
| `Last comment of Jira issue <key> not read: <cause>` | The comment read failed; the result is posted without it |
| error with the request UUID | A result could not be delivered |

Request headers, tokens, secrets, webhook URLs and request or native payload bodies are never logged.

## The executor does not start

It prints `Invalid Jira executor configuration:` followed by a numbered list. Typical causes: bad URL, missing credentials, Data Center with REST v3, personal access token on Cloud, Cloud OAuth without the `api.atlassian.com` API URL, a correlation field that is not `customfield_<digits>`, a field map that overrides a reserved field, an unparsable template, a profile without a `FULFILLED` rule, an invalid regex. The complete list is in [startup validation](configuration-reference.md#startup-validation). A "required property ... not found" error means `jira.profiles.subscribe` or `jira.profiles.unsubscribe` is missing, or `JIRA_SITE_URL` / `JIRA_CORRELATION_FIELD` is not set.

## The issue is not created

The marketplace shows an `ERROR` result with a technical message:

| Message hint | Check |
|:--|:--|
| `Jira rejected the integration credentials (HTTP 401/403 ...)` | The service account or token, its product access and the project permissions |
| `Unable to obtain a Jira OAuth token ...` | Client id and secret, scopes, `jira.auth.oauth2.token-url` |
| HTTP 400 naming a field | The correlation field (or another field) is not on the create screen or request type; use `correlation-via-edit` for JSM |
| Requester not found | The requester must be of type `USER`, with an email known to Jira; check *Browse users and groups* and email visibility |
| JSM 400/403 with a hint | The requester must be a customer of the service project and the integration identity a service desk agent |
| `Jira Automation webhook rejected the call` | The webhook token and the rule's incoming webhook trigger |
| HTTP 429 after retries | Raise `jira.rate-limit.max-attempts` or `backoff`, or reduce load |
| Request answered `409` | The [request filter](configuration-reference.md#request-filter) does not accept it. Nothing is created |

## The request stays `PENDING`

The final status only ever comes from a completion notification. Check in this order:

1. **Is the correlation field set?** Open the issue: the *Marketplace request id* must hold the UUID. If it is empty, check that the field is on the create screen or the request type (or use `correlation-via-edit`), and, for the Automation kind, that the creation rule copies `{{webhookData.correlationId}}` into it.
2. **Did the Automation rule or the webhook fire?** Check the rule's **audit log** (Automation does not retry), or the webhook's delivery log. If the native webhook never arrives, check the Webhook Auth Gateway (signature failures, wrong URL).
3. **Did the gateway accept the call?** `401` or `403` come from your gateway: check the credentials Jira sends and the route or path allow-list. Run the [smoke test](build-and-run.md#verify).
4. **What did the executor answer?** Look for `ignored: <REASON>` in the executor log:
   - `NOT_A_STATUS_TRANSITION`: the native webhook fired for an edit, not a transition (expected for most events);
   - `MISSING_CORRELATION`: the correlation field was not read back (wrong smart value in the rule, or a wrong `jira.correlation.field`);
   - `UNKNOWN_REQUEST`: the correlation value is not a Marketplace Service UUID;
   - `NOT_HANDLED_BY_EXECUTOR`: the filter does not accept the request any more;
   - `FINAL_RESULT_ALREADY_REPORTED`: a final result already exists;
   - `NON_TERMINAL_STATE`: no outcome rule matched the fields. Compare the real `statusCategory` / `status` / `resolution` with your rules (regexes are full matches and case-sensitive unless `(?i)`). An issue closed without a resolution sends an empty `resolution`: see [what a resolution is](jira-setup.md#what-a-resolution-is).
5. A `500` from the endpoint means the Marketplace Service was unreachable or rejected the result: fix it and re-run the rule (Automation), or wait for Jira's retries (native webhook).

## Other situations

- **Unexpected result message or missing text.** Missing template values render empty by design. Check the data model in [Templates](templates.md#data-model). A message that must not contain the Jira user: see [Privacy](concepts.md#privacy).
- **Two executors both report.** Filters are not exclusive: see [Several executors](examples.md#several-executors-behind-one-marketplace).
- **Unsubscribe while the subscribe issue is still open.** Two issues exist at the same time. The executor does not resolve this: handle it in your Jira process.
- **Duplicate notifications.** Both completion channels are active for the same project. Keep one.
