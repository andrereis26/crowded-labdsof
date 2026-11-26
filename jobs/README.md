# Crowded Jobs

Jobs service of Crowded ([Technical Design](../docs/deliverables/technical-design.md), [ADR 0001](../docs/deliverables/adr/0001-three-backend-services.md)). It has no endpoints: it watches IPMA on a schedule and tells Core what changed.

- **Responsibility:** background work: IPMA weather warnings and forecasts, AI report grouping and periodic tasks, on a schedule. It publishes what it fetches to the broker; Core applies it.
- **Owns (writes):** sync snapshots (`weather_snapshot`).
- **Dependencies:** Spring Web MVC, Spring Data JPA with the PostgreSQL driver, Spring AMQP, Spring Data Redis, Spring Security with OAuth2 Resource Server (JWT), MapStruct; ArchUnit in the tests.

## Weather Watch

Every 5 minutes (`crowded.weather.interval`), `WeatherWatch` reads the districts of the events that have not ended or been cancelled and start within 5 days, and asks IPMA's open data ([Asynchronous Flows](../docs/deliverables/asynchronous-flows.md#4-weather-warning-flow)):

- `warnings_www.json` on every run, for the yellow, orange and red warnings of each district's warning area, so a new warning is detected within 10 minutes;
- `cities/daily/{globalIdLocal}.json` once an hour per district (`crowded.weather.forecast-interval`), for the forecast of its capital. A region uses its capital: Ponta Delgada and Funchal.

IPMA's times are UTC. Each call has a 10 s timeout and is tried again after 1 s, 2 s and 4 s; when IPMA still does not answer, nothing changes and the next run asks again. Data that does not parse or validate is rejected and logged for that district only (`crowded.weather.rejected`), and never replaces the last valid snapshot. A district's warnings or forecast that differ from its latest snapshot of that kind become a new `weather_snapshot` row (JSON in `content`, read by Core too) and a `WeatherUpdated` {`district`, `snapshotId`} on `crowded.events` with the routing key `weather.updated`. `published_at` stays null until RabbitMQ confirms the message, so an unconfirmed one is published again on the next run.

To try it with your own data, point `IPMA_URL` at a server that answers the same two paths, for example `python3 -m http.server` in a folder with `forecast/warnings/warnings_www.json` and `forecast/meteorology/cities/daily/1131200.json` (Porto).

## Stack

Same for `core/`, `jobs/` and `notifications/` ([ADR 0006](../docs/deliverables/adr/0006-technology-stack.md)):

- Java 25 (JDK 25, LTS) and Spring Boot 4.1.2, the latest stable release when the project was created.
- Maven 3.10.0 through the committed wrapper (`./mvnw`, or `mvnw.cmd` on Windows). Maven must run on JDK 25: set `JAVA_HOME` to a JDK 25 installation, or the build stops with a message saying so.
- groupId `com.crowded`, base package `com.crowded.jobs`.
- Formatting: Spotless with google-java-format. Lint: the compiler with `-Xlint:all` and `failOnWarning`, so any warning fails the build.

## Setup

**Prerequisites:** JDK 25 as `JAVA_HOME` (Maven itself comes with the committed wrapper) and Docker, for the infrastructure and the integration tests. [`scripts/local/setup.sh`](../scripts/local/setup.sh) (`setup.ps1` on Windows) checks them and creates `.env` ([Getting Started](../README.md#getting-started)).

**Configuration from `.env`:** `start` sets the variables of [Configuration](#configuration) for this service only: `DB_URL` from `DB_PORT` and `POSTGRES_DB`, `DB_USERNAME=crowded_jobs` with `DB_PASSWORD` from `JOBS_DB_PASSWORD`, `RABBITMQ_*` and `REDIS_*` on `127.0.0.1` with the ports and passwords of `.env`, `JWT_PUBLIC_KEY_LOCATION` pointing at the key of `JWT_PUBLIC_KEY_BASE64`, written to `.local/jwt/`, and `SERVER_PORT` from `JOBS_PORT` (8082).

**Running it alone,** from source, without the whole Docker stack:

| Step | Debian, macOS | Windows (PowerShell 7) |
|---|---|---|
| Start PostgreSQL (migrated, with the demo data), RabbitMQ and Redis | `scripts/local/infra-up.sh` | `scripts\local\infra-up.ps1` |
| Run Jobs (`./mvnw spring-boot:run`) on `http://localhost:8082/` | `scripts/local/start.sh jobs` | `scripts\local\start.ps1 jobs` |
| Follow its log | `scripts/local/logs.sh jobs` | `scripts\local\logs.ps1 jobs` |
| Stop it | `scripts/local/stop.sh jobs` | `scripts\local\stop.ps1 jobs` |
| Remove the infrastructure | `scripts/local/infra-down.sh` | `scripts\local\infra-down.ps1` |

`start` also works against the data stores of `scripts/local/stack-up.sh`, next to the stack's own Jobs. To run it from an IDE instead, give it the same variables; `/actuator/health` answers `UP` once it reaches PostgreSQL, RabbitMQ and Redis, and shows each one's status.

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
| `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL user of this service, `crowded_jobs`. It writes only the tables this service owns and cannot change the schema ([`database/`](../database/README.md)) | – |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | RabbitMQ broker | –, `5672` |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | RabbitMQ user | – |
| `REDIS_HOST`, `REDIS_PORT` | Redis | –, `6379` |
| `REDIS_PASSWORD` | Redis password | empty (no auth) |
| `JWT_PUBLIC_KEY_LOCATION` | Core's public key that checks token signatures, e.g. `file:/run/secrets/jwt-public.pem` | – |
| `IPMA_URL` | IPMA's open data, or a stand-in that answers the same paths | `https://api.ipma.pt/open-data` |
