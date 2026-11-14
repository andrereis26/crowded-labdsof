# ADR 0007: Live updates with Server-Sent Events

- Status: accepted
- Date: 2026-10-11
- Deciders: Crowded team

## Context

Open apps and websites need fresh data without reloading: the event page must show organization updates within 30 seconds (NFR-008) and confidence changes within 30 seconds (NFR-019), and new notifications must appear as a live banner with an updated unread badge within seconds (FR-068). Browsers get no push in the MVP (FR-069), so this live channel is how web users are notified.

## Options considered

1. **Polling every ~15 seconds**: trivial and cache-friendly, but many requests that return nothing new, and banners arrive late.
2. **Server-Sent Events (SSE)**: the server streams updates over plain HTTP; one direction only, which is all these updates need; works through NGINX and in every browser.
3. **WebSocket**: two-way connection; more flexible, but needs its own protocol handling and reconnection logic.

## Decision

SSE, from two sources that each stream the data they own:

- **Core** streams event-page updates (status, crowd, queues, alerts, participant estimates).
- **Notifications** streams new notifications to the notification center (banner and unread badge).

SSE is chosen over WebSocket because updates only flow from server to client.

A stream stays on the instance that accepted it, but the change it reports may happen on another instance of the same service. The instances therefore tell each other through Redis publish/subscribe, on the Redis of [ADR 0004](0004-redis-for-revocation-rate-limiting-and-cache.md):

- After its transaction commits, the instance that made the change publishes only the id on a channel: `core:events:changed` with the event's id (Core first drops that event's cached page), and `notifications:created` with the id of each new notification.
- Every instance subscribes to its service's channel, reads the current data from the database and sends it to the streams it holds for that event or recipient.

Pub/sub keeps nothing: an instance that is not subscribed when a message is published misses it. That is acceptable because every stream can be rebuilt: the client reconnects and fetches the event page or the notification center again.

## Consequences

- Updates appear within seconds instead of after the next poll, so notifications can be tested end to end in the browser before mobile push exists.
- A client keeps up to two open connections (event page and notifications).
- NGINX must keep long-lived connections open (no response buffering, longer timeouts).
- Clients must reconnect after network drops and fetch what they missed (the notification center lists everything sent while offline, FR-067).
- Core and Notifications can run several instances, because the live updates reach every instance through Redis. If Redis is down, changes are still saved but nothing is sent live, and the event page cache expires on its own.
