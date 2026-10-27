# Deployment

Everything needed to run Crowded on the Docker Swarm host ([ADR 0008](../docs/deliverables/adr/0008-single-ec2-docker-swarm-with-terraform.md)) from the images the pipeline publishes ([ADR 0011](../docs/deliverables/adr/0011-ci-pipeline-and-vulnerability-policy.md)). This README covers the CI/CD side: what the scripts do, and the GitHub and Doppler settings they need. Provisioning the host, and the workflow job that runs these scripts on it, come with the infrastructure story.

| Path | What it is |
|---|---|
| `docker-compose.swarm.yml` | The Swarm stack: router, `core`, `jobs`, `notifications`, `backoffice`, `participant-app`, PostgreSQL, RabbitMQ and Redis |
| `remote-deploy.sh` | Prepares the host, deploys a release, checks it and rolls back if it fails |
| `remote-rollback.sh` | Deploys an earlier release again |
| `lib/common.sh` | Functions both scripts share |
| `router/nginx.conf` | The router's routes, rate limit and SSE settings |
| `observability/` | Prometheus, Loki, Alloy and Grafana configuration, added by #70 |

## How a Deployment Works

1. The pipeline pushes the image of each changed app to Docker Hub, tagged with the commit SHA and `main`.
2. The deploy job copies this folder to the host and runs `remote-deploy.sh`, passing `RELEASE_ID` (the commit SHA) and the image of each changed app, for example `CORE_IMAGE=<namespace>/crowded-core:<sha>`. A service without a new image keeps the image it runs now.
3. On the host, the script:
   1. installs Docker Engine and the Doppler CLI if they are missing, checks that Docker is rootful and `live-restore` is off, and initialises a single-node Swarm;
   2. pulls each image and pins it by digest;
   3. reads the secrets from Doppler and stores each one as a Swarm secret named after a hash of its value: a changed secret becomes a new secret, an unchanged one is reused;
   4. writes a **release** to `/opt/crowded/releases/<time>-<sha>/`: a copy of the stack file and router config, and `release.env` with every image digest, secret name and configuration value;
   5. runs `docker stack deploy` with that release and waits until every service runs all its replicas with the release's image, then requests `/` through the router.
4. If any of this fails, the release is renamed to `<release>.failed` and the previous release is deployed again. The script still exits with an error, so the pipeline run fails.
5. On success, `/opt/crowded/current` points at the new release. The newest 5 releases, and the secrets they use, are kept for rollback.

Each service also rolls itself back when its new task does not become healthy (`update_config.failure_action: rollback`). The script notices because the service no longer runs the release's image.

### Rolling Back

```bash
bash /opt/crowded/deployment/remote-rollback.sh --list               # releases that can be restored
bash /opt/crowded/deployment/remote-rollback.sh                      # the release before the current one
bash /opt/crowded/deployment/remote-rollback.sh 20261027T181500Z-2617d73e  # a specific release
```

A rollback deploys the whole recorded release again: the stack file, every image by digest and the secrets as they were. It needs no Doppler token. Database migrations are not undone, so a migration must stay compatible with the previous release's code.

### Running a Deployment by Hand

```bash
RELEASE_ID=<commit sha> \
DOCKERHUB_NAMESPACE=<namespace> \
DOPPLER_TOKEN=<service token> \
CORE_IMAGE=<namespace>/crowded-core:<commit sha> \
bash /opt/crowded/deployment/remote-deploy.sh
```

`remote-deploy.sh --bootstrap-only` only prepares the host (Docker, Doppler, Swarm), for example from the instance's first-boot script.

| Variable | Default | Meaning |
|---|---|---|
| `RELEASE_ID` | required | Commit SHA being deployed |
| `DOCKERHUB_NAMESPACE` | required | Docker Hub user or organization of the images |
| `DOPPLER_TOKEN` | required | Doppler service token of the environment's config |
| `CORE_IMAGE`, `JOBS_IMAGE`, `NOTIFICATIONS_IMAGE`, `BACKOFFICE_IMAGE`, `PARTICIPANT_APP_IMAGE` | the current release's, else `<namespace>/crowded-<service>:main` | Image for that service, by tag or digest |
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

The deploy job also needs SSH access to the host (address, user, private key and known-hosts entry). The infrastructure story adds those secrets, because they come from the provisioned instance.

## Doppler Settings

Project `crowded`, config `prd`. The service token in `DOPPLER_TOKEN` already identifies the project and config, so the scripts take no project or config name.

| Key | Secret | Meaning |
|---|---|---|
| `POSTGRES_DB` | No | Database name, e.g. `crowded` |
| `POSTGRES_USER` | No | Database user of the services |
| `POSTGRES_PASSWORD` | Yes | Password of that user |
| `RABBITMQ_USERNAME` | No | RabbitMQ user of the services |
| `RABBITMQ_PASSWORD` | Yes | Password of that user. Use letters and digits only: it is written into a RabbitMQ config file |
| `REDIS_PASSWORD` | Yes | Redis password |
| `JWT_PRIVATE_KEY` | Yes | PEM private key Core signs tokens with ([ADR 0005](../docs/deliverables/adr/0005-authentication-in-core-with-jwt.md)). Only Core gets it |
| `JWT_PUBLIC_KEY` | Yes | PEM public key every service checks tokens with |

Generate the key pair once, store both keys in Doppler, and delete the local files:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
```

Secrets reach the containers as files in `/run/secrets`, never as environment variables, so `docker inspect` does not show them. The backend services read them through Spring Boot's config tree import; PostgreSQL through `POSTGRES_PASSWORD_FILE`; Redis and RabbitMQ through a config file. Rotating a secret is changing it in Doppler and deploying: the new value becomes a new Swarm secret, and the services restart with it.

## Routes

| Path | Service |
|---|---|
| `/api/notifications/…` | `notifications` (without `/api`) |
| `/api/…` | `core` (without `/api`) |
| `/backoffice/…` | `backoffice` (without `/backoffice`) |
| `/…` | `participant-app` |
| `/healthz` | the router itself |

The backoffice is built with Vite's `base: '/backoffice/'`, so its asset URLs keep the prefix the router removes.

`/api/actuator` and `/api/notifications/actuator` answer 404: health and metrics stay inside the Swarm. The router is the only service with a published port (80); the data stores are on an internal network that the router cannot reach.
