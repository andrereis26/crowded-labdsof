# Crowded Notifications

Notifications service of Crowded ([Technical Design](../docs/deliverables/technical-design.md), [ADR 0001](../docs/deliverables/adr/0001-three-backend-services.md)). It turns Core's `NotificationRequested` messages into notifications for the official changes; the notification center, the live stream and the other types come with their stories.

- **Responsibility:** fan-out to connected users, preferences, deduplication, the notification center and live banners (SSE); push to the mobile app later.
- **Owns (writes):** notifications, device tokens.
- **Dependencies:** Spring Web MVC, Validation, Spring Data JPA with the PostgreSQL driver, Spring AMQP, Spring Data Redis, Spring Security with OAuth2 Resource Server (JWT).

## Messages

The service follows the onion layout of [`core/`](../core/README.md#code-layout), checked by `OnionArchitectureTest`; the `notification` module holds everything below.

| Queue | Bound to | Consumes |
|---|---|---|
| `notifications`, durable | `crowded.events`, routing key `notification.requested` | Core's `NotificationRequested` ([API Outline](../docs/deliverables/api-outline.md#messages)) |
| `notifications.dlq`, durable | the dead-letter queue of `notifications` | messages that kept failing, kept for inspection and replay |

For an official change (`OFFICIAL_CHANGE`), the service reads the change from Core's `event_change` and stores one `notification` per participant connected to the event through a ticket or the access code who did not turn official changes off, for the event or overall (FR-013, FR-041, FR-065). The title and body, in European Portuguese, say what changes: the new date, time or venue, and, for a ticket holder, whether the ticket stays valid and where to ask for a refund; the organization's message comes last (FR-075). The source shown to the participant is the organization, which the type implies.

- **Once per participant (NFR-003):** the `deduplication_key` is the participant plus the message's `messageId`, and a notification whose key exists is skipped, so a redelivered message notifies no one twice.
- **Retries and dead letters (NFR-004):** a failed message is retried after 5 s, 15 s and 30 s, then rejected into `notifications.dlq`. A message that cannot succeed, because it is not valid JSON or its type has no text yet, goes there at once. The message is acknowledged only after its notifications are committed.

## Stack

Same for `core/`, `jobs/` and `notifications/` ([ADR 0006](../docs/deliverables/adr/0006-technology-stack.md)):

- Java 25 (JDK 25, LTS) and Spring Boot 4.1.2, the latest stable release when the project was created.
- Maven 3.10.0 through the committed wrapper (`./mvnw`, or `mvnw.cmd` on Windows). Maven must run on JDK 25: set `JAVA_HOME` to a JDK 25 installation, or the build stops with a message saying so.
- groupId `com.crowded`, base package `com.crowded.notifications`.
- Formatting: Spotless with google-java-format. Lint: the compiler with `-Xlint:all` and `failOnWarning`, so any warning fails the build.

## Setup

**Prerequisites:** JDK 25 as `JAVA_HOME` (Maven itself comes with the committed wrapper) and Docker, for the infrastructure and the integration tests. [`scripts/local/setup.sh`](../scripts/local/setup.sh) (`setup.ps1` on Windows) checks them and creates `.env` ([Getting Started](../README.md#getting-started)).

**Configuration from `.env`:** `start` sets the variables of [Configuration](#configuration) for this service only: `DB_URL` from `DB_PORT` and `POSTGRES_DB`, `DB_USERNAME=crowded_notifications` with `DB_PASSWORD` from `NOTIFICATIONS_DB_PASSWORD`, `RABBITMQ_*` and `REDIS_*` on `127.0.0.1` with the ports and passwords of `.env`, `JWT_PUBLIC_KEY_LOCATION` pointing at the key of `JWT_PUBLIC_KEY_BASE64`, written to `.local/jwt/`, and `SERVER_PORT` from `NOTIFICATIONS_PORT` (8083).

**Running it alone,** from source, without the whole Docker stack:

| Step | Debian, macOS | Windows (PowerShell 7) |
|---|---|---|
| Start PostgreSQL (migrated, with the demo data), RabbitMQ and Redis | `scripts/local/infra-up.sh` | `scripts\local\infra-up.ps1` |
| Run Notifications (`./mvnw spring-boot:run`) on `http://localhost:8083/` | `scripts/local/start.sh notifications` | `scripts\local\start.ps1 notifications` |
| Follow its log | `scripts/local/logs.sh notifications` | `scripts\local\logs.ps1 notifications` |
| Stop it | `scripts/local/stop.sh notifications` | `scripts\local\stop.ps1 notifications` |
| Remove the infrastructure | `scripts/local/infra-down.sh` | `scripts\local\infra-down.ps1` |

`start` also works against the data stores of `scripts/local/stack-up.sh`, next to the stack's own Notifications. To run it from an IDE instead, give it the same variables; `/actuator/health` answers `UP` once it reaches PostgreSQL, RabbitMQ and Redis.

**Tests:** `./mvnw verify` from this folder (`mvnw.cmd verify` on Windows) runs everything CI checks; the integration tests need Docker running, and `./mvnw verify -DskipITs` skips them. See [Commands](#commands).

## Commands

```bash
./mvnw verify             # everything CI checks: unit tests with 80% line coverage (JaCoCo),
                          # integration tests, SpotBugs and spotless:check
./mvnw verify -DskipITs   # without the integration tests (no Docker needed)
./mvnw test               # unit tests only
./mvnw spotless:apply     # format the code
./mvnw spotless:check     # fail on unformatted files, naming them
./mvnw test-compile       # lint: compile main and test code; warnings fail
./mvnw compile spotbugs:check  # static analysis; false positives go in spotbugs-exclude.xml
```

Unit tests are `*Test` classes; integration tests are `*IT` classes that extend `IntegrationTest`, which starts PostgreSQL, RabbitMQ and Redis with Testcontainers (Docker must be running). Read the rules in `IntegrationTest` before adding one: all of them share one Spring context, and a test that changes the database, broker or cache takes the `SHARED_STATE` lock ([ADR 0011](../docs/deliverables/adr/0011-ci-pipeline-and-vulnerability-policy.md#integration-tests)).

The `Dockerfile` builds the service and runs it on a JRE as a non-root user; its health check calls `/actuator/health/readiness`, the only endpoint that needs no token. The application does not start without PostgreSQL, RabbitMQ and Redis; [`deployment/`](../deployment/README.md) runs it with them.

## Configuration

`src/main/resources/application.yml` reads every setting from the environment, and no credentials or keys are committed (SR-008, NFR-016).

| Variable | Used for | Default |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC URL, e.g. `jdbc:postgresql://postgres:5432/crowded` | – |
| `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL user of this service, `crowded_notifications`. It writes only the tables this service owns and cannot change the schema ([`database/`](../database/README.md)) | – |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | RabbitMQ broker | –, `5672` |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | RabbitMQ user | – |
| `REDIS_HOST`, `REDIS_PORT` | Redis | –, `6379` |
| `REDIS_PASSWORD` | Redis password | empty (no auth) |
| `JWT_PUBLIC_KEY_LOCATION` | Core's public key that checks token signatures, e.g. `file:/run/secrets/jwt-public.pem` | – |
