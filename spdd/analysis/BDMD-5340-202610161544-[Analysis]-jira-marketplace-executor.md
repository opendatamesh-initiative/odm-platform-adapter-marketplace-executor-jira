# SPDD Analysis: Jira Marketplace Executor (issue-driven access request fulfilment)

## Original Business Requirement

> This analysis applies the BDMD-5340 requirement, written for ServiceNow, to the **Jira** executor (`odm-platform-adapter-marketplace-executor-jira`). The requirement text is copied unchanged from the ServiceNow analysis (`odm-platform-adapter-marketplace-executor-servicenow/spdd/analysis/BDMD-5340-202610161110-[Analysis]-servicenow-marketplace-executor.md`). Read "ServiceNow" as "Jira" wherever it names the target tool.

BDMD-5340 on odm-platform-adapter-marketplace-executor-servicenow
The ServiceNow marketplace executor is an adapter service that allows to integrate the ODM marketplace workflows and processes into the ServiceNow infrastructure. 

The service should be easily configurable, allowing the user to specify the resource type created on ServiceNow, and its content structure.
It should also be configurable to adapt on different processes/practices the user has adopted on ServiceNow.

The resource created must contain in some part of its structure the marketplace request identifier, so when the ServiceNow webhook is triggered, it is possible to update the correct access request in the marketplace service. 

Workflow example (must be valid for every access request Operation)
Task Opened:
 - Marketplace Access Request --> Executor --> Task Opened on Service Now
Task Completed:
- Ticket Completed on Service Now --> Service Now WebHook --> Executor --> Marketplace Access Request Updated with the Operation result

The repository README.md should contain a detailed, user friendly section that explain how to configure the service and ServiceNow.

Nice to have but not mandatory :
The service should be able to be used also in a configuration where
- blindata-api webHooks--> executor --> ServiceNow
- Service Now webHooks --> executor --> blindata-api API 
(include it in the analysis and if it is actually usefull instead of doing something like blindata-api <--> ServiceNow)

### Requirement Clarifications (product owner answers to the first analysis round)

Requirement Ambiguities resolution
- "Resource type": support all Service Now interfaces from day one.
- "Adapt to different processes/practices": Ok per-operation configuration
- "Unsubscribe refused or failed": Use ERROR for both plaform( and services) errors/exceptions and user (service now side) denial. 
- "Who approves": yes, but only for MARKETPLACE_SUBSCRIBE operation. For the MARKETPLACE_UNSUBSCRIBE the denial is considered as an error (in this case the error message should be descriptive of the scenario)
- "Partial grants": Do not allow partial grants. The ServiceNow ticket is considered to handle all the ports (at once) in the access request.
- "Message content": The message should describe the grant process (resolution notes, close notes, ticket number, link,etc... and maybe username?)
- "Ticket lifecycle after completion": First final status wins. 
- "Time-bound access": automatic revoke is out of scope for now. (include in a "out of scope" subsection of the analysis how would you manage that, considering also the different authentication layers)
- "Multiple executors": yes, possibly. In the ServiceNow exectuor there should be the possibility to configure the filtering of requests, so only certain requests are handled.

### Requirement Clarifications (second round)

- ServiceNow → executor (webhook) the auth is not handled by the executor, but by the blindata-agent proxy
- executor --> serviceNow auth is handled by a serviceNow application token configured in the executor

Additional requirements: in the readme should be described a basic scenario (with the base config) for each ServiceNow Creation Interfaces use.

### Requirement Clarifications (third round)

"All ServiceNow interfaces": confirmed
"Catalog ordering style": yes requested_for must resolve to the marketplace requester
"Request filter criteria": ok the proposed ones. Use the regext for pattern matching
"Closing user in messages": confirmed inclusion. Make the inclusion configurable
"First-final-wins with co-handling executors"exlusive filters are an acceptable operating rule
"Agent authorization for the webhook route": A new executor auth filter should be included in the development
"Deployments without blindata-agent": confirmed that the agent is a hard prerequisite. Standalone deployments must provide their own gateway
"ServiceNow application token": use best practice and suggested auth mechanism for this scenario

Template engine (product owner, earlier round): "for the engine use the best fit for Quarkus" → Quarkus Qute.

### Jira-specific scoping (this analysis)

- Same story: BDMD-5340.
- The product-owner decisions above are **inherited as settled** for Jira, with "ServiceNow" read as "Jira". Only Jira-specific questions are open (see "Requirement Ambiguities").
- Supported Jira deployments: **Jira Cloud and Jira Data Center**.

### Requirement Clarifications (Jira, first round)

Resolved ambiguities:
1. Update the agent filter signature to allow all marketplace-executors
2. Ok but include also the built-in webhooks. (the headers pain can be resolved by a future agent extension or an additional gateway, e.g. lambda functions etc..)
3. Ok for all 3
4. For the API version why is not possible to support both?
5. Yes copy the required code

Accept all other proposed solution to ambiguities

Follow-up (agent filter path): "No use a general marketplace-executor path, so we do not need to modify agent code each time an executor is added" → one webhooks namespace `POST …/api/v1/up/marketplace-executor/webhooks/**` instead of a per-executor allow-list.

How each answer is applied in this analysis:

| # | Topic | Decision |
|---|---|---|
| 1 | Agent filter | One generic `marketplace-executor` filter, routed by service-name prefix, so any number and mix of executors can sit behind one proxy configuration. Its allow-list covers the webhook paths of every executor type, and it denies everything else. The companion agent canvas is updated accordingly. |
| 2 | Completion channels | **Two channels**: Jira Automation "Send web request" (fixed executor contract, authenticates to the agent with hidden headers) **and** native Jira webhooks (native payload). Native webhooks cannot send the agent's auth headers today. Until a future agent extension exists, they need an additional customer gateway (for example a Lambda function that verifies the Jira signature and adds the Blindata headers). |
| 3 | Creation interfaces | `ISSUE`, `SERVICE_DESK_REQUEST` and `AUTOMATION_WEBHOOK` confirmed. |
| 4 | REST API version | Both are supported: v2 (default, Cloud and DC) and v3 (Cloud only, opt-in). With v3 the executor turns rendered text into ADF for rich-text fields and turns ADF back into text when reading. See "Key Design Decisions". |
| 5 | Code reuse | Copy the required platform-agnostic code from the ServiceNow executor into this repository. No shared library for now. |
| — | Other proposals | Accepted: `PENDING` without an issue key for the Automation kind; reporter set to the requester configurable and off by default (the Issue API's equivalent of JSM's on-behalf-of); a requester who is not a JSM customer causes an `ERROR`; Data Center supported with a PAT (8.14+) as the baseline and DC service accounts as an option. |

---

## Domain Concept Identification

### Existing Concepts (from codebase)

**In this repository**: an untouched copy of `odm-platform-adapter-marketplace-executor-starter` (Quarkus 3.40.1, Java 21, stateless, no database, single `Initial commit`). The code is identical to the baseline the ServiceNow analysis started from, so those findings hold unchanged:

- **MarketplaceRequest**: the access request forwarded by the Marketplace Service. It carries `identifier`, `name`, provider (data product FQN and port FQNs), consumer, requester, free-form `properties`, `startDate` and `endDate`.
  - It is the source data for the Jira issue content and for the request filter.
  - Its `identifier` is the Marketplace Service's **internal UUID**, not the business identifier.
- **MarketplaceOperation**: `MARKETPLACE_SUBSCRIBE` and `MARKETPLACE_UNSUBSCRIBE`.
- **MarketplaceResult / MarketplaceRequestStatus**: `PENDING`, `GRANTED`, `DENIED`, `REVOKED` and `ERROR`, plus a message, the provider and `createdAt`.
- **ProcessMarketplaceRequest**: validates the invariants, then asynchronously calls `MarketplaceAccessOutboundPort.grantAccess/revokeAccess` and immediately reports `GRANTED` or `REVOKED`.
  - This synchronous-completion model **does not fit Jira**: completion happens later, when a person moves the issue through its workflow.
- **MarketplaceAccessOutboundPort / MarketplaceAccessOutboundPortImpl**: the documented extension point, still a TODO stub. It is where the "open an issue" behaviour belongs.
- **MarketplaceResultOutboundPort**: posts to `{marketplace}/api/v1/pp/marketplace/requests/{uuid}/results` after a hard-coded 2-second sleep, and swallows failures.
- **MarketplaceServiceConfig**: the `@ConfigMapping` precedent for typed YAML configuration.
- **RestUtils / AuthenticatedRestUtilsTemplate / Oauth2**: the template's HTTP client, with OAuth2 client-credentials support (token request, caching until `expires_in`). It is reusable for Jira Cloud and Jira DC service-account authentication, and for static-header modes (basic, bearer PAT).

**In the sibling ServiceNow executor** (`odm-platform-adapter-marketplace-executor-servicenow`, branch `BDMD-5340-servicenow-executor`, implementation in progress and uncommitted). This is the **reference design** for the Jira executor. Its canvas settled these platform-agnostic decisions:

- **Request filter** (regex, full match) evaluated synchronously. A non-matching request raises `RequestNotHandledException` → **409**, which the Marketplace Service counts as "not handled".
- **Two-phase result**: `PENDING` (best effort) or `ERROR` after creation; the final status comes from the webhook only.
- **Inbound webhook** with a **fixed, executor-defined contract** (`correlationId` plus a map of ticket fields). The use case:
  1. looks up the request in the Marketplace Service
  2. re-applies the filter
  3. checks first-final-wins against the executor-response history
  4. evaluates the ordered outcome rules (outcome cause → status)
  5. composes the message
  6. posts the result synchronously, answering 500 on failure so the caller can retry

  Every "ignore" case answers `200 IGNORED` with a reason.
- **Typed interface kinds** (strategy per kind) with built-in defaults, overridable through JSON Pointers. The executor always injects the correlation value after rendering, so templates cannot drop it.
- **Qute** parsed at runtime, maps-only data model, `strict-rendering=false`, no HTML escaping, startup validation.
- **Bounded retry** on result delivery (404, 5xx and I/O errors) replacing the 2-second sleep.
- **Closing-user inclusion** switch that also strips the field from the template model when disabled.
- Java base package and `template.yaml` kept unchanged (template-sync); only the Maven coordinates and names change.

**In blindata-agent** (branch `BDMD-5340-marketplace-executor-filters`, uncommitted, companion canvas `spdd/prompt/BDMD-5340-202610161527-[Feat]-filter-odm-marketplace-executor-auth.md`):

- New `OdmService.MARKETPLACE_EXECUTOR("marketplace-executor")` with `MarketplaceExecutorServiceAuthFilter`, a chain of `MarketplaceExecutorAuthHandlerTicketEvents` followed by `MarketplaceExecutorAuthHandlerDenyAll`.
  - It allows **only** `POST /**/api/v1/up/marketplace-executor/servicenow/ticket-events` for MARKETPLACE_EDITOR. **The path is hard-wired to ServiceNow.**
  - There is no admin bypass inside the filter. This differs from the ServiceNow analysis, which proposed a MARKETPLACE_ADMIN bypass; the canvas dropped it.
- **Routing is one URL per service name**:
  - `ODMProxyService` resolves the filter by exact `OdmService.valueOfServiceNameAndVersion(serviceName, version)`. A service name without a filter needs `DATAOPS_ADMIN`.
  - The forwarder sends the request to `servicesURLs[serviceName]` + the rest of the path.
  - So one ODM proxy configuration can reach **only one** `marketplace-executor` instance.
- **Caller authentication is header-based only** (`X-BD-User` / `X-BD-ApiKey`, or an `Authorization` header passed through to Blindata for validation). There is no HMAC-signature or URL-token authentication.
- All caller headers are forwarded unchanged to the backend.

**In the Marketplace Service** (`odm-platform-pp-marketplace-server`, `master`, unchanged since the ServiceNow analysis):

- The access request is submitted **inside the DB transaction** to every active executor. A non-2xx answer means "not handled", and if no executor handles it the transaction rolls back.
- `GET /requests/{uuid}`, and executor-response search by `accessRequestUuid` and `status`.
- `ExecutorResponse` is append-only, **without executor attribution**.
- No authentication anywhere.

**In the Blindata ecosystem**: unchanged from the ServiceNow analysis.

- The Blindata observer ignores `PENDING`, maps the other statuses to port grant statuses, and copies the `message` into the user-visible `grantMessage`.
- blindata-api webhooks are Mustache-templated, flattened, and fire-and-forget.
- The email sender sets the precedent for user-editable templates.

### New Concepts Required

- **Jira Issue (Fulfilment Resource)**: the issue, or service request, created on Jira for each marketplace request and operation. It covers **all ports of the request at once**, and its workflow completion determines the result.
- **Creation Interface**: the Jira inbound interface a profile uses ("all interfaces", inherited). The realistic server-to-server interfaces are:
  - **Issue API** (Jira platform REST, `/rest/api/2/issue`):
    - Any project and issue type: Task, Story, a custom "Access Request" type, and so on.
    - Same payload model on Cloud and DC, because v2 takes plain or wiki text and v3 would require ADF.
  - **Service Management Request API** (`/rest/servicedeskapi/request`):
    - A JSM service project, request type and `requestFieldValues`.
    - Raised **on behalf of the requester** (`raiseOnBehalfOf`), with approvals and SLAs handled by the customer's JSM configuration.
    - This is the Jira counterpart of the ServiceNow Service Catalog.
  - **Automation Incoming Webhook**: a Jira Automation rule triggered by "Incoming webhook", authenticated by a per-rule secret token.
    - The customer's rule creates whatever it needs (issue, sub-tasks, links).
    - This is the counterpart of ServiceNow Scripted REST: customer-defined creation logic.
    - **It returns no issue key synchronously.**

  The interfaces differ in request shape, in where the correlation can be stored, in what identifies the created issue in the response, and in how the completion notification is wired.
- **Issue Profile (per-operation configuration)**: one per operation (inherited). It defines the interface, the target (project and issue type, service desk and request type, or automation webhook URL), the field map built from MarketplaceRequest data, the correlation target, the response-extraction rules and the outcome rules.
- **Issue Reference**: the issue key (for example `ACC-123`) and id from the creation response, plus a **browse link** `{siteUrl}/browse/{key}` and, for JSM, an optional customer-portal link.
  - On Cloud with OAuth, the **API base URL** (`https://api.atlassian.com/ex/jira/{cloudId}`) differs from the **site URL** used for links, so both are configured.
- **Correlation Reference**: the marketplace UUID written into the issue and returned by the completion notification.
  - Default target: a dedicated **single-line text custom field** (configurable id, for example `customfield_10050`).
  - Optional extra: a **label** (`bdmp-<uuid>`) for exact JQL lookup.
  - The UUID is always repeated in the description.
- **Request Filter**: inherited unchanged. The criteria are operation, provider FQN, port FQNs, consumer type and identifier, requester, and `properties`. Patterns are full-match regexes, every requested port must match, and filters are exclusive between executors.
- **Requester Resolution**: the marketplace requester (type `user`, identifier = email) resolved to a Jira user.
  - **Cloud**: the `accountId`. Email visibility is subject to user privacy settings, and lookup needs the *Browse users and groups* permission.
  - **DC**: the username.
  - It is **mandatory for the JSM interface** (`raiseOnBehalfOf`, the counterpart of `requested_for`). The requester must also be a customer of the service project.
  - It is **optional for the Issue API** (set the reporter, which needs *Modify Reporter*).
- **Completion Notification (inbound callback)**: Jira tells the executor that an issue has changed. There are **two channels** (product-owner decision), both normalised into the same internal issue event, so the outcome logic is shared:
  - **Automation channel**: `POST …/webhooks/jira/issue-events`, with the same fixed contract as ServiceNow.
    - It is sent by a **Jira Automation rule** ("When issue transitioned" → "Send web request").
    - The rule carries the Blindata credentials as **hidden custom headers** and builds the body from smart values: correlation field, issue key, status, status category, resolution, last comment, actor.
  - **Native webhook channel**: `POST …/webhooks/jira/native-events`, accepting the **native Jira webhook payload** (`webhookEvent`, `user`, `issue`, `changelog`).
    - The executor extracts the correlation from the configured custom field, the status category, status, resolution and actor from the payload.
    - It treats only events whose changelog contains a status transition as candidates.
    - When the payload does not include the last comment, the executor reads it through the Jira API with the integration identity.
    - Jira retries native deliveries on 5xx, which fits the "answer 500 so the caller retries" rule.
  - Both channels are reached **through the blindata-agent proxy**. The executor does no authentication of its own.
- **Webhook Auth Gateway** (native channel only, customer-provided): native Jira webhooks cannot send custom headers, so they cannot authenticate to the agent today.
  - A customer-side gateway closes the gap until a future agent extension exists. For example, a serverless function or API gateway verifies Jira's `X-Hub-Signature` HMAC (Cloud), or a secret URL token, then adds the Blindata application-user headers and forwards to the agent.
  - The gateway is documented, not implemented. The future agent extension (signature-based caller authentication) is out of BDMD-5340.
- **Outcome Mapping Rules**: inherited model (ordered rules per operation → outcome cause → status, restricted by operation), with Jira-specific default conditions:
  - Status category `done` together with a resolution in the configured "fulfilled" set (for example `Done`, `Fixed`) → FULFILLED.
  - A resolution in the "refused" set (for example `Won't Do`, `Declined`, `Rejected`), or the JSM approval-decline status → REFUSED.
  - Anything else is ignored.
- **Result Message Composition**: inherited, template-driven.
  - Jira inputs: issue key and link, status, resolution, last comment as close notes, and the actor display name as closing user (inclusion configurable, default on).
  - On Cloud the actor's email is usually hidden, so the display name is the reliable value.
- **Jira Connection**: the site URL, the API base URL and the credentials. The best-practice auth mode is per deployment:
  - **Cloud (default)**: an **Atlassian service account with OAuth 2.0 client credentials**.
    - Token from `https://auth.atlassian.com/oauth/token`, valid for 60 minutes with no refresh token. Calls go to `api.atlassian.com/ex/jira/{cloudId}` with a Bearer token.
    - Service accounts take no license seat, and 5 are free per organization.
  - **Cloud (alternative)**: a service-account or integration-user **scoped API token** with basic auth (`email:token`).
    - Tokens expire after at most 365 days, so they need rotation.
  - **Data Center (default)**: a **Personal Access Token** (Bearer) of a dedicated integration user (Jira 8.14+).
  - **Data Center 11.1+ (alternative)**: a **DC service account** with OAuth 2.0 client credentials (`{base}/rest/oauth2/latest/token`).
- **Agent Webhook Route**: a `servicesURLs` entry, Blindata application-user credentials held in the Jira Automation rule, and an authorization rule. It is configured on the Blindata side.
- **Marketplace Executor Auth Filter (generalised, product-owner decision)**: the in-progress agent filter becomes a filter for **all marketplace executors**.
  - It is matched by service-name prefix (`marketplace-executor`, `marketplace-executor-<suffix>`), so each executor instance has its own `servicesURLs` key. ServiceNow and Jira can run side by side, as can several Jira executors split by request filter.
  - It allows one **general webhooks namespace**, `POST …/api/v1/up/marketplace-executor/webhooks/**`, for MARKETPLACE_EDITOR, so adding an executor needs no agent code change (product-owner decision). Everything else is denied.
  - Every executor places its callbacks there: ServiceNow `…/webhooks/servicenow/ticket-events`, Jira `…/webhooks/jira/issue-events` and `…/webhooks/jira/native-events`.
  - This changes the companion agent canvas.
- *(Nice-to-have)* **Inbound Source / Result Sink abstraction**: inherited; keeps a later Blindata-direct channel possible.

### Key Business Rules

All BDMD-5340 business rules are inherited unchanged. The Jira-specific reading of each:

- **Correlation is mandatory**: every created issue must carry the marketplace UUID in a place the Automation rule can return verbatim. A notification without a resolvable UUID produces no result. *Governs: Issue, Correlation Reference, Completion Notification.*
  - For the **Automation Incoming Webhook** interface, the customer's rule must copy the UUID onto the issue it creates. The README states this as a hard requirement of that interface.
- **The UUID, not the business identifier, is the key**: the business identifier may appear in the summary for humans only.
- **Request filtering happens before any side effect**: a non-matching request is declined with **409**, so no issue is opened and no result is posted. The filter is evaluated on the whole request.
- **No partial grants**: one issue covers all ports, and every result reports all requested ports.
  - Sub-tasks per port, which some Jira teams favour, are allowed only as the customer's internal breakdown. The **parent** issue's completion is the only outcome signal.
- **Two-phase result**:
  - **Issue API and JSM**: `PENDING` with the issue key after successful creation, `ERROR` on creation failure.
  - **Automation Incoming Webhook**: `PENDING` *without* an issue key ("submitted to Jira Automation rule"). The key appears in the final message.
- **Operation-specific status taxonomy** (inherited):

  | Operation | Issue opened | Fulfilled on Jira | Refused on Jira | Technical failure |
  |---|---|---|---|---|
  | `MARKETPLACE_SUBSCRIBE` | `PENDING` | `GRANTED` | `DENIED` | `ERROR` |
  | `MARKETPLACE_UNSUBSCRIBE` | `PENDING` | `REVOKED` | `ERROR` (message says the revoke was refused on Jira, with resolution, comment, issue and actor) | `ERROR` (technical cause) |

  A configuration mapping an operation to a status outside its allowed set is invalid.
- **Descriptive messages**: outcome, issue key and link, resolution, last comment and actor. `ERROR` messages distinguish a Jira-side refusal from a platform failure.
- **First final status wins**: reopen-and-close cycles are common in Jira, and every such transition fires the rule again. Later notifications are acknowledged and ignored.
- **Non-terminal transitions are not results**: transitions into `new` or `indeterminate` categories, or into a `done` status whose resolution matches no rule, are acknowledged and ignored.
- **Provider consistency**: the provider and ports come from the Marketplace Service lookup, never from the notification.
- **Only authenticated callers may complete requests**:
  - Authentication is delegated to blindata-agent, and the executor must be reachable only through the agent (network isolation).
  - **Native Jira webhooks cannot satisfy this rule on their own**: they send no custom headers, and the agent authenticates only by headers. The native channel is therefore supported only behind a Webhook Auth Gateway (or a future agent extension). The README states this as a hard requirement of that channel.
- **Every outbound Jira call uses the configured integration identity**:
  - No per-user credentials.
  - The identity needs, per profile:
    - Issue API: *Create issues* on the target projects, plus *Edit issues* if labels or properties are written after creation
    - JSM: agent role on the service desk (needed for `raiseOnBehalfOf`)
    - requester resolution: *Browse users and groups*
    - optionally *Modify Reporter*
- **Request invariants stay as they are** (required fields, `startDate <= endDate`).

---

## Strategic Approach

### Solution Direction

Mirror the ServiceNow executor's design (canvas `BDMD-5340-202610161509`) and replace only the target-tool adapter. The `spdd/norms/` conventions apply unchanged.

1. **Outbound leg**:
   - `POST /requests` → validate → filter (409 decline) → `202`.
   - Asynchronously: select the operation profile, render the Qute field map, resolve the requester when the interface needs it, and call the interface kind:
     - Issue API → `/rest/api/2/issue`
     - JSM → `/rest/servicedeskapi/request`
     - Automation → the incoming-webhook URL with its secret token
   - Extract the issue reference and report `PENDING` (best effort) or `ERROR`.
2. **Inbound leg** (two channels, one use case):
   - **Automation channel**:
     - A Jira Automation rule fires "When issue transitioned", optionally with a JQL condition on the correlation field being non-empty.
     - The rule's "Send web request" calls the **blindata-agent proxy** with hidden `X-BD-User` / `X-BD-ApiKey` headers and a body following the executor's fixed contract, built from smart values.
     - The agent authenticates, authorizes and forwards to `POST /api/v1/up/marketplace-executor/webhooks/jira/issue-events`.
   - **Native webhook channel**:
     - A Jira admin webhook (`jira:issue_updated`, with a JQL filter on the profile projects and a non-empty correlation field) calls the customer's **Webhook Auth Gateway**.
     - The gateway verifies the call and adds the Blindata headers, then the agent forwards to `POST /api/v1/up/marketplace-executor/webhooks/jira/native-events`.
     - The executor maps the native payload to the internal issue event: correlation, status category, status, resolution and actor, plus the last comment read from Jira when it is missing.
   - Both channels then follow the ServiceNow sequence: lookup → re-filter → first-final-wins → outcome rules → message → synchronous post.
3. **Configuration**:
   - Typed `@ConfigMapping(prefix = "jira")` with a deployment type (`CLOUD` or `DATA_CENTER`), site URL, API base URL, **REST API version** (`2` default, `3` Cloud only), auth mode and credentials, the filter, per-operation profiles (with rich-text field flags for v3), outcome rules and message templates.
   - Inline or `file:` Qute templates.
   - Startup validation, including auth-mode/deployment compatibility (for example PAT only on DC, `api.atlassian.com` base URL with OAuth on Cloud).
4. **Documentation**:
   - Rewrite the README from "starter" to "Jira executor".
   - **One basic scenario per creation interface** (Issue API, JSM Request, Automation Incoming Webhook). Each scenario covers:
     - base executor config with a SUBSCRIBE and an UNSUBSCRIBE profile
     - the minimal Jira objects: project or service desk, issue or request type, correlation custom field on the screens, workflow statuses and resolutions
     - the integration identity and its permissions
     - the **completion Automation rule**, with a copy-paste body and hidden headers
     - the **native webhook alternative**: admin webhook registration with a JQL filter and secret, and the Webhook Auth Gateway requirement with a reference sketch (signature check + header injection)
     - the agent route
     - an example run
   - Cloud and DC differences are called out inline: auth, user identity, API paths, Automation availability.

The executor stays **stateless**.

### Key Design Decisions

- **Shared core with the ServiceNow executor vs. replicate the pattern** → **copy the required code from the ServiceNow executor** (product-owner decision).
  - The copied code is platform-agnostic:
    - the request filter, outcome rules and result message services
    - the Qute renderer
    - the marketplace service client with bounded retry
    - the decline exception and the domain types for registered requests and ticket events
    - both use cases
    - the `AuthenticatedRestUtilsTemplate` scope fix
  - Jira-specific parts are written new: configuration, the Jira client, the interface-kind creators, requester resolution, the ADF conversion, the native-payload mapper and the REST resources.
  - Same package layout, contracts and config shape. Template-sync keeps working, and both executors ship in BDMD-5340.
  - The ServiceNow code is uncommitted and may still change in review. The copy should come from its reviewed state, and later fixes must be ported by hand.
  - A shared `marketplace-executor-ticketing-core` library stays a follow-up.
- **Which creation interfaces make up "all interfaces"**:
  - Jira has no direct counterpart of ServiceNow Import Set.
  - Forms and email channels are not server-to-server.
  - Bulk create adds nothing for one issue per request.
  - → **Three typed kinds**: `ISSUE`, `SERVICE_DESK_REQUEST`, `AUTOMATION_WEBHOOK`. Jira Software, Work Management and Service Management customers are all covered, and customers with bespoke creation logic use the Automation kind.
- **REST v2 vs v3** → **support both**, chosen per connection: **v2 by default** (Cloud and DC), **v3 opt-in on Cloud** (product-owner question, answered by supporting both).
  - The only real difference is the text format of rich-text fields (description, environment, textarea custom fields):
    - v2 accepts and returns plain or wiki text.
    - v3 accepts and returns only **ADF** (a JSON document tree).
  - Supporting v3 therefore means two conversions, and neither touches the templates, so users always write plain-text templates:
    - **Writing**: the executor turns the rendered text of each **rich-text field** into a minimal ADF document (paragraphs and line breaks).
      - The description is flagged as rich text by default. Textarea custom fields are flagged in the profile, because the executor cannot know a custom field's type without reading create metadata.
      - For JSM the setting drives `isAdfRequest`.
    - **Reading**: ADF content read back (the last comment) is flattened to plain text for result messages.
  - v3 on DC is rejected at startup (DC has v2 only).
  - Why v2 stays the default: it is the one setting that works on every supported deployment, and it avoids the conversion layer. v3 is the protection against a future v2 deprecation on Cloud.
- **Where to store the correlation UUID**:
  - (a) Issue entity property: invisible in the UI. Setting it at creation is unreliable (validation errors are reported), it needs a second call otherwise, and it is awkward in Automation smart values.
  - (b) **Text custom field**: visible to fulfillers, present in webhook and Automation payloads, works on Cloud and DC, and is settable through both the Issue and JSM APIs. It must be on the create screen, or on the request type for JSM.
  - (c) Label: exact-match JQL, but noisy and easy for users to edit.
  - → **(b) by default** (configurable field id), with **(c) optional**, and the UUID always repeated in the description.
  - For JSM the field must be on the request type, possibly as a hidden field with a preset value; if it cannot be, the executor sets it with a follow-up edit. The README states this.
- **Completion notification channel**:
  - (a) Native admin webhooks: free, retried up to 5 times, HMAC-signed on Cloud. But they **cannot send the agent's auth headers**, DC has no signing, and the agent would need a new signature-based authentication mode.
  - (b) **Jira Automation "Send web request"**: supports hidden custom headers and a fully templated body, works on Cloud and DC, and needs no agent auth change. But it consumes Automation usage (one execution per transition, within plan allowances), has a ~30 s timeout and **no automatic retry**.
  - (c) Polling: excluded by the requirement.
  - → **(a) and (b) both supported** (product-owner decision).
    - **(b) Automation** is the channel that works end to end with today's agent, so it is the default in the README scenarios.
    - **(a) Native webhooks** suit customers who want Jira's own retries, signing and no Automation usage. They need a Webhook Auth Gateway until the agent gains signature-based authentication (future extension, out of BDMD-5340).
- **Webhook contracts** → **two endpoints, one internal event**:
  - The Automation channel uses the **same fixed executor-defined contract as ServiceNow** (`correlationId` plus a flat `fields` map of strings).
  - The native channel accepts the **native Jira payload**, and a mapper extracts the same internal event from it. The correlation field id and the extraction paths are configurable, with defaults per deployment type.
  - A separate endpoint per channel keeps each contract strict and lets the agent allow-list name both. The use case, outcome-rule engine and tests stay shared.
  - The mapper keeps only the fields the rules and messages need, so full issue data (personal data) is not passed on.
- **Outcome determination defaults**:
  - Status names and resolutions are per project and workflow, so the default rules key on **status category `done`** plus **resolution name** sets, and all of it is configurable.
  - JSM approval decline shows up only as a workflow transition, so the default JSM rules treat the "Declined" status or resolution as REFUSED.
  - Reading the JSM approvals API to confirm the decision is not needed for the MVP.
- **Enforcing first-final-wins** → **inherited**: the marketplace response-history check, plus a README-documented rule condition (fire only on transition into a `done`-category status). Exclusive filters are the operating rule.
- **Initial `PENDING`** → **sent**, with the issue key (or "submitted to Automation rule" for the Automation kind).
- **Outbound authentication** → per deployment:
  - Cloud default: **service-account OAuth 2.0 client credentials**. It is the best practice for user-less integrations: short-lived tokens, no license seat, org-admin managed. It reuses `AuthenticatedRestUtilsTemplate` (the token request must be checked for format and the `scope` handling, as for ServiceNow).
  - Cloud alternative: **scoped API token** (basic).
  - DC default: **PAT** (Bearer).
  - DC 11.1+ alternative: **DC service account** (client credentials).
  - Secrets come only from env or mounted secrets.
- **Agent routing and authorization for several executors** (affects the companion agent canvas, still uncommitted):
  - (a) Widen the existing filter's allow-list to also accept `…/webhooks/jira/issue-events`. Simple, but it still routes the single `marketplace-executor` name to **one** URL, so ServiceNow and Jira (or two Jira executors) cannot coexist behind one proxy configuration.
  - (b) One `OdmService` entry and filter per executor type (`marketplace-executor-servicenow`, `marketplace-executor-jira`). Coexistence works, but it is still one instance per type and needs an agent release for every new executor.
  - (c) **One generic executor filter matched by service-name prefix** (`marketplace-executor` and `marketplace-executor-<suffix>`). Its allow-list covers the webhook paths of every executor type (`servicenow/ticket-events`, `jira/issue-events`) and it denies everything else. Each instance gets its own `servicesURLs` key.
  - → **(c)**, confirmed by the product owner ("allow all marketplace-executors"). It supports any number and mix of executors with one filter.
    - It requires replacing the exact-match lookup in `valueOfServiceNameAndVersion` for this family.
    - Instead of a per-executor allow-list, it exposes one **webhooks namespace** (`…/marketplace-executor/webhooks/**`), per the product owner ("use a general marketplace-executor path, so we do not need to modify agent code each time an executor is added"). The ServiceNow endpoint moves to `…/webhooks/servicenow/ticket-events`.
    - It requires updating the agent canvas through `/spdd-prompt-update` before the agent branch is committed.
- **Profile granularity** → one per operation (inherited). Project, issue type, assignee or component routing goes in templates driven by request data and `properties`.
- **Repository identity** → as for ServiceNow: rename the artifact and names to Jira, and keep the base package and `template.yaml`.

### Nice-to-have: Blindata-direct mode (blindata-api webhooks ↔ executor ↔ Jira)

The ServiceNow findings apply unchanged:

- The ODM path already reaches Blindata through the observer.
- Direct `blindata-api ↔ Jira` would put status mapping and a Blindata API key into Jira Automation rules. Blindata webhooks are flattened and not retried.
- The executor-mediated mode is useful only for Blindata-without-ODM customers.

There is one Jira-specific point. A direct `blindata-api webhook → Jira` path is *more* tempting than with ServiceNow, because Jira's Automation incoming webhook accepts arbitrary JSON. It still loses request filtering, the operation taxonomy, all-ports reporting and first-final-wins.

→ **Defer**, and keep the core channel-agnostic. The follow-up prerequisites are the same as for ServiceNow: verify the blindata-api webhook payload, define the trigger transitions, and provision a MARKETPLACE_EDITOR application user.

### Out of Scope: Automatic revoke on access expiry (`endDate`)

The principle is the same as for ServiceNow: **the lifecycle owner (blindata-api, or the Marketplace Service in standalone ODM) issues a normal `MARKETPLACE_UNSUBSCRIBE`** when access expires, and the Jira executor handles it like any other revoke.

**Authentication layers:**

| # | Hop | Current authentication | Implication for automatic revoke |
|---|-----|------------------------|----------------------------------|
| L1 | Marketplace Service → executor | none | unchanged |
| L2 | Executor → Jira | service-account OAuth client credentials or scoped API token (Cloud); PAT or DC service account (DC) | unchanged. Revoke issues are created by the same integration identity. Long-lived credentials need rotation (API tokens at most 1 year), which matters for revokes scheduled months ahead. |
| L3 | Jira Automation → blindata-agent → executor | Blindata application-user headers stored hidden in the Automation rule, plus the agent executor filter | unchanged |
| L4 | Executor → Marketplace Service | none | an executor-initiated submission would be unauthenticated |
| L5 | Blindata user → agent → ODM marketplace | user-bound roles plus a marketplace responsibility on the provider | an unattended submission needs a service identity holding responsibility on every provider |
| L6 | Client → blindata-api | API key, JWT or OAuth2 | system revokes need a MARKETPLACE_EDITOR application user, audited as "system" |

**Options:**

- **(A) Lifecycle owner schedules expiry**: recommended, as for ServiceNow.
- **(B) Executor schedules expiry**: rejected (state, no `endDate` search, lifecycle in an adapter).
- **(C) Jira schedules expiry**:
  - A scheduled Automation rule with JQL on a "Access end date" field could open the revoke issue and call the executor. Jira makes this easier than ServiceNow does: `endDate` goes into a date custom field, and a scheduled rule triggers on it.
  - It is still rejected as the default. The marketplace would need a new unsubscribe request, which pushes marketplace credentials (L4, L5 or L6) into Jira, and every Automation run consumes allowance.

**What BDMD-5340 should do now for Jira:**

- Include `startDate` and `endDate` in the default templates, optionally mapped to date custom fields.
- Keep unsubscribe independent of the subscribe issue (no issue links required).

### Alternatives Considered

- **Keep the starter's synchronous model**: rejected, as for ServiceNow.
- **A stateful executor**: rejected. The correlation field plus the marketplace lookup are enough.
- **Accept-and-ignore for filtered-out requests**: rejected (409 decline, inherited).
- **Automation-only completion** (the first-round proposal): superseded. The product owner asked for native webhooks too.
- **The Automation channel sending the "Issue data (Jira format)" body** instead of the fixed contract: rejected. The native channel already covers the Jira payload shape, and a third contract would add nothing.
- **Executor-side HMAC verification of native webhooks**: rejected. Inbound auth is delegated to the agent or gateway (inherited decision), and the executor would also need the webhook secret.
- **User-written ADF templates for v3**: rejected. ADF is hostile to users; the executor converts plain text instead.
- **v3 only**: rejected, because DC has v2 only.
- **Entity properties as the correlation store**: rejected as the default (see Key Design Decisions).
- **Atlassian Forge or Connect app**: rejected. It gives proper webhooks and app-scoped storage, but needs Marketplace distribution or per-customer installation, is Cloud-only, and creates a second runtime to maintain.
- **OAuth 2.0 3LO on Cloud**: rejected, because it binds the integration to a human user's consent and refresh tokens.
- **Sub-task per port with per-port outcomes**: rejected (no partial grants).
- **A shared library extracted now**: deferred. The code is copied instead (product-owner decision).

---

## Risk & Gap Analysis

### Requirement Ambiguities

All Jira-specific questions are resolved (see "Requirement Clarifications (Jira, first round)"):

| Topic | Resolution |
|---|---|
| "All interfaces" on Jira | `ISSUE`, `SERVICE_DESK_REQUEST` and `AUTOMATION_WEBHOOK` confirmed. |
| Automation kind without an issue key | Accepted: `PENDING` without a key, and the key appears in the final message. |
| Completion channel | Both Automation "Send web request" and native webhooks. The native channel needs a Webhook Auth Gateway until a future agent extension. |
| REST API version | Both: v2 by default, v3 opt-in on Cloud with ADF conversion. |
| Requester on the Issue API | Setting the reporter to the requester is configurable, **default off**. Resolution is mandatory only for JSM `raiseOnBehalfOf`. |
| Requester not a JSM customer | `ERROR` with a descriptive message; customers are never added automatically. |
| Agent filter | Generic filter for all marketplace executors, routed by service-name prefix. |
| Code reuse | Copy the platform-agnostic code from the ServiceNow executor. |
| Data Center | PAT (8.14+) baseline; DC 11.1+ service accounts optional. |

Residual items (not blocking, handled in design):
- **Native webhook payload fidelity**: whether the Cloud webhook `issue` representation includes the comment field, and whether the payload's text fields are plain text or ADF. To be checked against a real payload during the Canvas phase. The mapper reads the last comment through the API when it is missing.
- **Gateway reference**: the README gives a reference sketch of the Webhook Auth Gateway, not a supported component.

### Edge Cases

- **Notification for an unknown UUID, a missing correlation field, or a request this executor declined** → `200 IGNORED`, as for ServiceNow.
- **The correlation field is not on the create screen or the request type** → Jira rejects the create (400, "field cannot be set") → `ERROR` naming the field. Startup cannot detect this without a create-metadata check; a Canvas-phase option is a warm-up check through `/issue/createmeta/{project}/issuetypes/{id}`.
- **JSM request type hides the correlation field** → the create may succeed but drop the value. The executor must verify, or set it with a follow-up edit, otherwise the notification will be uncorrelated.
- **The Automation rule copies the wrong field, or none** (Automation kind) → notifications arrive without a UUID and the request stays `PENDING`. It is logged and documented.
- **Reopen and re-close**, or a status transition back and forth inside `done` → first-final-wins; later events are ignored.
- **Transition into `done` without a resolution** (workflows without a resolution screen) → no rule matches → ignored, so the request stays `PENDING`. The default rules and the README must cover "resolution unset".
- **JSM approval with several approvers** → the status changes only when every approver has responded; intermediate events are ignored.
- **Issue moved to another project, or its key changes** → the key in the final message may differ from the one in `PENDING`. Correlation is unaffected, because it uses the field.
- **Requester email hidden on Cloud or unmatched**, or several users matching → `ERROR` for JSM, and the reporter is not set for the Issue API.
- **Text fields**: values with wiki markup characters (`{`, `|`, `*`) or newlines, and `summary` limited to 255 characters, so rendering must truncate the summary.
- **Jira 429 rate limiting** (points-based on Cloud for OAuth apps; burst limits per tenant) → honour `Retry-After` with a bounded retry on creation, then `ERROR`.
- **Credential expiry**: API tokens expire after at most 365 days, and PATs optionally. A 401 → `ERROR` naming the auth mode, never the secret.
- **Automation rule fails or times out** (~30 s) while the marketplace is slow → no retry from Jira. The executor's own bounded retry on the marketplace post must fit within that timeout.
- **Native webhook noise**: `jira:issue_updated` fires on *every* edit, not only on transitions. Events without a status item in the changelog are ignored before any marketplace lookup, and the README recommends a tight JQL filter.
- **Native webhook retries and duplicates**: Jira retries up to 5 times on 5xx and timeouts, and `X-Atlassian-Webhook-Identifier` stays the same across retries. First-final-wins absorbs duplicates. A retry arriving after another final result has been posted is ignored.
- **Both channels configured for the same project** → two notifications per transition. The second is ignored by first-final-wins. The README advises choosing one channel per project.
- **Native webhook reaches the agent without a gateway** → the agent rejects it (401 or 403) and Jira retries, then gives up. The request stays `PENDING`. The README highlights this misconfiguration.
- **v3 rich-text field not flagged in the profile** → Jira rejects the plain-text value (400) → `ERROR` naming the field.
- **All executors decline**, **same identifier with both operations**, **unsubscribe while the subscribe issue is open**: as for ServiceNow.

### Technical Risks

- **Synchronous-success assumption in the starter**: same mitigation as for ServiceNow (rework plus a new callback use case).
- **Divergence between the two executors**:
  - The Jira design mirrors a ServiceNow implementation that is still uncommitted and may change during review.
  - *Mitigation:* derive the Jira canvas from the ServiceNow canvas, keep contracts and config shapes aligned, and plan the shared-core extraction.
- **Agent change ripples into the ServiceNow work**: generalising the agent filter alters the companion canvas and possibly the ServiceNow README's agent route (service name). *Mitigation:* update the agent canvas once, for both executors, before the agent branch is committed.
- **No retry on the Automation channel**:
  - Automation "Send web request" does not retry, so a lost delivery means `PENDING` forever.
  - *Mitigation:* the executor's internal bounded retry towards the marketplace, README guidance on the Automation audit log and on re-running the rule manually, and the native webhook channel (with Jira retries) as an alternative.
  - Reconciliation by JQL on the correlation label or field is a future option.
- **Native channel depends on a component outside BDMD-5340**:
  - Until the agent supports signature-based authentication, every native-channel deployment needs a customer-built gateway that holds Blindata credentials and the Jira webhook secret. Its quality and security are outside our control.
  - *Mitigation:* a README reference sketch with a security checklist (verify the signature, enforce TLS, keep credentials in a secret store, never log headers), and tracking the agent extension as a follow-up.
- **Native payload coupling**: the native mapper depends on Jira's webhook payload shape, which differs slightly between Cloud and DC (and between DC versions, for example the URL-variable syntax since Jira 10). *Mitigation:* configurable extraction paths with per-deployment defaults, and tests on recorded payloads.
- **ADF conversion (v3)**: plain text → ADF must handle empty values, newlines and very long text, and ADF → text must tolerate unknown node types. *Mitigation:* a minimal, lossy-but-safe converter (paragraphs and hard breaks out; text nodes only in), with unit tests on edge payloads.
- **Automation allowance consumption**: each relevant transition costs one rule execution under the pooled usage model. *Mitigation:*
  - a rule condition that fires only on transition into a `done`-category status, scoped to the profile projects
  - expected volume (two executions per access-request lifecycle) documented in the README
- **Jira REST v2 deprecation on Cloud**: not announced, but v3 is the strategic API. *Mitigation:* v3 is already supported as an opt-in, so switching is a configuration change.
- **Cloud API base URL vs site URL**: OAuth and scoped-token calls go to `api.atlassian.com/ex/jira/{cloudId}`, while links use the site URL. *Mitigation:* separate config keys, validated together; README instructions to obtain the cloudId.
- **Atlassian OAuth token request format and scopes**: the service-account token endpoint and scope semantics differ from ServiceNow. `AuthenticatedRestUtilsTemplate` compatibility (body encoding, `scope` omission) must be verified, as was done for ServiceNow.
- **Permission surface of the integration identity**: create, edit, agent role on the service desk, browse users, modify reporter. A missing grant shows up only at runtime. *Mitigation:* per-scenario permission tables in the README, and descriptive 401, 403 and 400 messages that reproduce Jira's `errors` map.
- **Privacy on Cloud**: hidden emails limit requester resolution, and actor display names and comments flow to the marketplace and Blindata. *Mitigation:* the configurable closing-user switch, and documented behaviour when an email is hidden.
- **Executor webhook without its own auth**, **agent forwards credentials**, **ServiceNow-equivalent reachability of the agent from Jira Cloud** (Jira Cloud egress must reach the agent, and Automation only targets allowed ports such as 443 and 8443): network isolation, never logging headers, and README guidance on exposure and ports.
- **Unverified Jira facts** to be checked during the Canvas phase:
  - `raiseOnBehalfOf` permission details and accepted identifier (accountId vs email)
  - creation-time `properties`
  - exact-email user search on Cloud
  - the smart-value syntax for custom fields and the last comment
  - whether service-account OAuth traffic counts against the points quota
- **Native-image compatibility and Qute syntax**: as for ServiceNow (maps-only model, README primer). Jira users know Automation smart values (`{{…}}`), so the single-brace Qute syntax in executor templates must be clearly separated from the `{{…}}` smart values in the Automation rule body.
- **Testing without Jira**: port mocks, as for ServiceNow. Jira Cloud free sites allow manual end-to-end checks (5 service accounts are free).

### Acceptance Criteria Coverage

The ACs are the ServiceNow ACs, derived one-to-one from the requirement and the clarifications, read for Jira.

| AC# | Description | Addressable? | Gaps/Notes |
|-----|-------------|--------------|------------|
| 1 | Configurable **resource type** through **all Jira interfaces** | Yes | `ISSUE`, `SERVICE_DESK_REQUEST`, `AUTOMATION_WEBHOOK` as typed kinds. The scope of "all" needs confirmation (ambiguity table). |
| 2 | Configurable **content structure** | Yes | Qute field map rendered into plain or wiki text (v2), or converted to ADF for flagged rich-text fields (v3, Cloud). External template files and startup validation. |
| 3 | Adaptable to **different Jira processes** (per-operation configuration) | Yes | One profile per operation, plus interface, target, correlation field, outcome rules and messages. |
| 4 | The created resource **contains the marketplace request identifier** | Yes | UUID in a text custom field (default), optional label, and in the description. For the Automation kind it depends on the customer's rule. |
| 5 | **Task Opened** flow | Yes | `PENDING` with the issue key and browse link (no key for the Automation kind), or `ERROR`. |
| 6 | **Task Completed** flow: Jira notification → executor → marketplace updated | Yes | Two channels into one use case: Jira Automation "Send web request" via the agent (works end to end today), and native webhooks via a customer Webhook Auth Gateway and the agent. |
| 7 | Both flows valid **for every operation**, with the status taxonomy | Yes | Inherited taxonomy, enforced by configuration validation. |
| 8 | **No partial grants** | Yes | One issue per request. Sub-tasks are only an internal breakdown. |
| 9 | **Descriptive result messages** | Yes | Issue key and link, status, resolution, last comment, actor display name. |
| 10 | **First final status wins** | Yes | Marketplace history check; the reopen-heavy Jira workflow makes this rule important. |
| 11 | **Request filtering** for multi-executor deployments | Yes | Inherited regex filter, 409 decline. Coexistence behind one agent needs the generalised agent filter (AC 15). |
| 12 | **README** on configuring the service **and** Jira | Yes | Cloud and DC differences, the Automation rule, the agent route, permissions, filtering, taxonomy, templates, network requirement. |
| 12a | **README basic scenario per creation interface** | Yes | Three walkthroughs: Issue API, JSM Request, Automation Incoming Webhook. Each uses the Automation completion rule, with the native-webhook alternative and its gateway requirement documented. |
| 15 | **Webhook auth delegated to blindata-agent**, with the executor auth filter | Yes | Generic filter for all marketplace executors (prefix routing; one general webhooks namespace `…/marketplace-executor/webhooks/**`, so new executors need no agent change), delivered by updating the companion agent canvas. The native channel additionally needs a Webhook Auth Gateway until a future agent extension. |
| 20 | **Native Jira webhooks supported** in addition to Automation | Yes | Dedicated endpoint and native-payload mapper. Signature verification and header injection belong to the customer gateway (or a future agent extension). |
| 21 | **REST v2 and v3 both supported** | Yes | v2 default (Cloud and DC), v3 opt-in on Cloud with plain-text ↔ ADF conversion. v3 on DC is rejected at startup. |
| 22 | **Platform-agnostic code copied from the ServiceNow executor** | Yes | Copied from its reviewed state; later fixes are ported by hand until a shared library exists. |
| 16 | **Executor → Jira authenticated with the best-practice mechanism** | Yes | Cloud: service-account OAuth client credentials (scoped API token as alternative). DC: PAT (DC 11.1+ service account as alternative). |
| 17 | **Requester resolves to the marketplace requester** (the `requested_for` counterpart) | Yes | JSM `raiseOnBehalfOf` with accountId (Cloud) or username (DC). Optional reporter for the Issue API. Fails as `ERROR` if the requester is unresolvable or not a customer. |
| 18 | **Regex-based request filter** | Yes | Inherited unchanged. |
| 19 | **Configurable inclusion of the closing user** | Yes | Actor display name from the Automation rule. When disabled, it is removed from the template model. |
| 13 | *(Out of scope)* Automatic revoke on `endDate` | N/A | Direction and authentication layers documented. Dates included in the default templates. |
| 14 | *(Nice-to-have)* Blindata-direct mode | Partial (deferred) | As for ServiceNow: a channel-agnostic core now, Blindata adapters later. |
