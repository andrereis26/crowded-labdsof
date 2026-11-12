# Crowded Core

Core service of Crowded ([Technical Design](../docs/deliverables/technical-design.md), [ADR 0001](../docs/deliverables/adr/0001-three-backend-services.md)). So far it registers participants and signs users in (`POST /auth/register`, `POST /auth/login`, #59), lets the platform administrator manage organizations and members (`/admin/...`, #140), publishes an organization's official event changes (`POST /events/{eventId}/changes`, #37) and relays its outbox to RabbitMQ; the other endpoints follow the [API Outline](../docs/deliverables/api-outline.md).

- **Responsibility:** domain rules, authentication (issues JWTs), REST API, Organization API, live event-page updates (SSE), AI ticket extraction and alert drafts. Applies the changes detected by Jobs.
- **Owns (writes):** users, organizations, events, connections, on-site information, weather, alerts, surveys, reports, incidents.
- **Dependencies:** Spring Web MVC, Validation, Spring Data JPA with the PostgreSQL driver, Spring AMQP, Spring Data Redis, Spring Security with OAuth2 Resource Server (JWT), MapStruct; ArchUnit in the tests.

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

`start` also works against the data stores of `scripts/local/stack-up.sh`, next to the stack's own Core. To run it from an IDE instead, give it the same variables; `/actuator/health` answers `UP` once it reaches PostgreSQL, RabbitMQ and Redis.

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

Tokens are signed with an RSA key pair: Core holds the private key, and every service validates with the public key ([ADR 0005](../docs/deliverables/adr/0005-authentication-in-core-with-jwt.md)).
