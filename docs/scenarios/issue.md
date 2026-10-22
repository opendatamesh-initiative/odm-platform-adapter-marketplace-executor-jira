# Scenario: Jira issue

[README](../../README.md) > **1. Scenario** > 2. Platform > 3. Test

Every access request becomes a **`Task`** in a Jira project (here `ACC`). A team works the issue; closing it with a resolution decides the outcome: *Done* grants (or revokes) the access, *Won't Do* declines it.

On this page you prepare Jira and write the executor configuration. **You will leave with:**

- the **client id** and **client secret** of a service account, and your site's **API URL**;
- the id of the **correlation custom field** (for example `customfield_10050`);
- the file **`config/application.yml`**.

You need Jira Cloud, with an **organisation admin** (for the service account) and a **Jira admin** (custom fields, workflows). Atlassian Cloud is renaming "issue" to "**work item**": menu labels may differ slightly on your site.

## 1. Create the service account

1. **admin.atlassian.com > Directory > Service accounts > Create service account**, for example `odm-marketplace-executor`. Give it **app access to Jira** (product role *User*).
2. Open the account, **Create credentials > OAuth 2.0**, with the scopes to create and edit issues, read users and read comments, for example `read:jira-work`, `write:jira-work` and `read:jira-user` (verify the scope names on your instance).
3. Copy the **client id** and **client secret**.
4. Find your **cloudId**: open `https://<site>.atlassian.net/_edge/tenant_info`. The API URL is `https://api.atlassian.com/ex/jira/<cloudId>`.
5. Check that a token can be obtained:

   ```bash
   curl -s -X POST "https://auth.atlassian.com/oauth/token" \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d grant_type=client_credentials -d client_id="<client id>" -d client_secret="<client secret>"
   # -> {"access_token":"...","token_type":"Bearer","expires_in":3600,...}
   ```

An API token of a dedicated user is the alternative ([details](../reference/jira-setup.md#cloud)).

## 2. Create the correlation custom field

The executor writes the marketplace request id in this field; the completion rule reads it back.

1. **Settings (cog) > Work items (Issues) > Custom fields > Create custom field**, type **Short text (single line)**, name *Marketplace request id*.
2. Note its id: it is in the URL of the field's configuration (`customfield_10050`), or in `GET /rest/api/2/field`.

## 3. Prepare the project

1. Create (or reuse) a project with key **`ACC`** and the issue type **`Task`**. A company-managed project gives you full control of screens and workflow.
2. Put *Marketplace request id*, `summary` and `description` on the **create screen** of `Task` in `ACC`. A missing field makes the creation fail with a `400` that names it.
3. **Project settings > People**: add the service account with a role that grants **Browse projects**, **Create issues** and **View comments** ([full permission list](../reference/jira-setup.md#2-permissions)).
4. In the workflow of `Task`, add closing transitions that set the resolution with a post function, then publish it:

   | Transition | To status | Resolution |
   |:--|:--|:--|
   | *Grant* | *Done* | *Done* |
   | *Decline* | *Done* | *Won't Do* |
   | *Reopen* | *To Do* | cleared (None) |

   Check in **Settings > Work items > Resolutions** that *Done* and *Won't Do* exist. [Why resolutions matter](../reference/jira-setup.md#6-statuses-resolutions-and-workflow).

The completion Automation rule comes later, in the platform quickstart, once the gateway URL is known.

## 4. Write the executor configuration

Save this as `config/application.yml`. Replace `customfield_10050` with your field id, and change the `filter` to the data products this executor should handle (or remove it to handle all of them).

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
      outcome-rules:                 # first match wins
        - when:
            statusCategory: "done"
            resolution: '(?i)(Done|Fixed|Resolved|Granted|Revoked)'
          outcome: FULFILLED
        - when:
            statusCategory: "done"
            resolution: "(?i)(Won't Do|Won't Fix|Declined|Rejected|Cannot Reproduce|Duplicate)"
          outcome: REFUSED
        - when:                      # any other completed issue, including an unset resolution
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

Every key is explained in the [configuration reference](../reference/configuration-reference.md); optional additions (reporter, priority, labels) are in [Examples](../reference/examples.md#optional-profile-additions).

## Next: pick your platform

You now have the client id and secret, the API URL, the correlation field id and `config/application.yml`.

| You run on... | Continue with |
|:--|:--|
| Google Cloud | [Cloud Run](../platforms/cloud-run.md) |
| Kubernetes with Knative | [Knative Serving](../platforms/knative.md) |
| Kubernetes with KEDA | [KEDA HTTP add-on](../platforms/keda-http.md) |
| Azure | [Azure Container Apps](../platforms/azure-container-apps.md) |

## If something goes wrong

| Symptom | Check |
|:--|:--|
| `ERROR` with HTTP 400 naming `customfield_10050` | The field is not on the create screen of `Task` in `ACC` (step 3.2) |
| `ERROR` with HTTP 401/403 | Service account app access, OAuth scopes, project role (steps 1 and 3.3) |
| Executor log `ignored: NON_TERMINAL_STATE` | The resolution does not match the outcome rules, or the workflow does not set one (step 3.4) |

More in [Troubleshooting](../reference/troubleshooting.md). A step-by-step example run with every payload is in [Examples](../reference/examples.md#jira-issue).
