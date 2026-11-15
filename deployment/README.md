# Deployment

Everything needed to run Crowded on the Docker Swarm host ([ADR 0013](../docs/deliverables/adr/0013-single-azure-vm-docker-swarm-with-terraform.md)) from the images the pipeline publishes ([ADR 0011](../docs/deliverables/adr/0011-ci-pipeline-and-vulnerability-policy.md)). This README covers the CI/CD side: what the scripts do, and the GitHub and Doppler settings they need. The host itself is provisioned with OpenTofu in [`infra/`](../infra/README.md), and the Release workflow (`.github/workflows/release.yml`) runs these scripts on it after every merge to `main` ([ADR 0014](../docs/deliverables/adr/0014-opentofu-state-access-and-deployment-from-main.md)). To run the same stack on a development machine, use `scripts/local/stack-up.sh` instead ([Getting Started](../README.md#getting-started)): it deploys `docker-compose.swarm.yml` with the local override `scripts/local/stack.local.yml` and the configuration of `.env`.

| Path | What it is |
|---|---|
| `docker-compose.swarm.yml` | The Swarm stack: router, the `database` migration job, `core`, `jobs`, `notifications`, `backoffice`, `participant-app`, PostgreSQL, RabbitMQ and Redis |
| `observability/` | Prometheus, Loki, Alloy and Grafana ([ADR 0009](../docs/deliverables/adr/0009-observability-stack.md)): `docker-compose.observability.yml`, deployed with the stack file, and their configuration ([README](observability/README.md)) |
| `remote-deploy.sh` | Prepares the host, deploys a release, checks it and rolls back if it fails |
| `remote-rollback.sh` | Deploys an earlier release again |
| `lib/common.sh` | Functions both scripts share |
| `router/nginx.conf` | The router's routes, rate limit and SSE settings |

## How a Deployment Works

1. The Release workflow runs the app workflows, which push the image of each changed app to Docker Hub, tagged with the commit SHA and `main`, and applies the infrastructure.
2. Its deploy job opens SSH for its own address, copies this folder to `/opt/crowded/deployment` on the host and runs `remote-deploy.sh`, passing `RELEASE_ID` (the commit SHA) and `<namespace>/crowded-<app>:main` for every app, for example `CORE_IMAGE=<namespace>/crowded-core:main`. The script pins each to its digest, so a service whose image did not change keeps running untouched. A push that pushed no image and did not change this folder is not deployed.
3. On the host, the script:
   1. installs Docker Engine and the Doppler CLI if they are missing, checks that Docker is rootful and `live-restore` is off, and initialises a single-node Swarm;
   2. pulls each image and pins it by digest;
   3. reads the secrets from Doppler and stores each one as a Swarm secret named after a hash of its value: a changed secret becomes a new secret, an unchanged one is reused;
   4. writes a **release** to `/opt/crowded/releases/<time>-<sha>/`: a copy of the stack file, the router config and `observability/`, and `release.env` with every image digest, secret name and configuration value;
   5. migrates the database ([ADR 0012](../docs/deliverables/adr/0012-database-migrations-with-flyway.md)): deploys the release with `core`, `jobs` and `notifications` still on the images they run now (with no replicas on the very first deployment), and waits until the `database` job, a one-shot `replicated-job` running the release's `crowded-database` image, has completed. A failed migration fails the deployment here, before any service gets a new image;
   6. runs `docker stack deploy` with the whole release and waits until every service runs all its replicas with the release's image and every update it started has `completed`, which Swarm reports once the new task has stayed healthy for the monitor period (30 seconds). Then it requests `/` through the router.
4. If any of this fails, the release is renamed to `<release>.failed` and the previous release is deployed again. The script still exits with an error, so the pipeline run fails.
5. On success, `/opt/crowded/current` points at the new release. The newest 5 releases, and the secrets they use, are kept for rollback.
6. The deploy job then requests `/healthz`, `/` and `/backoffice/` through the public IP, from outside. If that fails, it runs `remote-rollback.sh`, and the run fails.

Each service also rolls itself back when its new task does not become healthy (`update_config.failure_action: rollback`). The script notices at once, because the service no longer runs the release's image, and deploys the previous release. Tested on a single-node Swarm (Docker 29): a release whose `core` never became healthy was detected in about 20 seconds and the previous release was live again 5 seconds later.

The migration step was tested on a single-node Swarm (Docker 27, in Docker) with stub images for the services: on an empty database, `core` got its first task only after the job had completed; a release whose migration failed stopped there, with `core` still on the previous image; and the next good release, and a rollback to the first one, each ran the job again before updating the services.

### Rolling Back

```bash
bash /opt/crowded/deployment/remote-rollback.sh --list               # releases that can be restored
bash /opt/crowded/deployment/remote-rollback.sh                      # the release before the current one
bash /opt/crowded/deployment/remote-rollback.sh 20261027T181500Z-2617d73e  # a specific release
```

A rollback deploys the whole recorded release again: the stack file, every image by digest and the secrets as they were. It needs no Doppler token. Database migrations are not undone: the older `crowded-database` image ignores the newer migrations, and the older services run against the newer schema. So every migration stays compatible with the previous release's code, adding first and removing in a later release ([ADR 0012](../docs/deliverables/adr/0012-database-migrations-with-flyway.md#compatible-with-the-previous-release)).

### Running a Deployment by Hand

```bash
RELEASE_ID=<commit sha> \
DOCKERHUB_NAMESPACE=<namespace> \
DOPPLER_TOKEN=<service token> \
CORE_IMAGE=<namespace>/crowded-core:<commit sha> \
bash /opt/crowded/deployment/remote-deploy.sh
```

`remote-deploy.sh --bootstrap-only` only prepares the host (Docker, Doppler, Swarm). The VM's first boot runs it ([`infra/`](../infra/README.md)).

| Variable | Default | Meaning |
|---|---|---|
| `RELEASE_ID` | required | Commit SHA being deployed |
| `DOCKERHUB_NAMESPACE` | required | Docker Hub user or organization of the images |
| `DOPPLER_TOKEN` | required | Doppler service token of the environment's config |
| `DATABASE_IMAGE`, `CORE_IMAGE`, `JOBS_IMAGE`, `NOTIFICATIONS_IMAGE`, `BACKOFFICE_IMAGE`, `PARTICIPANT_APP_IMAGE` | the current release's, else `<namespace>/crowded-<service>:main` | Image for that service, by tag or digest |
| `STACK_NAME` | `crowded` | Swarm stack name |
| `DEPLOY_ROOT` | `/opt/crowded` | Where releases are kept |
| `KEEP_RELEASES` | `5` | Releases kept for rollback |
| `WAIT_TIMEOUT_SECONDS` | `600` | How long to wait for the services |
| `SMOKE_URL` | `http://127.0.0.1/` | Requested through the router after the services are ready |
| `SWARM_ADVERTISE_ADDR` | address of the default route | Address of the Swarm manager |

## GitHub Settings

Secrets live in the CI secret store, never in the repository (SR-008).

| Name | Kind | Used by | Value |
|---|---|---|---|
| `DOCKERHUB_NAMESPACE` | Variable | Image push, deploy | Docker Hub user or organization that owns the `crowded-*` repositories |
| `DOCKERHUB_USERNAME` | Secret | Image push | Docker Hub account that pushes |
| `DOCKERHUB_TOKEN` | Secret | Image push | Docker Hub personal access token with *Read & Write* scope. The host pulls the public images without one |
| `DOPPLER_TOKEN` | Secret | Deploy | Doppler **service token** with read access to the production config only |
| `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` | Secret | Plan, apply, deploy | The Azure for Students tenant and subscription |
| `AZURE_CLIENT_ID` | Secret | Apply, deploy | Client ID of the managed identity `crowded-github-deploy` (OIDC, no secret), which `main` signs in as |
| `AZURE_PLAN_CLIENT_ID` | Secret | Plan in pull requests | Client ID of the read-only managed identity `crowded-github-plan` |
| `TOFU_STATE_PASSPHRASE` | Secret | Plan, apply | Passphrase of OpenTofu's state encryption. Also kept in the team's password manager: without it the state cannot be read |
| `SSH_PRIVATE_KEY` | Secret | Deploy | Private key of the deploy key; its public key is in `infra/production.auto.tfvars` |
| `SSH_KNOWN_HOSTS` | Secret | Deploy | `<public IP> ssh-ed25519 <host key>`: the VM's host key, pinned. Changes when the VM is replaced |
| `SSH_ALLOWED_CIDRS` | Variable | Plan, apply | Optional. JSON list of the addresses allowed to SSH, e.g. `["203.0.113.10/32"]`. Default `[]`: only the deploy job, while it runs |
| `CROWDED_BOT_CLIENT_ID` | Secret (Actions **and** Dependabot) | Dependabot auto-merge | Client ID of the crowded-bot GitHub App |
| `CROWDED_BOT_PRIVATE_KEY` | Secret (Actions **and** Dependabot) | Dependabot auto-merge | A private key (`.pem` contents) of the crowded-bot GitHub App |

The crowded-bot app approves, auto-merges and asks Dependabot to rebase its pull requests ([ADR 0011](../docs/deliverables/adr/0011-ci-pipeline-and-vulnerability-policy.md#dependabot)). To create it, the repository owner opens *Settings → Developer settings → GitHub Apps → New GitHub App*, names it `crowded-bot`, turns off the webhook, grants the repository permissions *Contents: Read and write* and *Pull requests: Read and write* (*Metadata: Read* is added automatically), and chooses *Only on this account*. Then: generate a private key, install the app on this repository only, and add its client ID and private key as both Actions and Dependabot secrets (*Settings → Secrets and variables*). The repository settings allow auto-merge and *Allow GitHub Actions to create and approve pull requests* stays off, because the app, not `GITHUB_TOKEN`, approves.

The apply and deploy jobs run in the **`production` environment**, which only `main` can use and which has no required reviewers. The host's address and user come from OpenTofu's outputs, not from secrets. How to create the identities, the keys and the environment: [`infra/README.md`](../infra/README.md#setting-it-up).

## Doppler Settings

Project `crowded`, config `prd`, the only environment deployed for now (the design also has `dev` and `stg`, [ADR 0013](../docs/deliverables/adr/0013-single-azure-vm-docker-swarm-with-terraform.md#environments)). The service token in `DOPPLER_TOKEN` already identifies the project and config, so the scripts take no project or config name.

| Key | Secret | Meaning |
|---|---|---|
| `POSTGRES_DB` | No | Database name, e.g. `crowded` |
| `POSTGRES_USER` | No | The migration user, which owns the schema. Only the `database` job uses it |
| `POSTGRES_PASSWORD` | Yes | Password of that user |
| `CORE_DB_PASSWORD`, `JOBS_DB_PASSWORD`, `NOTIFICATIONS_DB_PASSWORD` | Yes | Passwords of the database users `crowded_core`, `crowded_jobs` and `crowded_notifications`, which the `database` job creates. Letters and digits only. Each service gets only its own |
| `SEED_DEMO_DATA` | No | `true` loads the demo data ([`database/`](../database/README.md#demo-accounts)). Optional, default `false`. On in development and staging; production sets it too while it simulates them ([ADR 0013](../docs/deliverables/adr/0013-single-azure-vm-docker-swarm-with-terraform.md#environments)) |
| `RABBITMQ_USERNAME` | No | RabbitMQ user of the services |
| `RABBITMQ_PASSWORD` | Yes | Password of that user. Use letters and digits only: it is written into a RabbitMQ config file |
| `REDIS_PASSWORD` | Yes | Redis password |
| `JWT_PRIVATE_KEY` | Yes | PEM private key Core signs tokens with ([ADR 0005](../docs/deliverables/adr/0005-authentication-in-core-with-jwt.md)). Only Core gets it |
| `JWT_PUBLIC_KEY` | Yes | PEM public key every service checks tokens with |
| `TICKET_ENTRY_CODE_KEY` | Yes | Base64 256-bit key that encrypts the entry codes of imported tickets. Only Core gets it. Changing it makes the codes already stored unreadable, so it is not rotated like the passwords |
| `GROQ_API_KEY` | Yes | Groq API key that Core reads imported tickets with ([ADR 0020](../docs/deliverables/adr/0020-groq-for-ticket-extraction.md)). Only Core gets it. With a wrong or revoked key, participants type the event code |
| `GRAFANA_ADMIN_PASSWORD` | Yes | Password of Grafana's `admin` at `/grafana/`. Grafana takes it on its first start only; to change it later, change it in Grafana too |

Generate the key pair once, store both keys in Doppler, and delete the local files:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
```

Generate the entry-code key once and store the printed value in Doppler:

```bash
openssl rand -base64 32
```

Secrets reach the containers as files in `/run/secrets`, never as environment variables, so `docker inspect` does not show them. The backend services read them through Spring Boot's config tree import; PostgreSQL through `POSTGRES_PASSWORD_FILE`; Redis and RabbitMQ through a config file. Rotating a secret is changing it in Doppler and deploying: the new value becomes a new Swarm secret, and the services restart with it.

## Routes

| Path | Service |
|---|---|
| `/api/notifications/…` | `notifications` (without `/api`) |
| `/api/…` | `core` (without `/api`) |
| `/backoffice/…` | `backoffice` (without `/backoffice`) |
| `/grafana/…` | `grafana`, with its own sign-in |
| `/…` | `participant-app` |
| `/healthz` | the router itself |

The backoffice is built with Vite's `base: '/backoffice/'`, so its asset URLs keep the prefix the router removes.

`/api/actuator` and `/api/notifications/actuator` answer 404: health and metrics stay inside the Swarm, where Prometheus scrapes them. The router is the only service with a published port (80); the data stores are on an internal network that the router cannot reach.
