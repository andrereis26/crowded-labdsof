# Crowded Jobs

Jobs service of Crowded ([Technical Design](../docs/deliverables/technical-design.md), [ADR 0001](../docs/deliverables/adr/0001-three-backend-services.md)). For now it is an empty skeleton: no endpoints, entities or queues yet.

- **Responsibility:** background work: ticketing source synchronization, severe-weather checks and AI report grouping, on a schedule. It publishes what it detects to the broker; Core applies it.
- **Owns (writes):** sync snapshots.
- **Dependencies:** Spring Web MVC, Spring Data JPA with the PostgreSQL driver, Spring AMQP, Spring Data Redis, Spring Security with OAuth2 Resource Server (JWT).

## Stack

Same for `core/`, `jobs/` and `notifications/` ([ADR 0006](../docs/deliverables/adr/0006-technology-stack.md)):

- Java 25 (JDK 25, LTS) and Spring Boot 4.1.2, the latest stable release when the project was created.
- Maven 3.10.0 through the committed wrapper (`./mvnw`, or `mvnw.cmd` on Windows). Maven must run on JDK 25: set `JAVA_HOME` to a JDK 25 installation, or the build stops with a message saying so.
- groupId `com.crowded`, base package `com.crowded.jobs`.
- Formatting: Spotless with google-java-format. Lint: the compiler with `-Xlint:all` and `failOnWarning`, so any warning fails the build.

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
| `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL user of this service | – |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | RabbitMQ broker | –, `5672` |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | RabbitMQ user | – |
| `REDIS_HOST`, `REDIS_PORT` | Redis | –, `6379` |
| `REDIS_PASSWORD` | Redis password | empty (no auth) |
| `JWT_PUBLIC_KEY_LOCATION` | Core's public key that checks token signatures, e.g. `file:/run/secrets/jwt-public.pem` | – |
