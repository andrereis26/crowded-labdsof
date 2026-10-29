# Product Backlog

The backlog lives in GitHub Issues and on the [board](https://github.com/users/andrereis26/projects/6). This document explains how it is structured and prioritized, and plans the releases. The rules for writing, estimating and accepting items are in the [Definition of Ready and Done](definition-of-ready-and-done.md).

## 1. Structure

| Level | Where | Example |
|---|---|---|
| Requirement | [Requirements Specification](requirements.md): 123 requirements (FR, NFR, SR, TR), each with a priority from 1 to 10 | FR-013: notify connected users of an official change, priority 10 |
| Epic | An issue per section of the requirements, linking its stories | #15 Official Event Changes |
| User story | An issue: *As a <persona>, I want <goal>, so that <benefit>*, with *Given / When / Then* acceptance criteria, its requirements, MoSCoW, estimate and the DoR and DoD checklists | #33 Notify connected users of an official event change |
| Technical item | An issue for the platform (CI/CD, database, observability, simulators), written the same way, with the team as the persona | #110 Database project |

## 2. How Items Are Prioritized

**Priority comes from the requirement, and value comes from the north star.** The [Product Vision](product-vision.md#success-criteria) measures Crowded by *the share of connected participants notified before the doors open*. A requirement's priority from 1 to 10 says how directly it serves that measure, and how much the rest of the product depends on it:

| Priority | MoSCoW | Meaning | Requirements |
|---|---|---|---|
| 10 | Must have | Without it there is no MVP: the participant does not learn of the change, or the information cannot be trusted | 75 Must have (8–10) |
| 8–9 | Must have | Makes the core journeys complete, reliable or safe (security, failure handling, AI safeguards) | |
| 5–7 | Should / Could have | Adds value to a journey that already works; first to move if a sprint is at risk | 33 |
| 1–4 | Won't have in the MVP | Valuable later, or outside the product's focus (search and map, recommendations, social features, zone-level crowd, federated login) | 15 |

A story takes the highest priority of its requirements. Selling tickets was removed rather than deferred: Crowded never sells or resells tickets (#30, closed), because the [market analysis](market-and-competitor-analysis-report.md#36-a-complement-to-ticketing-not-a-competitor) positions it as a complement to ticketing platforms, not a competitor.

**Tie-breakers between items of equal priority:**
1. Dependencies first. For example, accounts and roles (#59, #60) come before anything that stores user data.
2. Risk first. Integrations, AI and asynchronous flows come before plain screens, because problems there take longest to find.
3. Then whatever completes a journey end to end.

## 3. Epics

Ordered by the value of their need. Numbers counted on 29 October 2026.

| Order | Epic | Need it answers (persona) | Stories | Must / Should-Could / Won't | Points | Why in this position |
|---|---|---|---|---|---|---|
| 1 | **Official Event Changes** (#15) | Inês and Miguel learn of a change in time, once | 7 | 6 / 0 / 1 | 23 | The north star itself: detecting a change (FR-011) and notifying it (FR-013) are the reason the product exists |
| 2 | **Connecting to Events** (#14) | Inês keeps all her events in one place | 13 | 6 / 2 / 5 | 21 | No one can be notified without first being connected to the event: tickets and follows feed epic 1 |
| 3 | **Organization Backoffice** (#16) | Miguel and Rita test interest, manage the event and talk to their audience | 18 | 11 / 5 / 2 | 47 | Organizations publish changes, crowd levels and alerts, the second source of information. Their alerts are the second disruption type. |
| 4 | **On-Site Information for Participants** (#17) | Inês checks conditions, reports and decides | 12 | 6 / 4 / 2 | 31 | Shows what epic 3 publishes. Reports and confirmations fill the gap when the organization publishes nothing (risk R1). |
| 5 | **Accounts and Security** (#18) | Everyone trusts the data and controls their own | 11 | 6 / 3 / 2 | 22 | Enables all the others. Its Must items go first in Sprint 2 by the dependency rule. |
| 6 | **Platform and Operations** (#19) | The team can deliver, operate and prove quality | 8 | 6 / 0 / 2 | 23 | Not user-facing, but the course evaluates it. CI/CD and observability go early (Sprint 2), evidence late (Sprint 3). |

Stories without a MoSCoW line are counted as Won't when they have no sprint. Points add the estimates on the board.

## 4. Release Plan

| Release | Sprint | Goal | Content (Must items first) | Points |
|---|---|---|---|---|
| **R0: Foundations** | 1, until 1 Nov 2026 | Meaningful problem and credible direction | Research, vision, backlog, design, ADRs, assessments, mockups, walking skeleton ([Sprint 1 Scope](sprint-1-scope.md)) | about 42 |
| **R1: Vertical slice** | 2, until 6 Dec 2026 | The most important journey, end to end and operated: **a change in the ticketing source reaches the connected participant** | Import a ticket with AI and its manual fallback (#20–#23), wallet and follow (#24, #25); sync, change detection and notification (#32–#34, #92); event creation, changes, crowd, queues and alerts (#36, #37, #39–#41); event page with provenance (#51, #52); accounts, roles and organization verification (#59–#61); simulator, CI/CD, database, local setup, observability, infrastructure (#68–#70, #98, #101, #110, #111) | about 82 |
| **R2: MVP** | 3, until 3 Jan 2027 | Refine and evaluate a credible, maintainable product | Complete the journeys: push, ownership, proposed events and polls, surveys, incidents and AI grouping, AI alert drafts, live dashboard, reports and confirmations, weather, offline access, preferences, account deletion. Plus the evidence: AI evaluation (#72), performance and accessibility (#74), operational dashboard (#71). | about 97 |
| **Later** | After the MVP | Grow the audience | Municipal agenda (#35), search and map (#28), zone-level crowd (#58), custom report categories (#79), following organizations (#97), recommendations (#29), social features (#31) | – |

**R2 is larger than R1 in a shorter sprint that includes the holidays.** The buffer is its Should/Could items: proposed events and polls (#45, #46, #95), ownership requests (#38), weather (#55), offline access (#56), directions (#57), the organization API (#44) and the security hardening items (#65–#67). They move to *Later*, lowest priority first, if Sprint 2's velocity shows R2 does not fit. The Must items and the course's Sprint 3 deliverables do not move.

## 5. Roadmap

```
Sprint 1 (Oct)        Sprint 2 (Nov)                    Sprint 3 (Dec)                      After the MVP
Discover & design  →  Vertical slice: change → notify →  Organizer tools, reports, AI,     →  Discovery, real ticketing
                      ticket wallet, event page,         push, offline; evaluation,          and agenda integrations,
                      accounts, CI/CD, observability     dashboard, pitch                    partnerships with venues
```

## 6. To Resolve in Sprint 2 Planning

These are inconsistencies between the issues and the requirements, found while writing this document. The Product Owner decides each one:

| Item | Inconsistency | Proposal |
|---|---|---|
| #50 Post-event summary | FR-030 has priority 8 and is in the MVP boundary, but the story has no sprint | Add it to Sprint 3 |
| #64 Sign in with Google or Facebook | FR-044 has priority 2 (Won't have), but the story is in Sprint 3 | Move it to *Later* |
| #71 Operational dashboard | NFR-012 has priority 4, but the course requires an operational dashboard in Sprint 3 | Raise NFR-012 to 8 |
| #71, #73, #91 | No MoSCoW line (#91 also has no estimate) | Complete them before Sprint 3 planning (DoR-05, DoR-06) |
| #101, #110, #111 | Platform stories not linked from epic #19 | Link them |
