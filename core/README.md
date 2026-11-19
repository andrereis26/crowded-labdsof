# Crowded Core

Core service of Crowded ([Technical Design](../docs/deliverables/technical-design.md), [ADR 0001](../docs/deliverables/adr/0001-three-backend-services.md)). It sends its messages to the other services through RabbitMQ and serves the endpoints below. The [API Outline](../docs/deliverables/api-outline.md) describes each of them in detail.

| Endpoint | What it does | Story |
|---|---|---|
| `POST /auth/register`, `POST /auth/login` | Registers participants and signs users in | #59 |
| `POST /auth/password` | A member replaces their temporary password at the first sign-in | #141 |
| `GET /admin/organizations`, `POST /admin/organizations` | The platform administrator lists and creates organizations | #140 |
| `POST /admin/organizations/{organizationId}/members`, `PATCH /admin/members/{userId}` | The platform administrator creates, deactivates and reactivates members, and issues new temporary passwords | #140 |
| `POST /events`, `PATCH /events/{eventId}`, `DELETE /events/{eventId}`, `GET /organizations/me/events` | An organization creates, corrects, deletes and lists its events | #36 |
| `POST /events/{eventId}/changes` | An organization publishes an official change to its event | #37 |
| `PUT /events/{eventId}/crowd` | An organization publishes its event's crowd level, which connected participants see live | #39 |
| `GET /events/{eventId}`, `GET /events/{eventId}/changes`, `GET /events/{eventId}/live` | A connected participant or the organization reads the event page and its change history, and follows the page live | #34 |
| `PUT`, `DELETE` and `GET /events/{eventId}/image` | Adds, removes and reads an event's image | #172 |
| `POST /tickets` | Imports a ticket from its PDF | #20 |
| `POST /connections` | Connects a participant to a public event with its access code | #139 |

- **Responsibility:** domain rules, authentication (issues JWTs), REST API, Organization API, live event-page updates (SSE), AI ticket extraction and alert drafts. Applies the changes detected by Jobs.
- **Owns (writes):** users, organizations, events, connections, on-site information, weather, alerts, surveys, reports, incidents.
- **Dependencies:** Spring Web MVC, Validation, Spring Data JPA with the PostgreSQL driver, Spring AMQP, Spring Data Redis, Spring Security with OAuth2 Resource Server (JWT), MapStruct, Apache PDFBox and ZXing (reading imported tickets and their QR codes); ArchUnit in the tests.

## Stack

Same for `core/`, `jobs/` and `notifications/` ([ADR 0006](../docs/deliverables/adr/0006-technology-stack.md)):

- Java 25 (JDK 25, LTS) and Spring Boot 4.1.2, the latest stable release when the project was created.
- Maven 3.10.0 through the committed wrapper (`./mvnw`, or `mvnw.cmd` on Windows). Maven must run on JDK 25: set `JAVA_HOME` to a JDK 25 installation, or the build stops with a message saying so.
- groupId `com.crowded`, base package `com.crowded.core`.
- Formatting: Spotless with google-java-format. Lint: the compiler with `-Xlint:all` and `failOnWarning`, so any warning fails the build.

## Setup

**Prerequisites:** JDK 25 as `JAVA_HOME` (Maven itself comes with the committed wrapper) and Docker, for the infrastructure and the integration tests. [`scripts/local/setup.sh`](../scripts/local/setup.sh) (`setup.ps1` on Windows) checks them and creates `.env` ([Getting Started](../README.md#getting-started)).

**Configuration from `.env`:** `start` sets the variables of [Configuration](#configuration) for this service only: `DB_URL` from `DB_PORT` and `POSTGRES_DB`, `DB_USERNAME=crowded_core` with `DB_PASSWORD` from `CORE_DB_PASSWORD`, `RABBITMQ_*` and `REDIS_*` on `127.0.0.1` with the ports and passwords of `.env`, `JWT_PUBLIC_KEY_LOCATION` and `JWT_PRIVATE_KEY_LOCATION` pointing at the keys of `JWT_PUBLIC_KEY_BASE64` and `JWT_PRIVATE_KEY_BASE64`, written to `.local/jwt/`, and `SERVER_PORT` from `CORE_PORT` (8081).

**Running it alone,** from source, without the whole Docker stack:

| Step | Debian, macOS | Windows (PowerShell 7) |
|---|---|---|
| Start PostgreSQL (migrated, with the demo data), RabbitMQ and Redis | `scripts/local/infra-up.sh` | `scripts\local\infra-up.ps1` |
| Run Core (`./mvnw spring-boot:run`) on `http://localhost:8081/` | `scripts/local/start.sh core` | `scripts\local\start.ps1 core` |
| Follow its log | `scripts/local/logs.sh core` | `scripts\local\logs.ps1 core` |
| Stop it | `scripts/local/stop.sh core` | `scripts\local\stop.ps1 core` |
| Remove the infrastructure | `scripts/local/infra-down.sh` | `scripts\local\infra-down.ps1` |

`start` also works against the data stores of `scripts/local/stack-up.sh`, next to the stack's own Core. To run it from an IDE instead, give it the same variables; `/actuator/health` answers `UP` once it reaches PostgreSQL, RabbitMQ and Redis, and shows each one's status.

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

## Code Layout

Onion architecture ([ADR 0016](../docs/deliverables/adr/0016-onion-architecture-for-backend-services.md)), one package per module under `com.crowded.core` (`event`, `user`, `organization`, `audit`, `outbox`, and `shared` for what they all use). Inside a module, dependencies only point inwards:

| Layer | Package | Holds | May use |
|---|---|---|---|
| Domain | `domain.model`, `domain.exception` | Entities and aggregate roots (classes that hold their rules), value objects (records that validate themselves), domain events, enums, the refusals (`NotFoundException`, `RuleViolationException`, ...) | Nothing else |
| | `domain.service`, `domain.repository` | Rules that span several aggregates or need a port, and the repository ports (interfaces) | The domain model |
| Application | `application.service`, `application.dto` | Use cases (`@Service`, `@Transactional`), their commands and results | The domain |
| Adapters | `api.controller`, `api.dto`, `api.mapper` | REST controllers, request and response DTOs, MapStruct mappers to the application DTOs | Application, domain |
| | `infrastructure.persistence.entity`, `.repository`, `.mapper` | JPA entities, Spring Data repositories with the adapters that implement the ports, MapStruct mappers to the domain | Domain |
| | `infrastructure.messaging`, `infrastructure.config` | The outbox relay to RabbitMQ; security and clock configuration | Domain |

The domain is a rich model ([ADR 0017](../docs/deliverables/adr/0017-rich-domain-model.md)): an entity is a class whose factories and methods check its invariants (`UserAccount`, `Event`), a value object is a record that cannot be built invalid (`Email`, `Schedule`, `Venue`), and an application service only orchestrates. The domain uses no framework: no Spring, JPA, validation, Jackson or MapStruct. Adapters never use each other. `OnionArchitectureTest` (ArchUnit) checks these rules on every build, and that entities are classes without public constructors or setters. The `ApiExceptionHandler` turns a domain refusal into an RFC 9457 problem with its `code`. Mappers are MapStruct interfaces, generated at compile time as Spring beans. A target property a mapper leaves unmapped fails the build.

**Who may see or manage an event:** every endpoint about one event starts with `EventAccess`. `viewable(userId, eventId)` returns the event to an active member of the organization that owns it or to a participant connected to it, and refuses anyone else with `403 NOT_CONNECTED`. `checkManager(userId, event)` lets through only an active member of its organization (`403 NOT_EVENT_ORGANIZATION`). The role and organization come from the database, never from the token; the rules themselves are on `Event` (`checkCanBeSeenBy`, `checkCanBeManagedBy`).

**Event pages:** `EventPages` builds the page and keeps it in Redis as JSON, shared by every instance ([ADR 0004](../docs/deliverables/adr/0004-redis-for-revocation-rate-limiting-and-cache.md)); `EventViewService` checks `EventAccess` on every request before it reads the cache. A use case that changes what participants see calls the `EventUpdates` port: once the transaction commits, `RedisEventUpdates` drops the cached page and announces the event on the `core:events:changed` channel, and every instance sends the new page to the `GET /events/{eventId}/live` streams it holds ([ADR 0007](../docs/deliverables/adr/0007-live-updates-with-server-sent-events.md)). A missed drop lasts at most the cache's ten minutes.

## Configuration

`src/main/resources/application.yml` reads every setting from the environment, and no credentials or keys are committed (SR-008, NFR-016).

| Variable | Used for | Default |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC URL, e.g. `jdbc:postgresql://postgres:5432/crowded` | – |
| `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL user of this service, `crowded_core`. It writes only the tables this service owns and cannot change the schema ([`database/`](../database/README.md)) | – |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | RabbitMQ broker | –, `5672` |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | RabbitMQ user | – |
| `REDIS_HOST`, `REDIS_PORT` | Redis | –, `6379` |
| `REDIS_PASSWORD` | Redis password | empty (no auth) |
| `JWT_PUBLIC_KEY_LOCATION` | Public key that checks token signatures, e.g. `file:/run/secrets/jwt-public.pem` | – |
| `JWT_PRIVATE_KEY_LOCATION` | Private key Core signs tokens with (`crowded.jwt.private-key-location`). Only Core gets it. | – |
| `CROWDED_TICKETS_ENTRYCODEKEY` | Base64 256-bit key that encrypts the entry codes of imported tickets with AES-GCM (`crowded.tickets.entry-code-key`). Without it, Core starts but refuses ticket imports (503 `TICKET_IMPORT_UNAVAILABLE`). Locally it is `TICKET_ENTRY_CODE_KEY` of `.env`, which `setup` creates; deployments read the Doppler secret of the same name. | – |
| `CROWDED_AI_GROQ_APIKEY` | Groq API key, so Core reads imported tickets with AI (`crowded.ai.groq.api-key`, [ADR 0020](../docs/deliverables/adr/0020-groq-for-ticket-extraction.md)). Without it, every extraction fails and the participant types the event code. Locally it is the optional `GROQ_API_KEY` of `.env`; deployments read the Doppler secret of the same name. The model is `crowded.ai.groq.model` | – |

Tokens are signed with an RSA key pair: Core holds the private key, and every service validates with the public key ([ADR 0005](../docs/deliverables/adr/0005-authentication-in-core-with-jwt.md)).
