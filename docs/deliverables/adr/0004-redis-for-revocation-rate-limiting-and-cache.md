# ADR 0004: Use Redis for token revocation, rate limiting and caching

- Status: accepted
- Date: 2026-10-11
- Deciders: Crowded team

## Context

Signed-out or deleted users must not keep using their tokens (FR-042). Sign-up, sign-in, reports and confirmations need rate limits (SR-011, SR-015). Event status and on-site information are read often and should be cached (TR-006). These need fast, shared, short-lived state.

## Options considered

1. **Redis**: in-memory, expiring keys, atomic counters; one store for all three needs.
2. **PostgreSQL tables**: no new technology, but adds load on the main database for every request.
3. **In-process memory**: fastest, but not shared between services or restarts, so revocations and limits would leak.

## Decision

Redis, used by Core (revocation, rate limits, cache) and Notifications (revocation checks). NGINX keeps only a coarse per-IP limit; per-user limits run in Core.

## Consequences

- Revocation and limits are consistent across services and survive service restarts.
- Cached data must be invalidated when it changes, or users see stale information.
- One more container; if Redis is down, the services must fail safely and reject requests whose token status cannot be checked.
