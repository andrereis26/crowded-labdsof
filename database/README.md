# Crowded Database

The schema of the one PostgreSQL database that Core, Jobs and Notifications share, the database user of each service, and the demo data ([ADR 0012](../docs/deliverables/adr/0012-database-migrations-with-flyway.md), [ADR 0002](../docs/deliverables/adr/0002-shared-database-with-table-ownership.md)). Flyway applies it from plain SQL files, packaged as the `crowded-database` image: the same image runs locally, in the services' integration tests, in CI and in the shared environment.

| Path | What it is |
|---|---|
| `migrations/V<n>__<description>.sql` | Versioned migrations, applied once in order. The tables follow the [data model](../docs/deliverables/technical-design.md#data-model) |
| `migrations/R__grants.sql` | Which service writes which table. Applied again whenever it changes |
| `migrations/beforeMigrate__create_service_users.sql` | Creates `crowded_core`, `crowded_jobs` and `crowded_notifications` and sets their passwords, before every migrate |
| `seed/R__demo_data.sql` | Demo data, only with `SEED_DEMO_DATA=true` |
| `docker-entrypoint.sh` | Reads the configuration below and runs Flyway |
| `Dockerfile` | The `crowded-database` image |
| `compose.yaml` | A throwaway database for local development ([Setup](#setup)) |

## Setup

**Prerequisites:** Docker, nothing else. [`scripts/local/setup.sh`](../scripts/local/setup.sh) (`setup.ps1` on Windows) checks it and creates `.env` ([Getting Started](../README.md#getting-started)).

**Configuration from `.env`:** `POSTGRES_DB`, `POSTGRES_USER` and `POSTGRES_PASSWORD` (the migration user), `CORE_DB_PASSWORD`, `JOBS_DB_PASSWORD` and `NOTIFICATIONS_DB_PASSWORD` (the service users), `SEED_DEMO_DATA` and `DB_PORT`.

**Running it alone,** without the whole Docker stack: a throwaway PostgreSQL 18 on `127.0.0.1:5432`, whose data lives in memory. The `migrate` container applies the migrations and the demo data, then exits.

| Step | Debian, macOS | Windows (PowerShell 7) |
|---|---|---|
| Start it, with RabbitMQ and Redis, with the passwords of `.env` | `scripts/local/infra-up.sh` | `scripts\local\infra-up.ps1` |
| Read the migration's log | `scripts/local/logs.sh migrate` | `scripts\local\logs.ps1 migrate` |
| Drop the database and migrate it again | `scripts/local/reset-db.sh` | `scripts\local\reset-db.ps1` |
| Remove it, data included | `scripts/local/infra-down.sh` | `scripts\local\infra-down.ps1` |

`reset-db` works on the stack of `scripts/local/stack-up.sh` too. Without `.env`, [`compose.yaml`](compose.yaml) also runs alone, on any OS, with the fixed passwords below (another port with `DB_PORT=55432`):

```bash
docker compose -f database/compose.yaml up --build --detach
docker compose -f database/compose.yaml logs migrate
docker compose -f database/compose.yaml down    # or any restart: an empty database again
```

| User | Password without `.env` | Use |
|---|---|---|
| `crowded` | `crowded` | Owns the schema; only Flyway uses it |
| `crowded_core` | `core` | Core: `DB_USERNAME=crowded_core`, `DB_PASSWORD=core` |
| `crowded_jobs` | `jobs` | Jobs |
| `crowded_notifications` | `notifications` | Notifications |

A service then runs with `DB_URL=jdbc:postgresql://localhost:5432/crowded` and its own user. These passwords are only for this throwaway database; the deployed environments get theirs from Doppler (SR-008).

**Tests:** what CI runs, on Linux (`scripts/ci/` is Linux-only; on macOS and Windows, rely on CI or WSL):

```bash
docker build -t crowded/database database
bash scripts/ci/test-database.sh crowded/database
```

They apply the migrations with and without the seed, run them a second time, which must change nothing, and check each service user's rights. The services' integration tests also run these migrations ([Adding a Migration](#adding-a-migration)).

### Demo Accounts

Every demo account's password is `crowded-demo`. The database stores it only as a bcrypt hash (SR-013).

| Email | Role |
|---|---|
| `admin@crowded.test` | Administrator |
| `equipa@ribeira-live.test` | Member of Ribeira Live (verified) |
| `geral@sons-do-minho.test` | Member of Associação Sons do Minho (pending verification) |
| `ana@crowded.test`, `bruno@crowded.test`, `carla@crowded.test` | Participants |

The demo data also has an event in every state (`PROPOSED` to `ENDED`), tickets and follows, event changes, alerts (sent and draft), queue times, weather forecasts, reports with confirmations and an incident, a survey, an interest poll, notifications and audit entries. The rows have fixed ids, grouped by kind in the file's header, so tests and apps can point at them. Times are relative to when the seed runs.

## Configuration

| Variable | Meaning |
|---|---|
| `DB_URL` | JDBC URL, e.g. `jdbc:postgresql://postgres:5432/crowded` |
| `DB_USERNAME`, `DB_PASSWORD` | The migration user, which owns the schema |
| `CORE_DB_PASSWORD`, `JOBS_DB_PASSWORD`, `NOTIFICATIONS_DB_PASSWORD` | Passwords of the service users. Letters and digits only |
| `SEED_DEMO_DATA` | `true` also applies `seed/`. Default `false` |

Each password can instead be read from a file named by `<NAME>_FILE`, such as `DB_PASSWORD_FILE=/run/secrets/postgres_password` in the Swarm stack. The image's arguments are Flyway's: `migrate` by default, or `info`, `validate`...

```bash
docker build -t crowded/database database
docker run --rm --network <network> -e DB_URL=... -e DB_USERNAME=... ... crowded/database info
```

## Adding a Migration

1. Add `migrations/V<next number>__<what it does>.sql`, for example `V3__add_device_token.sql`. Never edit a migration that is on `main`: Flyway refuses to run when an applied file changed. Fix it with a new version.
2. Write snake_case names, and the data model's types (`uuid` keys, `timestamptz`, `jsonb`, enum types). Update `docs/deliverables/diagrams/data-model.puml` in the same pull request.
3. A new table: add its owning service to `R__grants.sql`. Every service can already read it.
4. Keep it **compatible with the previous release**. During a deployment, and after a rollback, the previous release's services run against the new schema. Add tables, nullable columns or columns with a default. To rename or drop a column, or make one `NOT NULL`: first add the new column and deploy the code that writes it; drop the old one in a later release (expand, then contract).
5. Data that every environment needs, such as a new report category, goes in a migration. Demo data goes in `seed/R__demo_data.sql`, with `ON CONFLICT DO NOTHING`.
6. Test it: `bash scripts/ci/test-database.sh <image>` after `docker build -t <image> database` (what CI runs), and the integration tests of the services that use the table (`./mvnw verify` in each). Then update the JPA entities: the services only validate the schema (`ddl-auto=validate`) and fail at startup when it does not match.

## Where It Runs

- **Integration tests:** each service's `IntegrationTest` builds this image from `../database` and runs it with the demo data against its PostgreSQL container, then connects as that service's user. A write to a table the service does not own fails there as it would in production.
- **CI:** the Database workflow builds the image, scans it with Trivy and runs `scripts/ci/test-database.sh` on every change to this folder. On `main` it then pushes `<namespace>/crowded-database`. The Backend workflow also runs, because the services' integration tests use these migrations.
- **Deployed environments** (development, staging and production; only production for now): the `database` job of the Swarm stack. `deployment/remote-deploy.sh` runs it before Core, Jobs and Notifications get their new images; a failed migration stops the deployment ([`deployment/`](../deployment/README.md)).
