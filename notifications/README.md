# Crowded Notifications

Notifications service of Crowded ([Technical Design](../docs/deliverables/technical-design.md), [ADR 0001](../docs/deliverables/adr/0001-three-backend-services.md)). It turns Core's `NotificationRequested` messages into notifications for the official changes, the operational alerts and IPMA's weather warnings, and serves each participant's notification center and live stream; the other types come with their stories.

- **Responsibility:** fan-out to connected users, preferences, deduplication, the notification center and live banners (SSE); push to the mobile app later.
- **Owns (writes):** notifications, device tokens.
- **Dependencies:** Spring Web MVC, Validation, Spring Data JPA with the PostgreSQL driver, Spring AMQP, Spring Data Redis, Spring Security with OAuth2 Resource Server (JWT).

## Messages

The service follows the onion layout of [`core/`](../core/README.md#code-layout), checked by `OnionArchitectureTest`; the `notification` module holds everything below.

| Queue | Bound to | Consumes |
|---|---|---|
| `notifications`, durable | `crowded.events`, routing key `notification.requested` | Core's `NotificationRequested` ([API Outline](../docs/deliverables/api-outline.md#messages)) |
| `notifications.dlq`, durable | the dead-letter queue of `notifications` | messages that kept failing, kept for inspection and replay |

For an official change (`OFFICIAL_CHANGE`), the service reads the change from Core's `event_change` and stores one `notification` per participant connected to the event through a ticket or the access code who did not turn official changes off, for the event or overall (FR-013, FR-041, FR-065). The title and body, in European Portuguese, say what kind of change it is and the new venue, and, for a ticket holder, whether the ticket stays valid and where to ask for a refund; the organization's message comes last (FR-075). The new date and time are not written into the text: they stay in the change (`cause_id`), which the clients read and show in the participant's time zone. The source shown to the participant is the organization, which the type implies.

For an operational alert (`OPERATIONAL_ALERT`), it reads the alert from Core's `alert` and notifies the same participants, except those who turned operational alerts off, for the event or overall (FR-020, FR-041, FR-065). The title is the alert's kind ("Entrada fechada", "Lotação esgotada", "Atraso no horário", "Alerta da organização") and the body the organization's message; every participant reads the same text. A participant who turned them off still sees the alert on the event page, which Core sends.

For a weather warning (`WEATHER_WARNING`), it reads the warning from Core's `weather_warning`, with its event's district, and notifies the same participants, except those who turned weather warnings off (FR-036, FR-041). Core only asks for orange and red warnings, once per level. The title is the level and what it warns of ("Aviso laranja de vento"), with "Crítico ·" before a red one (FR-074), and the body IPMA's text and when the warning is in force, in the district's time ("Em vigor das 18:00 de 27/11 às 03:00 de 28/11."). The source shown to the participant is IPMA, which the type implies.

- **Once per participant (NFR-003):** the `deduplication_key` is the participant plus the message's `messageId`, and a notification whose key exists is skipped, so a redelivered message notifies no one twice.
- **Retries and dead letters (NFR-004):** a failed message is retried after 5 s, 15 s and 30 s, then rejected into `notifications.dlq`. A message that cannot succeed, because it is not valid JSON or its type has no text yet, goes there at once. The message is acknowledged only after its notifications are committed.

## Notification center and live stream

The API is in the [API Outline](../docs/deliverables/api-outline.md#notification-center-notifications); the router serves it under `/api/notifications/`. Every endpoint needs a token, in the `Authorization` header or Core's `crowded_access` cookie, that is not revoked or signed out, and a change must come from the host it was sent to ([ADR 0021](../docs/deliverables/adr/0021-browser-sessions-in-cookies-with-refresh-tokens.md)). A participant only ever sees and reads their own notifications (SR-003).

- **Center:** `GET /notifications` lists them newest first with the event's name, read from Core's `event`, and the unread count; `POST /notifications/{id}/read` stamps `read_at` once.
- **Live stream (ADR 0007):** `GET /notifications/stream` is a server-sent event stream. When a message stores new notifications, each one is announced after the transaction commits on the Redis channel `notifications:created`, which every instance subscribes to; the instance holding the recipient's streams sends it and stamps `delivered_at` the first time. A notification that already existed is not announced again, so a redelivered message shows no second banner. A comment every 25 s keeps idle connections open, and the stream ends when the token expires or the service stops; the client reconnects and fetches what it missed from the center.

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

`start` also works against the data stores of `scripts/local/stack-up.sh`, next to the stack's own Notifications. To run it from an IDE instead, give it the same variables; `/actuator/health` answers `UP` once it reaches PostgreSQL, RabbitMQ and Redis, and shows each one's status.

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

The `Dockerfile` builds the service and runs it on a JRE as a non-root user; its health check calls `/actuator/health/readiness`. Health and `/actuator/prometheus` are the only endpoints that need no token; the router keeps them inside the Swarm. Logs are JSON lines with the trace id of the request or message, and the metrics include request latency and errors ([Observability](../deployment/observability/README.md#what-the-services-give)). The application does not start without PostgreSQL, RabbitMQ and Redis; [`deployment/`](../deployment/README.md) runs it with them.

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
