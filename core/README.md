# Crowded Core

Core service of Crowded ([Technical Design](../docs/deliverables/technical-design.md), [ADR 0001](../docs/deliverables/adr/0001-three-backend-services.md)). For now it is an empty skeleton: no endpoints, entities or queues yet.

- **Responsibility:** domain rules, authentication (issues JWTs), REST API, Organization API, live event-page updates (SSE), AI ticket extraction and alert drafts. Applies the changes detected by Jobs.
- **Owns (writes):** users, organizations, events, connections, on-site information, alerts, surveys, polls, reports, incidents.
- **Dependencies:** Spring Web MVC, Validation, Spring Data JPA with the PostgreSQL driver, Spring AMQP, Spring Data Redis, Spring Security with OAuth2 Resource Server (JWT).

## Stack

Same for `core/`, `jobs/` and `notifications/`:

- Java 25 (LTS) and Spring Boot 4.1.2, built with Gradle 9.8.2 (Kotlin DSL) through the committed wrapper (`./gradlew`).
- Gradle needs a JDK 17 or later to start. It downloads JDK 25 itself (into `~/.gradle/jdks`) if it is not installed, for compiling and for running the build (`gradle/gradle-daemon-jvm.properties`).
- Base package: `pt.ipp.isep.crowded.core`.
- Formatting: Spotless with google-java-format. Lint: the compiler with `-Xlint:all -Werror`, so any warning fails the build.

## Commands

```bash
./gradlew build           # compile, unit tests and spotlessCheck
./gradlew test            # unit tests only
./gradlew spotlessApply   # format the code
./gradlew spotlessCheck   # fail on unformatted files, naming them
./gradlew lint            # compile main and test code; warnings fail
```

The application does not start yet without PostgreSQL, RabbitMQ and Redis; running it against them comes with the walking skeleton (#11).

## Configuration

`src/main/resources/application.yml` reads every setting from the environment, and no credentials or keys are committed (SR-008, NFR-016).

| Variable | Used for | Default |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC URL, e.g. `jdbc:postgresql://postgres:5432/crowded` | – |
| `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL user of this service | – |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | RabbitMQ broker | –, `5672` |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | RabbitMQ user | – |
| `REDIS_HOST`, `REDIS_PORT` | Redis | –, `6379` |
| `REDIS_PASSWORD` | Redis password | empty (no auth) |
| `JWT_PUBLIC_KEY_LOCATION` | Public key that checks token signatures, e.g. `file:/run/secrets/jwt-public.pem` | – |
| `JWT_PRIVATE_KEY_LOCATION` | Private key Core signs tokens with (`crowded.jwt.private-key-location`). Only Core gets it. | – |

Tokens are signed with an RSA key pair: Core holds the private key, and every service validates with the public key ([ADR 0005](../docs/deliverables/adr/0005-authentication-in-core-with-jwt.md)).
