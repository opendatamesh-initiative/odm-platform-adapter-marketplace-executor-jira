# Provisioning

[Back to README](../README.md)

## Overview

This page explains how to build the executor, run it (as a Java process or as a Docker container), connect it to the rest of the platform and check that it works. The executor is a standard Quarkus service: one process, no database, port 9019. It needs to reach Jira and the Marketplace Service, and it must be reachable by the Marketplace Service and by the gateway that authenticates Jira ([Securing the notification endpoints](jira-setup.md#8-securing-the-notification-endpoints)).

The easiest way to run it is the ready-made Docker image, published for every release on GitHub Container Registry and Docker Hub ([Run with Docker](#run-with-docker)). The repository does not ship a Helm chart or Kubernetes manifests; the [Deploy](#deploy) section lists what a container needs. Images are built and published by GitHub Actions ([CI/CD and releases](#cicd-and-releases)).

Related: [Jira setup](jira-setup.md) · [Configuration reference](configuration-reference.md) · [Troubleshooting](troubleshooting.md)

## Contents

- [Requirements](#requirements)
- [Build](#build)
- [Configure](#configure)
- [Run with Docker](#run-with-docker)
- [Container configuration](#container-configuration)
- [Deploy](#deploy)
- [Register with the Marketplace Service](#register-with-the-marketplace-service)
- [Verify](#verify)
- [Tests](#tests)
- [CI/CD and releases](#cicd-and-releases)

## Requirements

- To run the published image: Docker (or any container runtime). Nothing else on the host.
- To build from source: Java **21** and Maven **3.9+** (or the included `./mvnw`). The project uses Quarkus 3.40.1. A native image build also needs Docker (no local GraalVM or Mandrel is required).
- Jira Cloud or Data Center 8.14+, prepared as in [Jira setup](jira-setup.md).
- A running Marketplace Service (default address `http://localhost:8085`).
- An authenticating gateway or reverse proxy in front of the executor's webhook endpoints, for the notification leg (see [Securing the notification endpoints](jira-setup.md#8-securing-the-notification-endpoints)).

## Build

```bash
./mvnw package                  # JVM build, runner in target/quarkus-app/
./mvnw -Pnative package         # native build (Mandrel or GraalVM 21 installed locally)
```

The JVM build is started with `java -jar target/quarkus-app/quarkus-run.jar`. To build the native executable that goes into the Docker image, see [Build the image locally](#build-the-image-locally).

## Configure

1. Set the environment variables:

| Environment variable | Purpose |
|:--|:--|
| `JIRA_DEPLOYMENT` | `CLOUD` (default) or `DATA_CENTER` |
| `JIRA_SITE_URL` | **Required.** Site base URL |
| `JIRA_API_URL` | API base URL. **Required on Cloud with OAuth** (`https://api.atlassian.com/ex/jira/<cloudId>`) |
| `JIRA_API_VERSION` | `2` (default) or `3` (Cloud only) |
| `JIRA_AUTH_TYPE` | `OAUTH2_CLIENT_CREDENTIALS` (default), `API_TOKEN` or `PERSONAL_ACCESS_TOKEN` |
| `JIRA_CLIENT_ID`, `JIRA_CLIENT_SECRET` | OAuth 2.0 client credentials |
| `JIRA_API_TOKEN_EMAIL`, `JIRA_API_TOKEN` | Cloud API token credentials |
| `JIRA_PAT` | Data Center Personal Access Token |
| `JIRA_CORRELATION_FIELD` | **Required.** Id of the correlation custom field |
| `JIRA_MESSAGES_INCLUDE_CLOSING_USER` | `false` to keep the Jira actor out of result messages (default `true`) |
| `ODM_PRODUCT_PLANE_MARKETPLACE_SERVICE_ADDRESS` | Marketplace Service base URL |
| `QUARKUS_CONFIG_LOCATIONS` | Path of an extra configuration file |

2. Provide the issue profiles (`jira.profiles.subscribe` and `jira.profiles.unsubscribe`) in an external file: `config/application.yml` in the working directory, or the path in `QUARKUS_CONFIG_LOCATIONS`. Start from the profile of the [use case](use-cases.md) that matches your Jira practice. Use case 1 (Issue API) works as-is on a project that has a `Task` issue type.

Full details: [Configuration reference](configuration-reference.md). Secrets must come from the environment or mounted secrets, never from a committed file.

Local run, for example on Cloud:

```bash
export JIRA_DEPLOYMENT=CLOUD
export JIRA_SITE_URL=https://acme.atlassian.net
export JIRA_API_URL=https://api.atlassian.com/ex/jira/<cloudId>
export JIRA_AUTH_TYPE=OAUTH2_CLIENT_CREDENTIALS
export JIRA_CLIENT_ID=<service account client id>
export JIRA_CLIENT_SECRET=<service account client secret>
export JIRA_CORRELATION_FIELD=customfield_10050
export ODM_PRODUCT_PLANE_MARKETPLACE_SERVICE_ADDRESS=http://localhost:8085

./mvnw quarkus:dev
```

The executor **refuses to start with an invalid configuration** and prints a numbered list of problems; see [startup validation](configuration-reference.md#startup-validation).

## Run with Docker

The image contains the Quarkus **native** executable on `ubi9/ubi-minimal`. It runs as the non-root user `1001`, listens on port **9019** and needs no Java installation. All settings are passed as environment variables, plus a mounted file for the issue profiles.

### Use the published image

Every release is published to two registries:

```bash
docker pull ghcr.io/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira:<version>
# or
docker pull opendatamesh/odm-platform-adapter-marketplace-executor-jira:<version>
```

Run it, for example on Cloud with OAuth, with the profiles file mounted where the executor looks for it (`/work/config/application.yml`):

```bash
docker run --rm -p 9019:9019 \
  -e JIRA_DEPLOYMENT=CLOUD \
  -e JIRA_SITE_URL=https://acme.atlassian.net \
  -e JIRA_API_URL=https://api.atlassian.com/ex/jira/<cloudId> \
  -e JIRA_AUTH_TYPE=OAUTH2_CLIENT_CREDENTIALS \
  -e JIRA_CLIENT_ID=<service account client id> \
  -e JIRA_CLIENT_SECRET=<service account client secret> \
  -e JIRA_CORRELATION_FIELD=customfield_10050 \
  -e ODM_PRODUCT_PLANE_MARKETPLACE_SERVICE_ADDRESS=http://host.docker.internal:8085 \
  -v "$(pwd)/config/application.yml:/work/config/application.yml:ro" \
  ghcr.io/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira:<version>
```

Replace `<version>` with a released version (for example `1.0.2`). `host.docker.internal` reaches a Marketplace Service running on the Docker host (on Linux add `--add-host=host.docker.internal:host-gateway`); in a real deployment use the service's real address. To keep secrets out of the command line, use `--env-file` instead of `-e`.

### Build the image locally

You need Docker; no local GraalVM or Mandrel. The native executable is built inside a builder container, then copied into the runtime image:

```bash
./mvnw package -DskipTests -Dnative -Dquarkus.native.container-build=true
docker build -t odm-platform-adapter-marketplace-executor-jira .
```

The `Dockerfile` copies `target/*-runner` to `/work/application`, so the native build must run first. The native build takes several minutes.

## Container configuration

- **Environment variables.** The same ones as in [Configure](#configure); every property can be set this way ([Configuration reference](configuration-reference.md)). Credentials (`JIRA_CLIENT_SECRET`, `JIRA_API_TOKEN`, `JIRA_PAT`) go in secrets, not in the image or in a committed file.
- **Issue profiles.** `jira.profiles.subscribe` and `jira.profiles.unsubscribe` are required and are not packaged. The working directory of the container is `/work`, and the executor reads `config/application.yml` relative to it, so mount your file at `/work/config/application.yml`. Alternatively mount it anywhere and set `QUARKUS_CONFIG_LOCATIONS` to its path (for example `-e QUARKUS_CONFIG_LOCATIONS=/etc/executor/application.yml`). Any other property can also be set in that file instead of an environment variable.
- **Port.** `9019` is exposed. Override with `QUARKUS_HTTP_PORT` if needed, and map it accordingly.
- **User.** The process runs as user `1001` (group `root`, as OpenShift-style platforms expect). Mounted files must be readable by it.
- **Startup validation.** As for any run, the container exits with a numbered list of problems if the configuration is invalid; read them with `docker logs`.

## Deploy

The recommended way is the container image, on Docker, Kubernetes or any container platform. Running the JVM build as a plain Java 21 process also works. Things to provide:

- Port **9019** (`quarkus.http.port`).
- The environment variables and secrets above, and the profile YAML as a mounted file (`/work/config/application.yml` in the image, or any path named by `QUARKUS_CONFIG_LOCATIONS`).
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

## Verify

1. **Health of the process.** Swagger UI at [http://localhost:9019/swagger-ui](http://localhost:9019/swagger-ui) and the OpenAPI document at [http://localhost:9019/api-docs](http://localhost:9019/api-docs) should load.
2. **Jira credentials.** On Cloud, run the `curl` token test from [Jira setup](jira-setup.md#cloud). On Data Center, check the Personal Access Token with a read call, as in [Jira setup](jira-setup.md#data-center).
3. **Notification chain** (harmless): the smoke test of [Securing the notification endpoints](jira-setup.md#8-securing-the-notification-endpoints) must answer `200 {"outcome":"IGNORED","reason":"UNKNOWN_REQUEST"}`. A `401` or `403` comes from the gateway.
4. **End to end.** Submit a request from the marketplace for a data product the filter accepts. In the executor log you should see the business log line with the request UUID and the issue key, the request shows `PENDING` with the issue link, and completing the issue in Jira produces the final status. See [Troubleshooting](troubleshooting.md#logs).

## Tests

```bash
./mvnw test      # outbound calls are mocked
```

## CI/CD and releases

Two GitHub Actions workflows live in `.github/workflows/`:

| Workflow | Runs on | What it does |
|:--|:--|:--|
| `ci.yml` | Every push and pull request to `main` | Sets up JDK 21 (Temurin) and runs `./mvnw -B verify` |
| `cicd.yml` | A GitHub release is created | Checks the version, runs the tests, builds the native image and publishes it |

On a release, `cicd.yml` runs two jobs in sequence:

1. **`verify-version`** takes the release tag, strips every character that is not a digit or a dot (`v1.0.2` becomes `1.0.2`) and compares it with the version in `pom.xml`. If they differ, the workflow fails. It then runs the tests.
2. **`deploy-docker-images`** builds the native executable (`./mvnw -B package -DskipTests -Dnative -Dquarkus.native.container-build=true`), builds the image from the `Dockerfile` and pushes it, tagged with the release version, to:
   - GitHub Container Registry, with the workflow's `GITHUB_TOKEN`: `ghcr.io/opendatamesh-initiative/odm-platform-adapter-marketplace-executor-jira:<version>`
   - Docker Hub: `opendatamesh/odm-platform-adapter-marketplace-executor-jira:<version>`. This needs the repository secrets `DOCKER_USERNAME` and `DOCKER_PASSWORD`.

To make a release:

1. Set the POM version to the release version, without `-SNAPSHOT` (for example `1.0.2`).
2. Merge the change to `main`.
3. Create a GitHub release with a tag that matches it: `v1.0.2` or `1.0.2`.
4. Check that the workflow is green and that the image is available in both registries.
5. Bump the POM to the next `-SNAPSHOT` version.

---
[Back to README](../README.md) | [Troubleshooting](troubleshooting.md)
