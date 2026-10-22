# Deployment

[Back to README](../../README.md)

## Overview

The executor can run on serverless container platforms, with **scale to zero** and **cold starts**, without any code or configuration change. One rule decides which platforms work.

**The rule.** The executor answers the Marketplace Service with `202` immediately and then opens the Jira issue **on a background thread, after the response has been sent**. The platform must therefore:

1. keep the CPU of a running instance available after the response (no freezing, no throttling between requests);
2. scale an instance in only after an **idle window** of several minutes, long enough for the background work (a few seconds) to finish.

Scale to zero and cold starts are not a problem:

- the image contains a native executable that starts in well under a second;
- the Marketplace Service calls the executor without a client timeout, so it simply waits for the first instance;
- the OAuth token and the parsed templates are cached in memory and are rebuilt by a new instance on first use.

Related: [Build and run](build-and-run.md) · [Jira setup](jira-setup.md) · [Configuration reference](configuration-reference.md) · [Troubleshooting](troubleshooting.md)

## Contents

- [Which platforms fit](#which-platforms-fit)
- [What every deployment needs](#what-every-deployment-needs)
- [Exposing the notification endpoints](#exposing-the-notification-endpoints)
- [Known limit: scale-in during background work](#known-limit-scale-in-during-background-work)
- [Cost](#cost)
- [Containers and Kubernetes](#containers-and-kubernetes)
- [Register with the Marketplace Service](#register-with-the-marketplace-service)

## Which platforms fit

| Platform | Scale to zero | CPU after the response | Key setting | Supported | Guide |
|:--|:--|:--|:--|:--|:--|
| **Google Cloud Run**, instance-based billing | Yes (`--min-instances=0`) | Yes | `--no-cpu-throttling` | ✅ | [Deploy on Cloud Run](deploy-cloud-run.md) |
| **Knative Serving** on Kubernetes (GKE, EKS, AKS, on-premises) | Yes | Yes | `autoscaling.knative.dev/scale-to-zero-pod-retention-period` | ✅ | [Deploy on Knative](deploy-knative.md) |
| **KEDA HTTP add-on** on Kubernetes | Yes | Yes | `scaledownPeriod` of the `HTTPScaledObject` | ✅ | [Deploy with the KEDA HTTP add-on](deploy-keda-http.md) |
| **Azure Container Apps** | Yes | Yes | `cooldownPeriod` of the scale settings | ✅ | [Deploy on Azure Container Apps](deploy-azure-container-apps.md) |
| AWS Fargate (Amazon ECS) | No scaling to zero on HTTP traffic (at least one task) | Yes | none | ⚠️ works, without scale to zero | [Provisioning](deployment.md#containers-and-kubernetes) |
| Google Cloud Run, request-based billing (the default) | Yes | **No**: the CPU is throttled after the response | none | ❌ | |
| AWS Lambda | Yes | **No**: the runtime is frozen after the response | none | ❌ | |
| AWS App Runner | Instances stay provisioned | **No**: the CPU is throttled between requests | none | ❌ | |

On the ❌ platforms the issue creation that starts after the `202` stalls, or is lost: the marketplace request stays without a `PENDING` or `ERROR` result. On AWS, the way to get scale to zero is Kubernetes (EKS) with [Knative](deploy-knative.md) or the [KEDA HTTP add-on](deploy-keda-http.md).

## What every deployment needs

Every platform guide provides the same things, in its own way:

| Need | Value |
|:--|:--|
| Image | `docker.io/opendatamesh/odm-platform-adapter-marketplace-executor-jira:<version>` (or `ghcr.io/opendatamesh-initiative/...` where the platform can pull from it). Always pin a released version |
| Port | `9019` |
| Profiles file | Your `application.yml` with `jira.profiles.subscribe` and `.unsubscribe`, mounted as a file (ConfigMap, secret volume), with `QUARKUS_CONFIG_LOCATIONS` pointing at it ([why](configuration-reference.md#where-to-put-the-configuration)) |
| Environment | `JIRA_DEPLOYMENT`, `JIRA_SITE_URL`, `JIRA_API_URL` (Cloud with OAuth), `JIRA_AUTH_TYPE`, `JIRA_CORRELATION_FIELD`, `ODM_PRODUCT_PLANE_MARKETPLACE_SERVICE_ADDRESS` |
| Secrets | `JIRA_CLIENT_ID` and `JIRA_CLIENT_SECRET` (Cloud OAuth), or `JIRA_API_TOKEN_EMAIL` and `JIRA_API_TOKEN` (Cloud API token), or `JIRA_PAT` (Data Center); for `AUTOMATION_WEBHOOK` profiles, the webhook URLs and tokens your profiles reference. From the platform's secret store, never in a committed file |
| Network isolation | **Hard requirement.** The executor's endpoints have no authentication of their own ([why](jira-setup.md#8-securing-the-notification-endpoints)): only the Marketplace Service and the gateway may reach it. Use the platform's internal-only ingress or a cluster-local address |
| Egress | Jira Cloud: `api.atlassian.com` and `auth.atlassian.com` over HTTPS. Jira Data Center: a private network path to the Data Center host (VPN, peering). Plus the Marketplace Service |
| Idle window | Several minutes (for example 5) before an idle instance is removed |
| Registration | The executor's **internal** URL in the Marketplace Service configuration ([how](deployment.md#register-with-the-marketplace-service)) |
| Native webhooks only | A small, public HTTP function that verifies Jira's `X-Hub-Signature` and forwards the call (Cloud Run functions, Azure Functions, a Knative service, ...): [Jira setup, section 10](jira-setup.md#10-completion-channel-b---native-webhook) |

## Exposing the notification endpoints

The gateway options do not depend on the platform: [Securing the notification endpoints](jira-setup.md#8-securing-the-notification-endpoints) describes blindata-agent (recommended) and your own authenticating gateway. Each platform guide shows how to point them at the executor's internal address, and a platform-native alternative for your own gateway.

The Marketplace Service leg never goes through the gateway: it calls the executor's internal address directly.

## Known limit: scale-in during background work

When the platform removes an idle instance it sends `SIGTERM`. The background pool that opens issues uses daemon threads and does not wait for them to finish, so an issue that is being opened at that exact moment can be lost.

- The idle window makes this unlikely: the background work lasts a few seconds, the window several minutes.
- It is not impossible, for example when a platform removes an instance earlier than usual.
- **Symptom**: the marketplace request has no `PENDING` and no `ERROR` result, and the log has no `Opened Jira issue ...` line for its UUID. The issue may or may not exist in Jira.
- **What to do**: check Jira for an issue with that request id in the correlation field; if there is none, submit the request again.

## Cost

You pay for the time an instance is alive, including the idle window after the last request. At marketplace volumes (a few requests a day) this is far cheaper than an instance that is always on: each burst of activity costs roughly one idle window of instance time. Keeping one instance warm (minimum one replica) removes cold starts at the cost of paying for it continuously.

## Containers and Kubernetes

The recommended way is the container image, on Docker, Kubernetes or any container platform. Running the JVM build as a plain Java 21 process also works.

- **Serverless, with scale to zero**: see [Which platforms fit](#which-platforms-fit) and the [platform quickstarts](../../README.md#2-pick-your-platform). The platform must keep the CPU after the response, because the issue is created after the `202` answer: AWS Lambda, AWS App Runner and Cloud Run with request-based billing are not supported for this reason. AWS Fargate works, without scale to zero.

Things to provide:

- Port **9019** (`quarkus.http.port`).
- The environment variables and secrets of [Configure](build-and-run.md#configure), and the profile YAML as a mounted file (`/work/config/application.yml` in the image, or any path named by `QUARKUS_CONFIG_LOCATIONS`).
- Outbound access to Jira (or `api.atlassian.com` and `auth.atlassian.com` on Cloud) and to the Marketplace Service.
- **Network isolation**: the executor's endpoints have no authentication of their own. It must be reachable **only by the Marketplace Service and by the authenticating gateway**, never directly from Jira or the internet; otherwise anyone who can reach it can forge final results. The gateway may forward the caller's headers; the executor ignores them. The executor never logs or echoes request headers, bodies or tokens; keep it that way in any proxy or access log you add in front of it. See [Jira setup](jira-setup.md#8-securing-the-notification-endpoints).

On **Kubernetes**, run the image as a `Deployment` with one replica per executor, take the credentials from a `Secret`, mount the profiles from a `ConfigMap`, and expose it with a cluster-internal `Service` only. A sketch:

```yaml
containers:
  - name: executor
    image: ghcr.io/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira:<version>
    ports:
      - containerPort: 9019
    envFrom:
      - secretRef:
          name: jira-executor-credentials   # JIRA_CLIENT_ID, JIRA_CLIENT_SECRET, ...
    env:
      - name: JIRA_SITE_URL
        value: https://acme.atlassian.net
      - name: JIRA_CORRELATION_FIELD
        value: customfield_10050
      - name: ODM_PRODUCT_PLANE_MARKETPLACE_SERVICE_ADDRESS
        value: http://marketplace-service:8085
    volumeMounts:
      - name: profiles
        mountPath: /work/config
volumes:
  - name: profiles
    configMap:
      name: jira-executor-profiles           # key: application.yml
```

Use a `NetworkPolicy` that admits only the Marketplace Service and the gateway to port 9019.

## Register with the Marketplace Service

The Marketplace Service forwards every request to the executors listed in its own configuration (`odm.utility-plane.marketplace-executors`), calling `{address}/api/v1/up/marketplace-executor/requests`. Add this executor:

```yaml
odm:
  utility-plane:
    marketplace-executors:
      - name: JIRA
        address: http://marketplace-executor-jira.internal:9019
        active: true
```

The `name` is free. Restart the Marketplace Service if it does not reload configuration. The executor must also know the Marketplace Service (`ODM_PRODUCT_PLANE_MARKETPLACE_SERVICE_ADDRESS`) to post results to `/api/v1/pp/marketplace/requests/{uuid}/results`. Requests the executor's filter does not accept are answered `409`. Also [secure the notification endpoints](jira-setup.md#8-securing-the-notification-endpoints) (register the executor in your authenticating gateway) and create the Jira Automation rule or webhook.

---
[Back to README](../../README.md) | [Build and run](build-and-run.md) | [Examples](examples.md)
