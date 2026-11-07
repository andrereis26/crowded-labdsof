# ADR 0015: The organization as the only event source, and IPMA as the external integration

- Status: accepted
- Date: 2026-11-07
- Deciders: Crowded team
- Amends: [ADR 0001](0001-three-backend-services.md) (what Jobs does), [ADR 0010](0010-external-integrations.md) (weather vendor)

## Context

Until the Sprint 1 review, an event's official information could come from two sources: the organization, through the backoffice, and a ticketing source, which Jobs polled from a simulator. When both reported the same change, Core merged them into one change and notified it once; when they disagreed, it recorded a conflict (the former FR-078 and FR-079). That flow was planned for Sprint 2.

At the review, the professors found the scope too broad and the story hard to follow. The two-source merge was the hardest part to explain and to build, and it served only events whose organization had linked them to the ticketing source.

Crowded's model also became clearer: a festival or venue subscribes to Crowded instead of building its own app. It registers its events in the backoffice, and its participants connect with their ticket or an access code. Every event in Crowded therefore has an organization behind it.

The course still requires at least one integration with an external system or a realistic simulator (assignment, section 6.5), and one meaningful asynchronous workflow (section 6.3).

## Options considered

1. **Keep the ticketing source for the official fields.** The organization creates the event and links it to the ticketing event; date, time, venue and cancellation come only from synchronization and are locked in the backoffice. One source per field, no merge, but a simulator to build and the organization unable to publish its own changes.
2. **Ticketing source only for refund information.** The organization publishes every change; the simulator only confirms a cancellation later with refund details. Keeps an integration with little scope, but the simulator would exist mostly to satisfy the course.
3. **The organization as the only source, with IPMA as the external integration.** The organization publishes every change and its refund information. Jobs polls IPMA's public open data for weather warnings and forecasts, a real external system, which is also a disruption that does not depend on the organization.

## Decision

Option 3.

- **The organization is the only source of event information.** It registers each event (ticketed, with the event code printed on its tickets, or public, with an access code) and publishes every change: postponement, venue or time change, doors opening time and cancellation, with the refund information. There is no ticketing synchronization, no ticketing simulator and no merging of changes.
- **IPMA is the external integration.** Jobs polls IPMA's warnings and forecasts, which are public and need no key, and publishes `WeatherUpdated`. Core stores them per event and notifies orange and red warnings for the event's district ([Asynchronous Flows](../asynchronous-flows.md#4-weather-warning-flow)). It replaces the weather provider that [ADR 0010](0010-external-integrations.md) left open.
- **The AI provider and a GTFS transport feed** are the other external systems, behind adapters as in [ADR 0010](0010-external-integrations.md).
- **Jobs** keeps its place from [ADR 0001](0001-three-backend-services.md): it now polls IPMA, groups reports and runs the periodic tasks, instead of synchronizing the ticketing source.

## Consequences

- Each change has one source, so it is stored and notified once without a merge step. The *One change, one notification* flow of the Sprint 1 presentation is replaced by the flow in the [Asynchronous Flows](../asynchronous-flows.md).
- The asynchronous workflow is unchanged in shape: Core writes the change and `NotificationRequested` in one transaction, and Notifications delivers it through RabbitMQ.
- An event exists in Crowded only when an organization registers it. Crowded can no longer show official changes for events whose organization does not use it, so organizer adoption becomes the product's main risk ([Problem and Opportunity Report](../problem-and-opportunity-report.md#5-assumptions), A2).
- Without a ticketing integration, Crowded cannot check that an imported ticket is genuine. It links each ticket's QR code to one account per event and records the limitation in the [Security and Privacy Assessment](../security-and-privacy-assessment.md).
- No simulator is deployed. The demonstration uses the seed data and a demo script outside the product (TR-009).
- The database schema still has the ticketing columns and the poll tables of the previous scope; a forward-only migration removes them ([database](../../../database/README.md)).
- IPMA's format can change without notice; its adapter validates every response and keeps the last valid data (NFR-005).
