# ADR 0003: Use RabbitMQ for asynchronous messaging

- Status: accepted
- Date: 2026-10-11
- Deciders: Crowded team

## Context

Event changes must reach every connected user within 1 minute (NFR-001), be delivered at least once without duplicates (NFR-003), and failed messages must be retried and moved to a dead-letter queue (NFR-004). TR-003 requires a message broker between event synchronization and notifications.

## Options considered

1. **RabbitMQ**: queues, acknowledgements, retries and dead-letter queues built in; light enough for a single host; good Spring support; Team has experience with it.
2. **Apache Kafka**: replayable event log and high throughput, but heavy to run and more than this volume needs.
3. **Redis Streams**: reuses Redis ([ADR 0004](0004-redis-for-revocation-rate-limiting-and-cache.md)), but dead-lettering and retries must be built by hand.

## Decision

RabbitMQ. It covers the delivery guarantees we need out of the box. Messages carry ids only; consumers read current data from the database. Team feels confortable with it.

## Consequences

- At-least-once delivery means consumers must be idempotent (Notifications uses a deduplication key).
- One more stateful container to run and monitor (queue depth and dead-letter queue size in Grafana).