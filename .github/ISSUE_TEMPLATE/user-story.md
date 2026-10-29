---
name: User story
about: A backlog item, with the Definition of Ready and Done as checklists
title: ""
labels: feature
---

**As a** <persona>, **I want** <goal>, **so that** <benefit>.

## Acceptance criteria

- **Given** <context>, **when** <action>, **then** <observable result>.

## Notes

- Requirements: <FR-…, NFR-…, SR-…> (`docs/deliverables/requirements.md`)
- MoSCoW: <Must have 8–10 | Should/Could have 5–7 | Won't have 1–4> (priority <n>)
- Dependencies: <stories, simulators, external services, or none>
- Mockup: <Figma frame, for UI items>
- Security, privacy or AI concerns: <SR-…, NFR-007, NFR-015, or none>

## Definition of Ready

- [ ] DoR-01 — Written as a user story.
- [ ] DoR-02 — Linked to requirements.
- [ ] DoR-03 — Testable acceptance criteria.
- [ ] DoR-04 — Fits within one sprint.
- [ ] DoR-05 — Estimated.
- [ ] DoR-06 — MoSCoW priority agreed with the Product Owner.
- [ ] DoR-07 — Dependencies identified and not blocking.
- [ ] DoR-08 — UI items have a mockup.
- [ ] DoR-09 — Security, privacy or AI concerns identified.
- [ ] DoR-10 — Team agrees it can be delivered (sprint planning).

## Definition of Done

- [ ] DoD-01 — All acceptance criteria are met and verified.
- [ ] DoD-02 — Merged through a pull request approved by at least one other team member.
- [ ] DoD-03 — Commits follow `type(#issue): message` and reference this issue.
- [ ] DoD-04 — Coding standards followed; static analysis passes without new warnings.
- [ ] DoD-05 — Unit tests for the new logic.
- [ ] DoD-06 — Integration or end-to-end tests where components, integrations or a principal journey are affected.
- [ ] DoD-07 — Failure scenarios handled and tested.
- [ ] DoD-08 — AI features: evaluation dataset updated and criteria met.
- [ ] DoD-09 — CI/CD pipeline passes.
- [ ] DoD-10 — Deployed to the shared environment and verified there.
- [ ] DoD-11 — Structured logs, metrics and trace identifiers in place.
- [ ] DoD-12 — No secrets committed.
- [ ] DoD-13 — Relevant security and privacy requirements implemented.
- [ ] DoD-14 — UI meets WCAG 2.1 AA.
- [ ] DoD-15 — API / architecture docs or ADRs updated if changed.
- [ ] DoD-16 — Product Owner reviewed and accepted.
- [ ] DoD-17 — Issue closed and board updated.

<!-- Mark a criterion that does not apply as "not applicable" with the reason; do not delete it. -->
