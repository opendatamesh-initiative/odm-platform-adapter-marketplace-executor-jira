# Jira setup

[Back to README](../README.md)

## Overview

Before the executor can work, a Jira administrator has to prepare a few things in Jira: an account the executor logs in with, a custom field to remember which marketplace request an issue belongs to, the project (or service desk) where issues are created, and a way for Jira to tell the executor when an issue is completed.

In short:

1. Create the integration identity and give it the right permissions.
2. Create one text custom field (the "correlation field") and put it on the right screens.
3. Prepare the project (issue type) or the service project (request type) that receives the requests.
4. Make sure the workflow sets a **resolution** when an issue is closed, and that statuses and resolutions match the executor's outcome rules ([what a resolution is](#what-a-resolution-is)).
5. Set up **one** completion channel: an Automation rule (recommended) or a native webhook.

Related: [Use cases](use-cases.md) · [Provisioning](provisioning.md) · [Configuration reference](configuration-reference.md) · [Troubleshooting](troubleshooting.md)

Items marked "verify on your instance" depend on the Atlassian release; check them before relying on them.

## 1. Integration identity

One credential is used for every interface your profiles use.

### Cloud

1. Create a **service account** in the Atlassian administration (admin.atlassian.com), give it product access to Jira (and Jira Service Management when you use it) and add it to the projects it needs.
2. Create an **OAuth 2.0 credential** for the service account, with the scopes needed to create and edit issues, read users, create service desk requests and read comments. Copy the client id and client secret into your secret store and expose them as `JIRA_CLIENT_ID` and `JIRA_CLIENT_SECRET`.
3. Use `https://api.atlassian.com/ex/jira/<cloudId>` as `jira.api-url` (`JIRA_API_URL`). To find the cloudId, open `https://<your-site>.atlassian.net/_edge/tenant_info` in a browser: the answer is `{"cloudId": "..."}`.
4. The alternative is a **scoped API token** of a dedicated user (`JIRA_AUTH_TYPE=API_TOKEN`, with `JIRA_API_TOKEN_EMAIL` and `JIRA_API_TOKEN`). API tokens expire: rotate them within their validity (at most 365 days).

Verify on your instance: the exact scope names, the token endpoint of service accounts and whether the token request needs an `audience` depend on the Atlassian release. The executor sends `grant_type`, `client_id` and `client_secret` (and `scope` if configured) to `jira.auth.oauth2.token-url`, which you can override. Check the token request before the first run:

```bash
curl -s -X POST "https://auth.atlassian.com/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d grant_type=client_credentials \
  -d client_id="$JIRA_CLIENT_ID" \
  -d client_secret="$JIRA_CLIENT_SECRET"
# -> {"access_token":"...","token_type":"Bearer","expires_in":3600,...}
```

### Data Center

- Default choice: a **dedicated user** with a **Personal Access Token** (`JIRA_AUTH_TYPE=PERSONAL_ACCESS_TOKEN`, `JIRA_PAT`). Create it in the user's profile, *Personal Access Tokens*, and set an expiry. The executor sends it as `Authorization: Bearer <pat>`.
- Alternative, Data Center **11.1+**: a **service account** with OAuth 2.0 client credentials (`JIRA_AUTH_TYPE=OAUTH2_CLIENT_CREDENTIALS`). The token URL defaults to `{site-url}/rest/oauth2/latest/token`. Verify on your instance that your release supports it.
- Data Center supports REST **v2 only**. The site URL is the API base, so `JIRA_API_URL` is not needed.
- Data Center reaches end of life on 2029-03-28: plan your move to Cloud.

## 2. Permissions

Give the integration identity the **minimum** needed, in each target project:

| Permission | Needed for |
|:--|:--|
| **Create issues** | `ISSUE` |
| **Edit issues** | label (`correlation.label`) and `correlation-via-edit` |
| **Browse users and groups** (global) | requester resolution (`set-reporter`, JSM on behalf of) |
| **Modify Reporter** | `set-reporter` |
| **Service desk agent** of the service project | JSM `raiseOnBehalfOf` |
| **Browse projects**, **View comments** | reading the last comment |

The one credential is used for every interface your profiles use, so grant only what the configured profiles need. A missing permission only shows up at runtime as an `ERROR` result with a 401/403 or 400 message. On Cloud a user's email may be hidden by privacy settings: if requesters are not found, check the *Browse users and groups* permission and email visibility.

## 3. The correlation custom field

The executor writes the marketplace request UUID in a **single-line text custom field**, so that completion notifications can be correlated.

1. Create a custom field of type **Text field (single line)**, for example *Marketplace request id*. Note its id (`customfield_10050` in these docs): on Cloud it is in the field's URL; in the REST API see `GET /rest/api/{v}/field`.
2. Add it to the **create and edit screens** of the target issue types (and, for JSM, to the **request type**, or use `correlation-via-edit`). A field that is not on the screen makes the creation fail with a 400 that names it.
3. Set `jira.correlation.field` (`JIRA_CORRELATION_FIELD`) to the id. It must look like `customfield_<digits>`.

## 4. Project and issue type (interface `ISSUE`)

- A project (for example `ACC`) and an issue type (for example `Task`); put the project key and issue type name in `project` and `issue-type`.
- The create screen of that issue type carries the correlation field, plus any field you fill through `fields`, `object-fields` or `list-fields`.
- Optional: *Modify Reporter* and user-search permission if you use `set-reporter: true`.

## 5. Service project and request types (interface `SERVICE_DESK_REQUEST`)

- A JSM service project and one request type for subscribe and one for unsubscribe. Find the ids with `GET /rest/servicedeskapi/servicedesk` and `GET /rest/servicedeskapi/servicedesk/{serviceDeskId}/requesttype`, or in the portal URL.
- The correlation field on the request type, or `correlation-via-edit: true`.
- The requester must be a **customer** of the service project and the integration identity a **service desk agent**.
- Add the request types to a portal group so customers see their requests.
- If approvals are part of the workflow, note the status that means "declined" and map it to `REFUSED` in the outcome rules ([Use case 2](use-cases.md#use-case-2---jira-service-management-requests)).

## 6. Statuses, resolutions and workflow

The executor decides the outcome from the fields of the completion event: `status`, `statusCategory` (`new`, `indeterminate`, `done`) and `resolution`. Of the three, the **resolution** is the one that says whether access was granted or declined, so it deserves a careful look.

### What a resolution is

The **status** says *where* an issue is in its workflow (*To Do*, *In Progress*, *Done*); the **resolution** says *how it ended* (*Done*, *Won't Do*, *Duplicate*...). Two issues can both be in status *Done*, one with resolution *Done* (the work was carried out) and the other with *Won't Do* (it was deliberately not carried out). For an access request this is exactly the difference between "access granted" and "access declined".

Key facts about the field:

- **It is a built-in system field.** Every Jira issue has it, on Cloud and Data Center, in Jira Software and Jira Service Management. Its field id is `resolution`; in the REST API it appears as `fields.resolution` (an object with `id` and `name`, or `null`).
- **Empty means "unresolved".** Jira considers an issue *resolved* when the field has any value and *unresolved* when it is empty, regardless of the status. JQL (`resolution = Unresolved`), filters, dashboards and reports all rely on it. A common misconfiguration is a workflow that moves issues to *Done* without setting a resolution: they look closed but Jira still counts them as unresolved.
- **The list of values is global.** Resolutions are defined once for the whole instance and shared by every project: **Settings, Issues, Resolutions** (Cloud) or *Administration, Issues, Resolutions* (Data Center). Typical defaults are *Done*, *Won't Do*, *Duplicate* and *Cannot Reproduce* on Cloud; Data Center instances often also have older ones such as *Fixed*, *Won't Fix* and *Incomplete*. Many organisations add or rename resolutions, so always check your instance.
- **It is set by workflow transitions, not by editing.** The field is normally not on the create or edit screens. It gets a value in one of these ways:
  - a **transition screen** that contains the *Resolution* field, so the person closing the issue picks one (the classic "Resolve Issue" screen);
  - a **post function** "Update issue field → Resolution" on the transition, which sets it automatically (company-managed projects and Data Center);
  - an **Automation rule** that transitions or edits the issue and sets the resolution;
  - the project template itself: company-managed Jira Software and Jira Service Management templates usually set it on their done/resolve transitions, while **team-managed projects often leave it empty** unless you add an Automation rule.
- **It should be cleared on reopen.** A "Reopen" transition should set the resolution back to empty (post function "Update issue field → Resolution: None"). Otherwise a reopened issue still counts as resolved. The executor is not affected (the [first final status wins](concepts.md#status-taxonomy-and-rules)), but your Jira reports are.
- **The values offered can be restricted per transition.** Since the list is global, use the transition properties `jira.field.resolution.include` / `jira.field.resolution.exclude` (company-managed workflows and Data Center) to show only the meaningful values, for example only *Done* on an "Approve" transition.

### How the executor uses it

The completion event carries the resolution **name** (not its id): the Automation rule sends `{{issue.resolution.name}}` and the native webhook carries `issue.fields.resolution.name`. The executor puts it in the normalised field `resolution`; when the issue has no resolution the value is an **empty string**.

The [outcome rules](concepts.md#outcome-rules) then match it with regular expressions. The rules shipped in the examples map resolutions like this:

| Resolution on the completed issue | Cause | Subscribe result | Unsubscribe result |
|:--|:--|:--|:--|
| *Done*, *Fixed*, *Resolved*, *Granted*, *Revoked* | `FULFILLED` | `GRANTED` | `REVOKED` |
| *Won't Do*, *Won't Fix*, *Declined*, *Rejected*, *Cannot Reproduce*, *Duplicate* | `REFUSED` | `DENIED` | `ERROR` (refused on Jira) |
| Anything else, or **no resolution** | `FAILED` (fallback rule) | `ERROR` | `ERROR` |

Things to keep in mind when writing the rules:

- Regexes match the **whole** value and are case-sensitive unless they start with `(?i)`.
- Because an unset resolution is an empty string, a rule with `resolution: ''` matches exactly the issues closed without one. Use it if you want to handle that case differently from an unknown resolution.
- Rename a resolution in Jira and you must update the regexes too, because the executor sees the name.

### Recommended setup

1. **Check the list** of resolutions on your instance and adapt the regexes in your profiles to the names you actually use.
2. **Make closing unambiguous.** The simplest workflow has two closing transitions, each setting the resolution with a post function so fulfillers never have to pick one:

   | Transition | To status | Resolution set by post function |
   |:--|:--|:--|
   | *Grant* (or *Approve*) | *Done* | *Done* |
   | *Decline* | *Done* (or *Declined*) | *Won't Do* |
   | *Reopen* | *To Do* | cleared (None) |

   If you prefer a single *Done* transition, put the *Resolution* field on its transition screen, make it required, and restrict the values offered.
3. **Keep the fallback rule** (`statusCategory: done` → `FAILED`) last, so that an issue closed without a usable resolution still produces a final result instead of leaving the request `PENDING`.
4. **Verify** by closing a test issue and reading it back:

   ```bash
   # <api-base>: the site URL on Data Center, JIRA_API_URL on Cloud with OAuth
   curl -s -H "Authorization: Bearer $TOKEN" \
     "<api-base>/rest/api/2/issue/ACC-1?fields=status,resolution" | jq '.fields | {status: .status.name, category: .status.statusCategory.key, resolution: .resolution.name}'
   # -> {"status":"Done","category":"done","resolution":"Done"}
   ```

   A `null` resolution on a done issue means the workflow does not set it.

Subscribe and unsubscribe can share a project and workflow or use different ones.

## 7. Automation incoming webhook (interface `AUTOMATION_WEBHOOK` only)

For each operation create an Automation rule with the trigger *Incoming webhook*, and an action *Create issue* that sets the correlation field to `{{webhookData.correlationId}}`. Copy the generated URL and secret into `webhook-url` and `webhook-token`. Details and the body are in [Use case 3](use-cases.md#use-case-3---jira-automation-incoming-webhook).

## 8. Completion channel A - Jira Automation rule (recommended)

A rule calls the executor, through your authenticating gateway (see [Securing the notification endpoints](provisioning.md#securing-the-notification-endpoints)), when the issue is completed. One rule per project (or one for several).

1. **Trigger**: *Issue transitioned*. Add a condition *Issue fields condition / status category = Done*, scoped to the projects of your profiles. Add a *JQL condition* `"<field name>" is not EMPTY` so that only marketplace issues call the executor.
2. **Action**: *Send web request*.

| Setting | Value |
|:--|:--|
| Web request URL | `https://<gateway-host>/<route>/api/v1/up/marketplace-executor/webhooks/jira/issue-events` |
| HTTP method | `POST` |
| Headers | `Content-Type: application/json` plus the credentials your gateway expects, marked **Hidden** |
| Web request body | *Custom data*, the JSON below |

```json
{
  "correlationId": "{{issue.customfield_10050}}",
  "fields": {
    "key": "{{issue.key}}",
    "id": "{{issue.id}}",
    "project": "{{issue.project.key}}",
    "issueType": "{{issue.issueType.name}}",
    "status": "{{issue.status.name}}",
    "statusCategory": "{{issue.status.statusCategory.key}}",
    "resolution": "{{issue.resolution.name}}",
    "actor": "{{initiator.displayName}}",
    "lastComment": "{{issue.comments.last.body.jsonEncode}}"
  }
}
```

Verify on your instance: check `{{issue.status.statusCategory.key}}`, `{{initiator.displayName}}` and `{{issue.comments.last.body.jsonEncode}}` in the rule audit log. In particular, `jsonEncode` does not add the surrounding quotes, so the value stays between the quotes of the template, and a last comment that does not exist renders empty. If `lastComment` is not reliable, **remove it from the body**: the executor then reads the last comment from Jira. Remove `actor` too if you do not want personal data to travel. The field meanings are in the [API reference](api-reference.md#post-apiv1upmarketplace-executorwebhooksjiraissue-events).

Notes:

- Automation rules consume your plan's **automation usage** limits. Check them for the expected volume.
- Automation waits for the web request answer for a limited time (about 30 s: verify on your instance) and does **not retry**. A `500` from the executor is visible only in the rule's **audit log**: fix the cause and re-run the rule for that issue.
- The gateway must be a **permitted target** of the web request: on Cloud, expose it over TLS and allow-list the Atlassian egress IPs on your edge, since the gateway is usually inside your network.

## 9. Completion channel B - native webhook

Use this when you prefer not to maintain an Automation rule. **It requires a customer-provided signature-verifying gateway**, because Jira signs the call but cannot send the credentials your auth gateway or upstream expects.

1. **Create the webhook**: *System, WebHooks* (Cloud and Data Center). Data Center 10+ can also use `/rest/jira-webhook/1.0/webhooks` (verify on your release). Event `jira:issue_updated`, a **JQL filter** that limits it to your projects and to issues with the correlation field set (`project in (ACC) AND "Marketplace request id" is not EMPTY`) and, on Cloud, a **secret**.
2. **URL**: the gateway's URL, which forwards to the executor's `.../api/v1/up/marketplace-executor/webhooks/jira/native-events` path (through your auth gateway, if separate).
3. The payload is mapped as described in the [API reference](api-reference.md#post-apiv1upmarketplace-executorwebhooksjiranative-events). Anything that is not a status transition is ignored.

Reference sketch of the gateway (for example an AWS Lambda behind an API Gateway). It verifies `X-Hub-Signature` (HMAC-SHA256 of the **raw** body with the webhook secret), adds the credentials expected by your gateway or upstream, taken from a secret store and forwards:

```javascript
// pseudo-code: adapt to your platform
const crypto = require('crypto');

exports.handler = async (event) => {
  const raw = event.body;                                   // the raw, unparsed body
  const received = (event.headers['x-hub-signature'] || '').replace('sha256=', '');
  const expected = crypto.createHmac('sha256', process.env.JIRA_WEBHOOK_SECRET).update(raw).digest('hex');

  const ok = received.length === expected.length &&
             crypto.timingSafeEqual(Buffer.from(received), Buffer.from(expected));   // constant time
  if (!ok) return { statusCode: 401 };                      // log nothing from the request

  const response = await fetch(process.env.NATIVE_EVENTS_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      // credentials expected by your gateway/upstream, from the secret store
      'Authorization': `Bearer ${process.env.UPSTREAM_TOKEN}`
    },
    body: raw
  });
  return { statusCode: response.status };                   // a 5xx makes Jira retry
};
```

Security checklist for the gateway:

- TLS only, in both directions.
- Verify the signature with a **constant-time** comparison on the **raw** body, before parsing.
- Keep the secret and the upstream credentials in a secret store.
- **Never log** headers, bodies or tokens.
- Pass the executor's status code through: Jira retries on `5xx` (up to 5 times).
- The native endpoint has no executor-side authentication: only the gateway and the Marketplace Service may reach it.

> **Choose one channel per project.** Using both for the same project only produces duplicate notifications, which the executor tolerates (first final status wins) but which are noise.

## Checklist

- [ ] Integration identity created, credentials in the secret store
- [ ] Permissions granted in every target project
- [ ] Correlation field created and on the create/edit screens or request type
- [ ] Project and issue type, or service project and request types, ready
- [ ] Resolutions and workflow checked against the outcome rules
- [ ] One completion channel configured and tested (see the [smoke test](provisioning.md#verifying-the-installation))
