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

## Consequences

- Updates appear within seconds instead of after the next poll, so notifications can be tested end to end in the browser before mobile push exists.
- A client keeps up to two open connections (event page and notifications).
- NGINX must keep long-lived connections open (no response buffering, longer timeouts).
- Clients must reconnect after network drops and fetch what they missed (the notification center lists everything sent while offline, FR-067).
