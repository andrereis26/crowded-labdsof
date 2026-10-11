# ADR 0005: Authentication in Core with JWTs

- Status: accepted
- Date: 2026-10-11
- Deciders: Crowded team

## Context

Users register with email and password (FR-043), have roles (FR-039), and every service must check who is calling. Organization systems call the Organization API with credentials scoped to their organization (SR-010). Google/Facebook sign-in is out of scope for the MVP (FR-044).

## Options considered

1. **Auth inside Core, JWTs validated by every service**: no extra service; tokens checked locally.
2. **Separate identity provider (e.g. Keycloak)**: standard flows and federated login later, but one more heavy container to run.
3. **Server-side sessions**: easy revocation, but every service would need to query a shared session store.

## Decision

Option 1. Core registers users, hashes passwords (SR-013) and issues short-lived JWTs. Jobs and Notifications only validate them and check the Redis revocation list ([ADR 0004](0004-redis-for-revocation-rate-limiting-and-cache.md)). Organization API calls use API keys scoped per organization.

## Consequences

- No identity service to run.
- Core owns security-critical code (hashing, token signing), so it needs careful tests.
- Adding federated login later means implementing OAuth 2.0 / OpenID Connect in Core or introducing an identity provider then.
