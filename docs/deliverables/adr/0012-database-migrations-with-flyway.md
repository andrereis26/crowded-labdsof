# ADR 0012: Database migrations, users and demo data in one Flyway project

- Status: accepted
- Date: 2026-10-28
- Deciders: Crowded team

## Context

The three services share one PostgreSQL database in which each table has one owning service ([ADR 0002](0002-shared-database-with-table-ownership.md)). ADR 0002 leaves two things open: the ownership rule must be enforced with a database user per service, and schema changes must be coordinated between the services. Every environment (a developer's machine, the integration tests, CI and the shared environment) also needs the same schema, applied the same way, and the demo needs data from an empty database (Sprint 2 deliverable 3; NFR-013, NFR-016, SR-008, SR-013).

## Options considered

- **Where the migrations live:** in each service (Flyway or Liquibase in Spring Boot); in a Maven module the services depend on; in a separate top-level project with its own image.
- **Tool:** Flyway with plain SQL files; Liquibase.
- **Local database:** H2 in memory; a throwaway PostgreSQL container.
- **Demo data:** a script run by hand; versioned migrations; a separate Flyway location behind a flag.

## Decision

### One `database/` project with its own image

Migrations are plain SQL files in a top-level [`database/`](../../../database/README.md) folder, applied by **Flyway**, packaged as the **`crowded-database`** image. One history for the one shared database, with the grants next to the tables they protect.

- Rejected: Flyway in each service (three histories for one schema, and grants spread over three projects); a Maven module (the services would depend on a shared artifact, against the independent builds of [ADR 0011](0011-ci-pipeline-and-vulnerability-policy.md)); Liquibase (more verbose than SQL, and a schema rollback can lose data).
- The image is built on the same `eclipse-temurin:25-jre-alpine` as the services, with only the parts of the official Flyway image that PostgreSQL needs. The official image ships the drivers and plugins of every database Flyway supports, and their dependencies have fixable HIGH vulnerabilities that the image scan rejects.

### Files

| Location | File | Runs |
|---|---|---|
| `migrations/` | `V<n>__<description>.sql` | Once, in version order. Never edited after it is merged: a change is a new version |
| `migrations/` | `R__grants.sql` | After the versioned files, whenever it changes. The whole list of which service writes which table |
| `migrations/` | `beforeMigrate__create_service_users.sql` | Before every migrate (a Flyway callback). Creates the service users and sets their passwords |
| `seed/` | `R__demo_data.sql` | Only with the seed flag, whenever it changes |

Tables and columns are the [data model](../technical-design.md#data-model)'s names in snake_case. `User` becomes `app_user`, because `user` is reserved in SQL. The model's enums are PostgreSQL enum types.

### Users and table ownership

| User | Owns | Can |
|---|---|---|
| The migration user (`POSTGRES_USER`) | The schema and every table | Everything; only Flyway uses it |
| `crowded_core` | Core's tables in the data model | Read every table; insert, update and delete its own |
| `crowded_jobs` | None yet (its sync snapshots, when they get a table) | Read every table |
| `crowded_notifications` | `notification`, `user_notification_setting`, `event_notification_setting` | Read every table; insert, update and delete its own |

The service users own nothing and cannot create objects, so a schema change from a service fails, and so does a write to another service's table. New tables are readable by every service through default privileges; their writer is added to `R__grants.sql` in the same pull request. Foreign keys from Notifications' tables to Core's (`app_user`, `event`, `event_connection`) cascade, so Core can delete a user or an event: the cascade runs with the table owner's rights.

The passwords of the service users reach Flyway as placeholders filled from the environment, never from the repository (SR-008). In the shared environment they are Doppler secrets (`CORE_DB_PASSWORD`, `JOBS_DB_PASSWORD`, `NOTIFICATIONS_DB_PASSWORD`), stored as Swarm secrets and mounted as files in the migration job and in each service ([`deployment/`](../../../deployment/README.md)). They may hold only letters and digits, because they are written into an `ALTER ROLE` statement. Changing one in Doppler takes effect at the next deployment.

### The services only validate

Each service sets `spring.jpa.hibernate.ddl-auto=validate`: it never creates or changes the schema, and it fails at startup when its entities do not match it. No service has Flyway on its classpath.

### Demo data behind a flag

The demo data is a separate Flyway location, `seed/`, applied when **`SEED_DEMO_DATA=true`**. It is on locally, in the integration tests and in the shared environment; it is off by default, so no other environment gets demo accounts. It covers every Sprint 2 journey: verified, pending and rejected organizations, an event in every state, tickets, follows, alerts, reports, an incident and notifications. Every person in it is invented, its passwords are stored as bcrypt hashes (SR-013) and its rows have fixed ids. Reference data that every environment needs, such as the predefined report categories, is a versioned migration, not demo data.

### Where it runs

| Where | How |
|---|---|
| Local development | `database/compose.yaml`: a throwaway `postgres:18-alpine` on tmpfs, migrated and seeded on start, empty again after it stops. No H2: local runs, the tests and production use the same engine and SQL |
| Integration tests | Each service's `IntegrationTest` builds the image from `../database` with Testcontainers, runs it with the seed against its PostgreSQL container and connects as that service's user |
| CI | The Database workflow builds the image, scans it with Trivy and runs `scripts/ci/test-database.sh`: migrations with and without the seed, a second run that changes nothing, and each user's rights. `Database result` is the required check. On `main` it then pushes `crowded-database`, tagged like the apps' images |
| Shared environment | The `database` service of the Swarm stack, a one-shot `replicated-job`. `remote-deploy.sh` deploys it first, with Core, Jobs and Notifications still on their previous images, waits for it to complete and only then deploys their new images. A failed migration fails the deployment, and the previous release keeps running |

### Compatible with the previous release

`deployment/remote-rollback.sh` runs the previous images against the newer schema, and during a deployment the previous release keeps running while the migration runs. So every migration is **forward-only and compatible with the release before it**: expand first, contract in a later release. Adding a table, a nullable column or a column with a default is fine. Renaming or dropping a column, or making one `NOT NULL`, takes two releases: first the new column, written by the new code; then, once no deployed release uses the old one, the removal. The image ignores migrations it does not know (`*:future`), so an older image runs without error against a newer schema.

## Consequences

- One place for the schema, the users and the grants; the Database workflow and every service's integration tests check them on each change. The Backend workflow also runs when `database/` changes.
- Integration tests build the database image once per test run, which adds a few seconds to each service's integration tests.
- A migration that breaks the previous release cannot be undone by a rollback. Reviews check the expand and contract rule.
- Every new table needs a line in `R__grants.sql`, or its owning service cannot write it.
- The `main` ruleset must add `Database result` to its required checks, and Doppler needs the three service-user passwords before the next deployment.
- The service users' passwords are restricted to letters and digits.
