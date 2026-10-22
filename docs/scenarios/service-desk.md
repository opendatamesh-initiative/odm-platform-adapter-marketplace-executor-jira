# Scenario: Service Management request with approvals

[README](../../README.md) > **1. Scenario** > 2. Platform > 3. Test

Access requests are raised in a **Jira Service Management (JSM) service project** (here `SD`), **on behalf of the marketplace requester**. They appear in the customer portal like any other request, the requester follows them there, and they go through the project's **approvals**. A declined approval becomes `DENIED` in the marketplace.

On this page you prepare Jira Service Management and write the executor configuration. **You will leave with:**

- the **client id** and **client secret** of a service account, and your site's **API URL**;
- the id of the **correlation custom field** (for example `customfield_10050`);
- the file **`config/application.yml`**.

You need Jira Cloud with Jira Service Management, an **organisation admin** (service account) and a **Jira admin** (custom fields, request types). Every marketplace requester must be (or be able to become) a **customer** of the service project, with the same email in Jira as in the marketplace. Menu labels may differ slightly on your site.

## 1. Create the service account

1. **admin.atlassian.com > Directory > Service accounts > Create service account**, for example `odm-marketplace-executor`. Give it app access to **Jira** and to **Jira Service Management** (as an **agent**: raising requests on behalf of customers requires it).
2. **Create credentials > OAuth 2.0**, with the scopes to create and edit issues, read users, create service desk requests and read comments, for example `read:jira-work`, `write:jira-work`, `read:jira-user`, `read:servicedesk-request` and `write:servicedesk-request` (verify the scope names on your instance).
3. Copy the **client id** and **client secret**.
4. Find your **cloudId** at `https://<site>.atlassian.net/_edge/tenant_info`. The API URL is `https://api.atlassian.com/ex/jira/<cloudId>`.
5. Check that a token can be obtained:

   ```bash
   curl -s -X POST "https://auth.atlassian.com/oauth/token" \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d grant_type=client_credentials -d client_id="<client id>" -d client_secret="<client secret>"
   # -> {"access_token":"...","token_type":"Bearer","expires_in":3600,...}
   ```

## 2. Create the correlation custom field

1. **Settings (cog) > Work items (Issues) > Custom fields > Create custom field**, type **Short text (single line)**, name *Marketplace request id*. Associate it with the screens of `SD`.
2. Note its id (`customfield_10050`): it is in the field's configuration URL, or in `GET /rest/api/2/field`.

## 3. Prepare the service project

1. Create (or reuse) a **service project** (for example from the *IT service management* or *General service management* template), key `SD`.
2. **Project settings > People**: add the service account with the role **Service Desk Team** (agent).
3. **Project settings > Customer permissions**: every marketplace requester must be a **customer** of `SD`. Allow *Anyone on Jira or the customer list*, or add the requesters (or their organisations) to the customer list.
4. **Project settings > Request types > Create request type**: one for subscribe (*Data product access*) and one for unsubscribe (*Data product access removal*). In each **Request form** keep *Summary* and *Description* and add *Marketplace request id*, hidden from the customer if your release allows it (otherwise visible but optional). Add both to a **portal group**.
5. Find the ids you need for the configuration:
   - `serviceDeskId`: `GET /rest/servicedeskapi/servicedesk`, the `id` of `SD`;
   - `requestTypeId`: `GET /rest/servicedeskapi/servicedesk/{serviceDeskId}/requesttype`, the `id` of each request type;
   - or read them from the portal URL `/servicedesk/customer/portal/{serviceDeskId}/group/.../create/{requestTypeId}`.
6. In the workflow of the request types, add an **approval** step (for example *Waiting for approval*). When the approver declines, the request must move to a status that means "declined", here **Declined**. Make sure the done transitions **set a resolution** (*Done* when access is granted or removed, *Won't Do* when refused). JSM templates usually do. [Why resolutions matter](../reference/jira-setup.md#6-statuses-resolutions-and-workflow).

If the field cannot be on the request type, see `correlation-via-edit` in [Examples](../reference/examples.md#notes). The completion Automation rule comes later, in the platform quickstart, once the gateway URL is known.

## 4. Write the executor configuration

Save this as `config/application.yml`. Replace `customfield_10050`, the `service-desk-id` and the two `request-type-id` values with yours, and change the `filter` to the data products this executor should handle.

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
      outcome-rules:                     # first match wins
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

Every key is explained in the [configuration reference](../reference/configuration-reference.md).

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
| `ERROR` with a 400 or 403 and a customer/agent hint | The requester is not a customer of `SD`, or the service account is not an agent (steps 3.2 and 3.3) |
| `ERROR` "requester not found" | The requester's email is not known to Jira or hidden by privacy settings; *Browse users and groups* |
| `ERROR` naming `customfield_10050` | The field is not on the request type: add it, or use `correlation-via-edit` |
| `ERROR` naming a correlation edit failure | `correlation-via-edit` without *Edit issues*; that request will not complete automatically |
| Declined approval, request still `PENDING` | The status name differs from the first outcome rule |

More in [Troubleshooting](../reference/troubleshooting.md). A step-by-step example run with every payload is in [Examples](../reference/examples.md#service-management-request).
