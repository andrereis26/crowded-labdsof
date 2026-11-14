# ADR 0010: External integrations behind adapters

- Status: accepted (Firebase Cloud Messaging, implemented after in-app notifications); weather decided in [ADR 0015](0015-organization-as-the-only-event-source.md) (IPMA), maps in [ADR 0018](0018-openstreetmap-for-maps-and-geocoding.md) (OpenStreetMap), ticket extraction in [ADR 0020](0020-local-nuextract-for-ticket-extraction.md) (local NuExtract); vendors for the other AI tasks and the transport feed to be decided
- Date: 2026-10-11
- Deciders: Crowded team

## Context

Crowded depends on external providers: push delivery, an AI model (ticket extraction, alert drafts, report grouping), weather forecasts, and maps/transport. Mobile apps can only be woken up through the platform push services. Every client already gets in-app notifications over SSE ([ADR 0007](0007-live-updates-with-server-sent-events.md)); push is needed only for the mobile app when it is closed or in the background (FR-069). The AI workflows must keep working when the provider is down (NFR-006) and must never act on untrusted input (SR-005).

## Options considered

- **Push:** Firebase Cloud Messaging (FCM, forwards to Apple APNs); APNs and FCM directly; a paid push service.
- **AI, weather, maps/transport:** choose vendors now, or put each behind an adapter and choose later.

## Decision

- **Push: Firebase Cloud Messaging.** One integration covers Android and iOS; free; well supported in Flutter. It is added after in-app notifications, as a second delivery channel in the Notifications service. Browsers get no push in the MVP.
- **AI, weather and maps/transport: decided later.** Each is called through an adapter with a fake implementation for tests. AI output is validated against a fixed schema, and every AI feature has a non-AI fallback.

## Consequences

- Vendors can be compared and swapped without changing the services.
- Tests do not depend on external services.
- Until push is added, users only see notifications while the app or website is open; anything sent meanwhile waits in the notification center.
- The vendor choices remain open work; this ADR is updated when they are made.
