# Product Backlog

The backlog lives in GitHub Issues and on the [board](https://github.com/users/andrereis26/projects/6). This document explains how it is structured and prioritized, and plans the releases. The rules for writing, estimating and accepting items are in the [Definition of Ready and Done](definition-of-ready-and-done.md).

## 1. Structure

| Level | Where | Example |
|---|---|---|
| Requirement | [Requirements Specification](requirements.md): 103 requirements (FR, NFR, SR, TR), each with a priority from 1 to 10 | FR-013: notify connected participants of a change published by the organization, priority 10 |
| Epic | An issue per section of the requirements, linking its stories | #15 Official Event Changes |
| User story | An issue: *As a <persona>, I want <goal>, so that <benefit>*, with *Given / When / Then* acceptance criteria, its requirements, MoSCoW, estimate and the DoR and DoD checklists | #33 Notify connected participants of an event change |
| Technical item | An issue for the platform (CI/CD, database, observability, demonstration data), written the same way, with the team as the persona | #110 Database project |

## 2. How Items Are Prioritized

**Priority comes from the requirement, and value comes from the north star.** The [Product Vision](product-vision.md#success-criteria) measures Crowded by *the share of connected participants notified before the doors open*. A requirement's priority from 1 to 10 says how directly it serves that measure, and how much the rest of the product depends on it:

| Priority | MoSCoW | Meaning | Requirements |
|---|---|---|---|
| 10 | Must have | Without it there is no MVP: the participant cannot connect, does not learn of the change, or the information cannot be trusted | 81 Must have (8–10) |
| 8–9 | Must have | Makes the core journeys complete, reliable or safe (security, failure handling, AI safeguards) | |
| 5–7 | Should / Could have | Adds value to a journey that already works; first to move if a sprint is at risk | 20 |
| 1–4 | Won't have in the MVP | Kept for a course deliverable or an optimization (operational dashboard, read cache) | 2 |

A story takes the highest priority of its requirements. Selling tickets was removed rather than deferred: Crowded never sells or resells tickets (#30, closed), because the [market analysis](market-and-competitor-analysis-report.md#36-a-complement-to-ticketing-not-a-competitor) positions it as a complement to ticketing platforms, not a competitor.

**Scope after the Sprint 1 review.** The professors found the scope too broad and the story hard to follow. The organization became the only source of event information, so the ticketing synchronization and the merging of the same change from two sources were removed. Discovery, the event catalog, pre-event interest polls, the post-event summary, the organization API, the verification of organizations and ownership requests were also removed. Their stories were closed as not planned: #21, #25, #27, #28, #29, #31, #32, #35, #38, #44, #45, #46, #50, #58, #61, #64, #68, #79, #80, #81, #95, #96 and #97.

**Tie-breakers between items of equal priority:**
1. Dependencies first. For example, accounts and roles (#59, #60) come before anything that stores user data.
2. Risk first. Integrations, AI and asynchronous flows come before plain screens, because problems there take longest to find.
3. Then whatever completes a journey end to end.

## 3. Epics

Ordered by the value of their need. Numbers counted on 7 November 2026; the new stories are counted with the estimates given when they were created.

| Order | Epic | Need it answers (persona) | Stories | Must / Should-Could / Won't | Points | Why in this position |
|---|---|---|---|---|---|---|
| 1 | **Official Event Changes** (#15) | Inês learns of a change in time, wherever she is | 4 | 4 / 0 / 0 | 13 | The north star itself: a change published by the organization (FR-016) reaches every connected participant (FR-013) |
| 2 | **Connecting to Events** (#14) | Inês reaches her event's page with her ticket or an access code | 8 | 7 / 1 / 0 | 20 | No one can be notified without first being connected to the event |
| 3 | **Organization Backoffice** (#16) | Miguel registers and changes the event; Rita operates it on the day | 11 | 11 / 0 / 0 | 34 | The organization is the customer and the only official source: every event, change and alert starts here |
| 4 | **On-Site Information for Participants** (#17) | Inês checks conditions, weather and transport, reports and decides | 10 | 8 / 2 / 0 | 31 | Shows what epic 3 publishes, with IPMA warnings and participant reports filling the gaps (risk R1) |
| 5 | **Accounts and Security** (#18) | Everyone trusts the data and controls their own; only connected participants see an event | 10 | 7 / 3 / 0 | 24 | Enables all the others. Its Must items go first in Sprint 2 by the dependency rule. |
| 6 | **Platform and Operations** (#19) | The team can deliver, operate, demonstrate and prove quality | 9 | 6 / 1 / 2 | 25 | Not user-facing, but the course evaluates it. CI/CD and observability go early (Sprint 2), evidence late (Sprint 3). |

Points add the estimates on the board, including the platform items already done.

## 4. Release Plan

| Release | Sprint | Goal | Content (Must items first) | Points |
|---|---|---|---|---|
| **R0: Foundations** | 1, until 1 Nov 2026 | Meaningful problem and credible direction | Research, vision, backlog, design, ADRs, assessments, mockups, walking skeleton ([Sprint 1 Scope](sprint-1-scope.md)) | about 42 |
| **R1: Vertical slice** | 2, until 6 Dec 2026 | The most important journey, end to end and operated: **a change published by the organization reaches the participant connected with their ticket** | Accounts, roles and access to event pages, organization accounts created by the administrator and the first sign-in (#59, #60, and the two new account stories); import a ticket with AI, match it by event code, the typed-code fallback, the participant's events and the access code of public events (#20, #22, #23, #24, and the access-code story); create and change an event, notify and the notification center (#36, #37, #33, #34, #92); crowd, queues and alerts on the event page with provenance (#39–#41, #51, #52); IPMA weather warnings, the first external integration (#55); the schema and demo data aligned with this scope; CI/CD, database, local setup, observability, infrastructure (#69, #70, #98, #101, #110, #111) | about 86 |
| **R2: MVP** | 3, until 3 Jan 2027 | Refine and evaluate a credible, maintainable product | Complete the journeys: screenshots, entry code, offline access, push, AI alert drafts, live dashboard, surveys, incidents and AI grouping, reports and confirmations, recognition, transport and directions, preferences, account deletion, security hardening. Plus the evidence: AI evaluation (#72), performance and accessibility (#74), operational dashboard (#71), seed and demo script. | about 75 |
| **Later** | After the MVP | Grow the audience | Discovery and following organizations, pre-event interest polls, ticketing integrations and ticket validation, an API for organizations' systems, zone-level crowd, custom report categories | – |

**R2 is smaller than before the review, but it falls in a shorter sprint that includes the holidays.** Its buffer is its Should/Could items: transport and directions (#57), recognition (#54), removing a ticket (#26), the audit log, rate limiting and service authentication (#65–#67), and the demo script. They move to *Later*, lowest priority first, if Sprint 2's velocity shows R2 does not fit. The Must items and the course's Sprint 3 deliverables do not move.

## 5. Roadmap

```
Sprint 1 (Oct)        Sprint 2 (Nov)                       Sprint 3 (Dec)                      After the MVP
Discover & design  →  Vertical slice: organization       →  On-site tools, reports, AI,      →  Discovery, ticketing
                      publishes a change → participant      push, offline, transport;           integrations, APIs for
                      with a ticket is notified; IPMA,      evaluation, dashboard, pitch        organizations' systems
                      accounts, CI/CD, observability
```

## 6. To Resolve in Sprint 2 Planning

These are inconsistencies between the issues and the requirements, found while writing this document. The Product Owner decides each one:

| Item | Inconsistency | Proposal |
|---|---|---|
| #71 Operational dashboard | NFR-012 has priority 4, but the course requires an operational dashboard in Sprint 3 | Raise NFR-012 to 8 |
| #71, #73 | No MoSCoW line | Complete them before Sprint 3 planning (DoR-05, DoR-06) |
| #101, #110, #111 | Platform stories not linked from epic #19 | Link them |
