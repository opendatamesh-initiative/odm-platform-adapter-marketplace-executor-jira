# Jira setup

[Back to README](../README.md)

## Overview

For the executor to work, Jira must be prepared in two directions:

1. **Jira lets the executor in.** A dedicated integration identity (a service account, API token or Personal Access Token), permission to create the issues, and the custom field that ties an issue to a marketplace request.
2. **Jira calls the executor back.** When an issue is completed, an Automation rule (or a native webhook) sends a small JSON message so the marketplace learns the outcome. That message goes through an **authenticating gateway**, which checks who is calling.

This page covers the steps shared by every Jira practice. The settings that depend on the practice (issue types, request types, Automation incoming webhooks) are in the [use cases](use-cases.md).

**Prerequisites:** Jira Cloud, or Jira Data Center 8.14 or later, and an **authenticating gateway** to authenticate the notification endpoints (see [Securing the notification endpoints](#8-securing-the-notification-endpoints)).

Items marked "verify on your instance" depend on the Atlassian release; check them before relying on them.

## Contents

1. [Integration identity](#1-integration-identity)
2. [Permissions](#2-permissions)
3. [The correlation custom field](#3-the-correlation-custom-field)
4. [Project and issue type (interface `ISSUE`)](#4-project-and-issue-type-interface-issue)
5. [Service project and request types (interface `SERVICE_DESK_REQUEST`)](#5-service-project-and-request-types-interface-service_desk_request)
6. [Statuses, resolutions and workflow](#6-statuses-resolutions-and-workflow)
7. [Automation incoming webhook (interface `AUTOMATION_WEBHOOK` only)](#7-automation-incoming-webhook-interface-automation_webhook-only)
8. [Securing the notification endpoints](#8-securing-the-notification-endpoints)
9. [Completion channel A - Jira Automation rule (recommended)](#9-completion-channel-a---jira-automation-rule-recommended)
10. [Completion channel B - native webhook](#10-completion-channel-b---native-webhook)
11. [Rotating secrets](#rotating-secrets)

Related: [Use cases](use-cases.md) · [Provisioning](provisioning.md) · [Configuration reference](configuration-reference.md) · [Troubleshooting](troubleshooting.md)

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

## 8. Securing the notification endpoints

The two notification endpoints (`.../webhooks/jira/issue-events` and `.../webhooks/jira/native-events`) have **no authentication of their own**: anyone who can reach them can forge a final result for a request. They must therefore never be exposed to Jira or the internet directly. Put an authenticating gateway or reverse proxy in front of them, with these properties:

- It authenticates Jira (or the component that relays Jira's calls).
- It forwards **only** the two webhook paths. In particular `POST .../requests` must not be reachable through it, because it would let a caller inject access requests bypassing the Marketplace Service.
- The executor is reachable only by the Marketplace Service and by this gateway (network policy or firewall).
- For **native Jira webhooks**, the gateway also verifies the `X-Hub-Signature` HMAC, because the executor does not.

Choose one of the options below.

### Option A: your own authenticating gateway

Any API gateway, reverse proxy or small function works. Authenticate the caller with whatever fits your environment (API key, bearer token, mTLS, IP allow-list combined with a secret), allow-list the two webhook paths and forward to the executor.

- **Jira Automation rule** (channel A): the rule can send a fixed secret header (marked *Hidden*) that the gateway checks. See [section 9](#9-completion-channel-a---jira-automation-rule-recommended).
- **Native webhook** (channel B): Jira can only sign the call. The gateway verifies the signature, adds the credentials expected by your gateway or upstream and forwards. A reference sketch and a security checklist are in [section 10](#10-completion-channel-b---native-webhook).
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

3. **A dedicated Blindata identity.** Create an identity for Jira with the **`MARKETPLACE_EDITOR`** permission and nothing else, and authenticate it with an API key or with OAuth 2.0 (for example Microsoft Entra ID). See [Authenticating to Blindata](#authenticating-to-blindata).

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

### Authenticating to Blindata

Every call to the agent must identify a Blindata user. The agent has no user store of its own: it passes the credentials to the Blindata platform, which returns the user's permissions. The caller must also send the `X-BD-Tenant` header with the tenant it acts on. Two methods are available.

| Method | Headers the caller sends | Fits |
|:--|:--|:--|
| API key | `X-BD-User`, `X-BD-ApiKey`, `X-BD-Tenant` | Jira Automation rule (channel A), or a gateway |
| OAuth 2.0 (Microsoft Entra ID) | `Authorization: Bearer <access token>`, `X-BD-Tenant` | A gateway or integration component that can obtain tokens |

#### API key

1. In Blindata, create an **application user** for Jira and grant it only the **`MARKETPLACE_EDITOR`** permission. Generate its API key and keep it in a secret store.
2. In the Jira Automation rule (see [section 9](#9-completion-channel-a---jira-automation-rule-recommended)), set these headers on the *Send web request* action, marking the key and the user **Hidden**:

| Header | Value |
|:--|:--|
| `X-BD-User` | the application user name |
| `X-BD-ApiKey` | the API key |
| `X-BD-Tenant` | the Blindata tenant |

The agent exchanges the user and API key for a Blindata access token on each call, so a revoked key stops working immediately. API keys do not refresh themselves: rotate them according to your security policy and update the rule's header.

#### OAuth 2.0 with Microsoft Entra ID

Blindata accepts access tokens issued by Microsoft Entra ID as bearer tokens, including tokens of an application (service principal) obtained with the client credentials flow. The token never carries Blindata permissions: they come from the Blindata user it maps to.

1. **Register an application in Entra ID** for the Jira integration and create a client credential for it (a certificate is preferable to a client secret). Note the application's object id (`oid`) and the Directory (tenant) id (`tid`).
2. **Request tokens with the audience Blindata is configured for.** Blindata accepts only tokens with exactly one audience, matching the Azure provider configured for your tenant. Ask your Blindata administrator for the value, and request the matching scope (typically `api://<application-id>/.default`).
3. **Enable the Azure provider for the tenant** in Blindata, and create a Blindata user whose name is `msazure:<oid>@<tid>` for the application, with only the **`MARKETPLACE_EDITOR`** permission. A token whose application has no such user is rejected with `401`.
4. **Call the agent** with the token:

```bash
TOKEN=$(curl -s -X POST "https://login.microsoftonline.com/<tid>/oauth2/v2.0/token" \
  -d grant_type=client_credentials \
  -d client_id="$ENTRA_CLIENT_ID" \
  -d client_secret="$ENTRA_CLIENT_SECRET" \
  -d scope="api://<application-id>/.default" | jq -r .access_token)

curl -i -X POST "https://<agent-host>/agent-api/v1/odm/proxy/<configUuid>/marketplace-executor-jira/api/v1/up/marketplace-executor/webhooks/jira/issue-events" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" -H "X-BD-Tenant: <tenant>" \
  -d '{"correlationId":"00000000-0000-0000-0000-000000000000","fields":{"statusCategory":"done"}}'
# -> 200 {"outcome":"IGNORED","reason":"UNKNOWN_REQUEST"}
```

Notes:

- **Jira cannot obtain tokens.** A Jira Automation rule can send only fixed headers, and Entra tokens expire (about an hour). The component that calls the agent (a gateway, an Azure Logic App or a function) must request and renew the token, then forward the Jira event with the `Authorization` header.
- **Token lifetime.** Request a new token when the current one expires. A `401` after a period of correct behaviour usually means an expired token or an expired client credential.
- **One identity per integration.** Use a separate Entra application, and a separate Blindata user, for each integration, so that one can be revoked without affecting the others.
- Verify on your instance: the audience value, the scope and the Azure provider settings depend on how your Blindata tenant is configured.

#### Which method to choose

- Use an **API key** when Jira calls the agent directly from an Automation rule: it is the only method a rule can use without extra components.
- Use **OAuth 2.0** when your organisation requires short-lived credentials or centrally managed identities in Entra ID, and a gateway or integration component already sits between Jira and the agent.
- In both cases, grant the identity **`MARKETPLACE_EDITOR`** only, and never `DATAOPS_ADMIN`.

## 9. Completion channel A - Jira Automation rule (recommended)

A rule calls the executor, through your authenticating gateway (see [Securing the notification endpoints](#8-securing-the-notification-endpoints)), when the issue is completed. One rule per project (or one for several).

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

- When the rule calls the Blindata agent directly, send the API key headers described in [Authenticating to Blindata](#authenticating-to-blindata). For OAuth 2.0 (Entra ID), a component that obtains the token must sit between Jira and the agent.
- Automation rules consume your plan's **automation usage** limits. Check them for the expected volume.
- Automation waits for the web request answer for a limited time (about 30 s: verify on your instance) and does **not retry**. A `500` from the executor is visible only in the rule's **audit log**: fix the cause and re-run the rule for that issue.
- The gateway must be a **permitted target** of the web request: on Cloud, expose it over TLS and allow-list the Atlassian egress IPs on your edge, since the gateway is usually inside your network.

## 10. Completion channel B - native webhook

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

## Rotating secrets

- *OAuth service account*: create or regenerate the client secret, update `JIRA_CLIENT_SECRET` and restart the executor. Tokens already issued are cached until their expiry.
- *API token* (Cloud): create the new token before the old one expires (at most 365 days), update `JIRA_API_TOKEN`, restart the executor, then revoke the old token.
- *Personal Access Token* (Data Center): create the new token, update `JIRA_PAT`, restart, then revoke the old one.
- *Automation webhook token*: regenerate it in the rule and update the `webhook-token` source.
- Between the rotation and the restart, issue creation fails with an `ERROR` message that names an authentication failure.
- The credentials Jira uses towards your gateway are rotated on the gateway side and in the Automation rule's hidden header (or in the signature-verifying gateway's secret store). The Blindata API key of [Authenticating to Blindata](#authenticating-to-blindata) is rotated in Blindata and in the rule's header together.

## Checklist

- [ ] Integration identity created, credentials in the secret store
- [ ] Permissions granted in every target project
- [ ] Correlation field created and on the create/edit screens or request type
- [ ] Project and issue type, or service project and request types, ready
- [ ] Resolutions and workflow checked against the outcome rules
- [ ] Notification endpoints secured (gateway or blindata-agent) and the smoke test passes
- [ ] One completion channel configured and tested (see the [smoke test](provisioning.md#verify))

---

[Back to README](../README.md) | [Use cases](use-cases.md) | [Provisioning](provisioning.md)
