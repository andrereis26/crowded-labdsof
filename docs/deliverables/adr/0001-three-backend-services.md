# ADR 0001: Split the backend into three services

- Status: accepted
- Date: 2026-10-11
- Deciders: Crowded team

## Context

The assignment requires at least two independently deployable backend components (TR-002). The backend has three kinds of work with different load and failure profiles: answering user requests, running background work on a schedule, and delivering notifications with retries (NFR-001, NFR-003, NFR-004).

## Options considered

1. **Modular monolith**: simplest to build and deploy, but does not meet TR-002 without a strong justification, and a notification backlog would slow user requests.
2. **Core + Jobs**: meets TR-002, but notification delivery (retries, dead-letter queue, push) would live inside one of them.
3. **Core + Jobs + Notifications**: each service has one clear job and can fail or scale on its own; one more service to build and operate.
4. **One service per domain area** (events, reports, polls…): strongest isolation, but a bit overengineered for the current scope and MVP.

## Decision

Option 3. **Core** handles requests and owns the domain rules, **Jobs** runs all background work (sync, weather checks, report grouping), and **Notifications** delivers notifications. NGINX is the single entry point in front of them.

## Consequences

- A slow push provider or a sync backlog does not affect request latency.
- Three services to deploy and monitor, and they must agree on message formats.
- Boundaries and data ownership are shown in the [Technical Design](../technical-design.md).
