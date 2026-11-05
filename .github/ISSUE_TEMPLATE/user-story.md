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
- Kinds without a label: <external integration, API or architecture change, or none>

## Definition of Ready

- [ ] DoR-01, DoR-03 — Written as a user story, with testable acceptance criteria.
- [ ] DoR-02 — Linked to requirements.
- [ ] DoR-04, DoR-05 — Estimated, at 8 points or less.
- [ ] DoR-06 — MoSCoW priority agreed with the Product Owner.
- [ ] DoR-07 — Dependencies identified and not blocking.

When it applies:

- [ ] DoR-08 — `frontend`: the screen or flow has a mockup.
- [ ] DoR-09 — `security` or `ai`: the security, privacy or AI concerns are identified.

DoR-10, the team's agreement, is given in sprint planning, when the story enters the milestone.

## Definition of Done

- [ ] DoD-01 — All acceptance criteria are met and verified.
- [ ] DoD-10 — Deployed to the shared environment and verified there.
- [ ] DoD-16 — Product Owner reviewed and accepted.

When it applies:

- [ ] DoD-06 — Between components, an external integration or a principal journey: integration or end-to-end tests.
- [ ] DoD-07 — `ai`, or depends on an external service or a message: failure scenarios handled and tested.
- [ ] DoD-08 — `ai`: evaluation dataset updated and criteria met.
- [ ] DoD-11 — `backend`: structured logs, metrics and trace identifiers in place.
- [ ] DoD-13 — `security` or `ai`: security and privacy requirements implemented.
- [ ] DoD-14 — `frontend`: meets WCAG 2.1 AA.
- [ ] DoD-15 — Changes the API or the architecture: docs or ADRs updated.

The required checks enforce DoD-02, DoD-04, DoD-05, DoD-09 and DoD-12; the pull request review checks DoD-03; closing this issue is DoD-17.

<!-- In refinement, add the labels of this item's kinds (frontend, backend, ai, security), keep only the "When it applies" lines of those kinds and of the kinds named in the notes, and delete the others. Never delete the other boxes. -->
