# Scenario: your own Automation rule

[README](../../README.md) > **1. Scenario** > 2. Platform > 3. Test

The executor does not create the issue itself: it calls a **Jira Automation rule** with an *Incoming webhook* trigger, and **your rule creates the issue** (routing to different projects, extra fields, sub-tasks, assignment). Completing the issue with a resolution decides the outcome, as in the other scenarios. The executor never learns the key of the issue the rule creates, so the marketplace shows `PENDING` without an issue key until the issue is completed.

On this page you prepare Jira and write the executor configuration. **You will leave with:**

- the **client id** and **client secret** of a service account, and your site's **API URL**;
- the id of the **correlation custom field** (for example `customfield_10050`);
- the **webhook URL and secret** of two creation rules (subscribe and unsubscribe);
- the file **`config/application.yml`**.

You need Jira Cloud with **Automation**, an **organisation admin** (service account) and a **Jira admin** (custom fields, Automation). Incoming webhook triggers consume your plan's automation usage. Menu labels may differ slightly on your site.

## 1. Create the service account

The executor uses it only to read the last comment of a completed issue.

1. **admin.atlassian.com > Directory > Service accounts > Create service account**, with app access to Jira.
2. **Create credentials > OAuth 2.0**, with read scopes such as `read:jira-work` and `read:jira-user` (verify the scope names on your instance).
3. Copy the **client id** and **client secret**. Find your **cloudId** at `https://<site>.atlassian.net/_edge/tenant_info`; the API URL is `https://api.atlassian.com/ex/jira/<cloudId>`.
4. Add the account to the project(s) where the rules create issues (here `ACC`), with **Browse projects** and **View comments**.

Token check and alternatives: [Integration identity](../reference/jira-setup.md#1-integration-identity).

## 2. Create the correlation custom field

1. **Settings (cog) > Work items (Issues) > Custom fields > Create custom field**, type **Short text (single line)**, name *Marketplace request id*. Associate it with the screens of the target project, so the creation rule can set it.
2. Note its id (`customfield_10050`).

The project where your rule creates issues needs a workflow whose done transitions set a resolution (*Done* to grant or revoke, *Won't Do* to decline). See [the recommended setup](../reference/jira-setup.md#recommended-setup).

## 3. Create the two creation rules

One rule for subscribe and one for unsubscribe. For each:

1. **Project settings > Automation > Create rule** (or a global rule if it may create issues in several projects).
2. **Trigger**: *Incoming webhook*. Choose the option that runs the rule **without work items from JQL** (verify the option name on your instance).
3. Save the trigger and copy the generated **webhook URL** and **secret**. Treat both as secrets.
4. **Action**: *Create work item* (*Create issue*) in project `ACC`, type `Task`:
   - *Summary*: `{{webhookData.fields.summary}}`
   - *Description*: `{{webhookData.fields.description}}`
   - **Mandatory**: *Marketplace request id* = `{{webhookData.correlationId}}`. Without it the issue has no request id, no completion can be correlated, and the request stays `PENDING` forever.
5. Add your own logic as needed, for example conditions on `{{webhookData.request.provider.dataProductFqn}}` to route to different projects ([full body](../reference/api-reference.md#automation-incoming-webhook-outbound)).
6. Name the rule (for example *Marketplace - create access issue*) and **Turn it on**.

The completion Automation rule (a separate one) comes later, in the platform quickstart, once the gateway URL is known.

## 4. Write the executor configuration

Save this as `config/application.yml`. Replace `customfield_10050` with your field id, and change the `filter` to the data products this executor should handle. The webhook URLs and secrets are **not** in this file: the platform quickstart passes them as secrets.

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
      outcome-rules:                     # first match wins
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

The executor never logs the webhook URLs or tokens and never includes the URL in an error message. Every key is explained in the [configuration reference](../reference/configuration-reference.md).

## Next: pick your platform

You now have the client id and secret, the API URL, the correlation field id, the two webhook URLs and secrets, and `config/application.yml`. Each platform quickstart has an *Automation rule scenario* step for the webhook secrets.

| You run on... | Continue with |
|:--|:--|
| Google Cloud | [Cloud Run](../platforms/cloud-run.md) |
| Kubernetes with Knative | [Knative Serving](../platforms/knative.md) |
| Kubernetes with KEDA | [KEDA HTTP add-on](../platforms/keda-http.md) |
| Azure | [Azure Container Apps](../platforms/azure-container-apps.md) |

## If something goes wrong

| Symptom | Check |
|:--|:--|
| `ERROR` "Jira Automation webhook rejected the call" | The token, or the rule is off, or the URL belongs to another rule |
| `PENDING`, no issue in Jira | The creation rule's audit log: action errors (required fields, project permissions of the rule actor) |
| Issue completed, request still `PENDING` | *Marketplace request id* empty on the issue (step 3.4), or the completion rule did not fire |
| Final message without a comment | The comment read uses the Jira credentials: check the service account's *View comments* permission |

More in [Troubleshooting](../reference/troubleshooting.md). A step-by-step example run is in [Examples](../reference/examples.md#automation-rule).
