# Technical Design

This document describes how Crowded is structured and deployed. The domain model is in the [README](/README.md#domain-model), and the reasoning behind each choice is in the [Architecture Decision Records](adr/).

The diagram sources are in [`diagrams/`](diagrams/) (PlantUML).

## Logical View (Level 2)

![Crowded — Logical View (Level 2)](diagrams/logical-view-level-2.svg)

The system is split into two client apps, a router, three backend services and three pieces of infrastructure. External systems sit outside the boundary and are reached through ports.

| Component | Responsibility | Owns (writes) |
|---|---|---|
| **Participant App** | Participant app for mobile and the web (Flutter). Calls the maps/transport and weather providers directly for display. | – |
| **Backoffice Web App** | Organizer and administrator app. | – |
| **Router (NGINX)** | Single entry point for both apps and the Organization API: TLS and routing. | – |
| **Core** | Domain rules, authentication (issues JWTs), REST API, Organization API, live event-page updates (SSE), AI ticket extraction and alert drafts. Applies changes detected by Jobs. | Users, organizations, events, connections, on-site information, alerts, surveys, polls, reports, incidents |
| **Jobs** | All background work: ticketing source synchronization, severe-weather checks, AI report grouping. | Sync snapshots |
| **Notifications** | Fan-out to connected users, preferences, deduplication, notification center and live banners (SSE); push to the mobile app later. | Notifications, device tokens |
| **Message Broker** | Asynchronous messages between Core, Jobs and Notifications, with retries and a dead-letter queue. | – |
| **Database** | One PostgreSQL database. Each table has one owning (writing) service; other services may only read it. | – |
| **Cache** | Token revocation list, rate-limit counters and read cache. | – |

Synchronous traffic goes through the Router to Core and Notifications. Everything else between services goes through the Message Broker: for example, Jobs publishes a detected event change, Core applies it and publishes `EventChanged`, and Notifications delivers it.

## Data Model

![Crowded - Data Model](diagrams/data-model.svg)

Based on the [Domain Model](../../README.md#domain-model), the diagram specifies attributes, keys and constraints using PostgreSQL data types and conventions. PostgreSQL was selected as the database in [ADR 0002](adr/0002-shared-database-with-table-ownership.md).

- **Keys and uniqueness:** `PK` identifies the primary key, `FK` a foreign key, and `UK` a unique constraint. Multiple `UK` attributes in one entity form a composite unique constraint.
- **Entity structure:** an event's location and crowd status are represented by columns on `Event`, while information that can have multiple entries, such as queue times and weather forecasts, is modeled as separate entities.
- **Nullable columns:** `[NULL]` marks a column that may have no value.
- **Data labels:** `[PERSONAL]`, `[PRIVACY-SENSITIVE]` and `[CREDENTIAL]` label personal, sensitive or credential data. `[MAY CONTAIN PERSONAL DATA]` marks free-text fields that may include personal data. `[ENCRYPTED]` marks data specified to be stored encrypted.
- **Ownership:** entities are grouped by their owning component. As specified in [ADR 0002](adr/0002-shared-database-with-table-ownership.md), each table has one owning service that may write to it, while other services may read it.
- **Enumerated values:** enum types represent fields that can take one of a defined set of values.

## Deployment View

![Crowded — Deployment View](diagrams/deployment-view.svg)

- Everything runs on **one AWS EC2 instance** inside a VPC, provisioned with **Terraform**. The instance type is a variable, so it can grow without redesign.
- The instance runs a **single-node Docker Swarm**. Each box in the diagram is a container (Swarm service) with a health check; Swarm restarts failed containers.
- Only the **router (NGINX)** publishes ports (443). All other containers are reachable only on internal overlay networks.
- The **web-app** container serves the static bundles of the participant web app and the backoffice.
- The **simulators** (ticketing source, entrance counter) run in the same Swarm as separate services, so they can be deployed and replaced independently.
- **Observability:** Prometheus scrapes the services and RabbitMQ; Alloy collects container logs (to Loki) and Postgres, Redis and host metrics (to Prometheus); Grafana shows both, behind the router at `/grafana`.
- Stateful containers keep their data in Docker volumes on an **EBS volume**.
- Images are built by GitHub Actions and pushed to Docker Hub; secrets come from Doppler and are mounted as Docker Swarm secrets.
