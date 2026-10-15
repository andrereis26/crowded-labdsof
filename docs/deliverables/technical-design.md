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
- **Proposed events:** an event proposed by an organization has `status = PROPOSED`, a `tentativePeriod` (e.g., "spring 2027") and no start time or venue yet, so those columns are nullable. Confirming it fills them in and sets `status = SCHEDULED`, recorded as an `EventChange` of type `CONFIRMATION`; withdrawing it is a `CANCELLATION` (FR-076).
- **Interest polls vs. surveys:** an `InterestPoll` is public (it has a shareable link, FR-080), can exist while the event is still proposed and has options written by the organization; an `EventSurvey` is only shown to users connected to a confirmed event (FR-051). Both keep the responder's `userId` only to enforce one answer per user and let them change it; organizations only ever see totals (SR-019).
- **One change, two sources:** when an event is linked to the ticketing source (`Event.ticketingSourceEventId`, FR-079), a change published by the organization and the same change detected by Jobs are stored as one `EventChange`; the second source is recorded in `corroboratingSourceType` and `corroboratedAt`, and no second notification is sent (FR-078).
- **Disruptions:** `EventChange` and `Alert` carry a `severity` (`CRITICAL`, `WARNING`, `INFO`) that decides how prominently they are shown (FR-074). It is stored rather than derived from the type, because the same type can have different severities: a start-time change is critical, while a doors-opening change (`Event.doorsOpenAt`, FR-072) is a warning. A sent alert is active until `resolvedAt` is set, when the organization resolves it (FR-073); its status stays `SENT`.
- **Recovery after a change:** `EventChange.ticketStillValid` and `EventChange.refundInfoUrl` tell participants what a cancellation, postponement or relocation means for them (FR-075). Both are nullable, because the source does not always provide them, and the app then says that the information is not available instead of guessing.
- **Notification cause:** `Notification.causeId` points to the `EventChange`, `Alert` or `Incident` that triggered it, and the notification type says which. It has no foreign key because those tables belong to Core and the id arrives in the message ([ADR 0002](adr/0002-shared-database-with-table-ownership.md), [ADR 0003](adr/0003-rabbitmq-for-asynchronous-messaging.md)). It makes each notification traceable to its disruption and lets us measure delivery time against NFR-001.

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

## Degraded Operation

A disruption can also affect Crowded itself. Each scenario below keeps the principal workflows running and tells users what is limited:

| Unavailable | What keeps working | What users see | Requirement |
|---|---|---|---|
| Push notification provider | Notifications are stored in the notification center and shown as live banners on open clients; Notifications retries the push with backoff and never sends twice | Every notification in the app, even without a push | NFR-020 |
| Network at the venue | Ticket, entry code, doors opening time, latest change and active alerts are cached on the device | The cached information, with the time of the last synchronization | FR-037 |
| Ticketing source | Jobs keeps the last valid data and rejects invalid data | The time of the last successful synchronization | NFR-005 |
| AI service | Manual ticket entry, manually written alerts, rule-based grouping of reports | The manual form or editor instead of the AI suggestion | NFR-006, FR-053 |

The final presentation demonstrates the **push provider failure**: with the provider switched off in the deployed environment, a change published by the ticketing simulator still reaches participants through the notification center and the live banner, and the failed push attempts appear in the logs and in the operational dashboard.
