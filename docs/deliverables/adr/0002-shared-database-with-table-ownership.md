# ADR 0002: One database, with one owning service per table

- Status: accepted
- Date: 2026-10-11
- Deciders: Crowded team

## Context

The three services ([ADR 0001](0001-three-backend-services.md)) need shared data: for example, Notifications must read who is connected to an event and their preferences (FR-041). The assignment asks us to justify data ownership and to avoid extra database technologies without reason.

## Options considered

1. **Database per service**: strongest decoupling, but every service needs copies of shared data kept in sync through messages.
2. **One database, any service writes anything**: fastest to build, but no real ownership and tight coupling.
3. **One PostgreSQL database, each table owned by one service**: the owner is the only writer; other services may read.

## Decision

Option 3. Core owns the domain tables and is the only writer of events and incidents. Jobs owns its sync snapshots. Notifications owns notifications and device tokens.

## Consequences

- One database to run, back up and monitor.
- The ownership rule is a convention, so it must be enforced: separate database users per service, with write grants only on owned tables.
- Changes to shared tables can affect readers in other services; schema migrations must be coordinated.
