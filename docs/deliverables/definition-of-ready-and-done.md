# Definition of Ready and Definition of Done

This document defines when a backlog item is ready to be pulled into a sprint (**Definition of Ready**) and when it can be considered complete (**Definition of Done**). Both apply to every backlog item tracked in GitHub Issues. They are part of the team's [Work Methodology](work-methodology.md), and section 3 explains how they are applied and reviewed.

They follow from three goals of the project:
- deliver the principal journeys of the [Product Vision](product-vision.md) as working, deployed software every sprint;
- meet the quality, security and AI requirements of the [Requirements Specification](requirements.md);
- show the evidence the course evaluates (tests, CI/CD, observability, AI evaluation, accessibility).

---

## 1. Definition of Ready (DoR)

A user story can only enter a sprint when **all the criteria that apply to it** are met. DoR-01 to DoR-07 and DoR-10 apply to every item, and DoR-10 is confirmed by sprint planning itself; DoR-08 and DoR-09 apply only to the kinds of item they name. Each issue carries the criteria a person checks as a checklist (see section 3), so its state is visible on the issue itself.

| # | Criterion | Why | How it is checked | Applies to |
|---|---|---|---|---|
| DoR-01 | The item is written as a user story: *As a <persona>, I want <goal>, so that <benefit>*. | It ties the work to a persona of the [Product Vision](product-vision.md#personas) and to the value it brings, which the Product Owner needs to prioritize it. Technical items name the team, or the persona they serve. | The issue's first lines | Every item |
| DoR-02 | The item is linked to at least one requirement in `requirements.md` (e.g., FR-013, NFR-003). | Traceability: every requirement is covered by stories, and no story builds something nobody asked for. The requirement also gives the priority (DoR-06). | A *Requirements* line in the issue's notes | Every item |
| DoR-03 | The item has clear and testable acceptance criteria (preferably in *Given / When / Then* format). | They are what DoD-01 verifies and what the tests are written from. Without them, "done" is a matter of opinion. | An *Acceptance criteria* section; each criterion names a condition, an action and an observable result | Every item |
| DoR-04 | The item is small enough to be completed within one sprint; otherwise it is split. | A story that spans sprints delivers nothing reviewable and hides risk until the end. | Estimate of 8 points or less; larger stories are split in refinement | Every item |
| DoR-05 | The item has been estimated by the team (story points). | Planning needs it: the sprint is filled up to the team's velocity ([Work Methodology](work-methodology.md#7-evaluating-and-improving-the-process)). An estimate by the whole team also exposes different understandings of the story. | The board's *Estimate* field | Every item |
| DoR-06 | The item has a priority agreed with the Product Owner, as MoSCoW derived from its requirements' 1–10 priority: **Must have 8–10**, **Should/Could have 5–7**, **Won't have in the MVP 1–4**. | One owner of priorities avoids conflicting orders. Deriving MoSCoW from the requirements keeps the two consistent. | A *MoSCoW* line in the issue, matching the highest priority of its requirements | Every item |
| DoR-07 | Dependencies (other stories, external services, simulators, AI provider) are identified and do not block the item. | A blocked story in a sprint wastes the sprint. Simulators and fakes exist so that external services do not block ([ADR 0010](adr/0010-external-integrations.md)). | A *Dependencies* line; dependent stories are done or in the same sprint and ordered first | Every item |
| DoR-08 | UI-related items have a sketch or mockup of the screen or flow. | The UI is agreed before it is built, so reviews discuss behaviour, not layout. It is also the base for the accessibility check (DoD-14). | A link to the Figma frame or screenshot | Items with a user interface |
| DoR-09 | Items involving personal data, authentication or AI identify the relevant security, privacy or AI evaluation concerns. | These concerns are cheap to design in and expensive to add later ([Security and Privacy Assessment](security-and-privacy-assessment.md), [Responsible AI Opportunity Assessment](responsible-ai-opportunity-assessment.md)). | A *Security, privacy or AI concerns* line naming the requirements (SR-…, NFR-007, NFR-015) | Items with personal data, authentication or AI |
| DoR-10 | The team understands the item and agrees it can be delivered, confirmed during sprint planning. | The people who build it commit to it, and questions come up before work starts, not halfway. | Sprint planning itself: adding the story to the milestone is the agreement, so it has no checkbox | Every item |

---

## 2. Definition of Done (DoD)

A user story is **done** when **all the criteria that apply to it** are met. They are of three kinds, shown in the *Kind* column:

- **Enforced:** the required checks and the `main` ruleset block the merge without them, so they cannot be skipped by accident and have no checkbox.
- **Every item:** a person checks them for every story.
- **When it applies:** a person checks them only for the kinds of item they name. The criterion itself is the same for every story of that kind; it is never adjusted story by story.

**Code and review**

| # | Criterion | Why | How it is verified | Kind |
|---|---|---|---|---|
| DoD-01 | All acceptance criteria are met and verified. | The story delivers what was agreed in DoR-03. | Each criterion is covered by a test or by a check recorded in the pull request's *How to test* | Every item |
| DoD-02 | The code is merged into the main branch through a pull request reviewed and approved by at least one other team member. | A second person catches defects and spreads knowledge of the code. | The `main` ruleset requires one approval; merges are rebase only | Enforced |
| DoD-03 | Commits follow the repository convention (`type(#issue): message`) and reference the GitHub issue. | Every change can be traced to its story and requirement. | Review of the pull request's commits | Every item |
| DoD-04 | The code follows the agreed coding standards and passes static analysis without new warnings. | Consistent code is easier to review and maintain, and static analysis finds defects early. | CI: Spotless, `-Xlint:all` with warnings as errors, SpotBugs, oxlint, `tsc`, `flutter analyze --fatal-infos` ([ADR 0011](adr/0011-ci-pipeline-and-vulnerability-policy.md)) | Enforced |

**Testing**

| # | Criterion | Why | How it is verified | Kind |
|---|---|---|---|---|
| DoD-05 | Unit tests are written for the new logic and pass. | They protect the logic from regressions and document its behaviour (NFR-013). | CI: unit tests pass, with at least 80% line coverage per app | Enforced |
| DoD-06 | Integration or end-to-end tests are written when the item affects communication between components, external integrations or a principal user workflow. | Most failures in a distributed system happen between components, where unit tests cannot see them. | CI: Testcontainers integration tests; end-to-end tests for the principal journeys | When it applies: items between components, with an external integration or on a principal journey |
| DoD-07 | Failure scenarios are handled and tested where relevant (unavailable external source, unavailable AI service, duplicate events). | Crowded's value is reliable information when things go wrong (NFR-003 to NFR-006, NFR-020). | Tests that simulate the failure; the simulators' failure scenarios (#68) | When it applies: items that depend on an external service, the AI provider or a message |
| DoD-08 | AI-assisted features have their evaluation dataset updated and meet the defined acceptance criteria. | AI output is never trusted without measurement (NFR-015). | The evaluation report (#72) shows the metrics for the change | When it applies: AI items |

**Delivery and operation**

| # | Criterion | Why | How it is verified | Kind |
|---|---|---|---|---|
| DoD-09 | The CI/CD pipeline passes (build, tests, static analysis, dependency checks, container image). | Only code that passes every check can be released. | The required checks of the `main` ruleset, and the release run on `main` | Enforced |
| DoD-10 | The feature is deployed to staging and verified there. While production is the only deployed environment, it plays staging ([ADR 0013](adr/0013-single-azure-vm-docker-swarm-with-terraform.md#environments)). | Working on a developer's machine is not the same as working deployed, with real configuration, secrets and networking. | Manual check of the acceptance criteria on the shared environment after the deployment | Every item |
| DoD-11 | Relevant structured logs, metrics and trace identifiers are in place for the new functionality. | Without them, a failure in production cannot be diagnosed (NFR-010 to NFR-012). | Review; the logs and metrics are visible in Grafana ([ADR 0009](adr/0009-observability-stack.md)) | When it applies: backend items |
| DoD-12 | No secrets are committed to the repository. | Leaked credentials compromise the system and are hard to revoke everywhere (SR-008). | Gitleaks in the Git hooks and in CI; GitGuardian on pull requests | Enforced |

**Quality and documentation**

| # | Criterion | Why | How it is verified | Kind |
|---|---|---|---|---|
| DoD-13 | Security and privacy requirements relevant to the item are implemented (access control, data minimisation, no exposure of personal data). | It closes the concerns found in DoR-09. | Tests for access control; review against the SR-… requirements named in the issue | When it applies: items with personal data, authentication or AI (as in DoR-09) |
| DoD-14 | User interfaces follow the agreed accessibility guidelines (WCAG 2.1 AA for principal user journeys). | Participants include people with disabilities, and event information is critical for them (NFR-017). | Automated accessibility checks and a manual keyboard and screen-reader pass (#74) | When it applies: items with a user interface |
| DoD-15 | API documentation, architecture documentation or ADRs are updated when the item changes them. | Documentation that does not change with the code misleads the next person. | Review: the pull request touches the docs it affects | When it applies: items that change the API or the architecture |
| DoD-16 | The Product Owner has reviewed and accepted the item. | The person who prioritized it confirms it delivers the value expected. | The Product Owner's approval on the pull request or on the issue | Every item |
| DoD-17 | The GitHub issue is closed and the board is updated. | The board shows the real state of the sprint and feeds the velocity. | The issue is closed, by the pull request's `Closes #…`, and is in *Done*. Closing the issue is the check, so it has no checkbox | Every item |

---

## 3. Applying and Reviewing Them

- **Communicated and visible:**
  - The issue template of user stories carries, as checklists, only the criteria a person checks: five boxes for the Definition of Ready (DoR-01 and DoR-03 share one, as do DoR-04 and DoR-05) and three for the Definition of Done (DoD-01, DoD-10, DoD-16), plus the *when it applies* criteria of the item's kinds.
  - The pull request template carries the Definition of Done criteria checked in the review, and names the ones the required checks enforce.
  - Both are presented to the team at the start of each sprint.
- **Applied:**
  - The Definition of Ready is checked when a story moves to *Ready* on the board, and confirmed in sprint planning (DoR-10).
  - The Definition of Done is checked in the pull request review and by the required CI checks.
  - The Product Owner checks it again before accepting the story (DoD-16).
- **Feasible:**
  - Each criterion can be met within a sprint with the tools already in place: CI, simulators, fakes, the shared environment.
  - In refinement, the team decides which kinds the item is and keeps only the *when it applies* criteria of those kinds in the issue. A backend-only story, for example, has no DoR-08 or DoD-14. The reviewer of the pull request checks that the kinds were chosen correctly.
  - Four kinds are the issue's existing labels, so the board shows them: `frontend` (DoR-08, DoD-14), `backend` (DoD-11), `ai` (DoR-09, DoD-07, DoD-08, DoD-13) and `security`, for personal data or authentication (DoR-09, DoD-13). The others, an external integration (DoD-06, DoD-07), a principal journey (DoD-06) and an API or architecture change (DoD-15), are named in the issue's notes.
  - *Every item* criteria are never removed from an issue.
- **Reviewed:**
  - At every sprint retrospective, the team looks at stories that met the Definition of Done but still had problems (escaped defects), and at whether a *when it applies* criterion was left out of an item it applied to.
  - A criterion is added, changed or removed by a pull request to this document, approved by the team.
  - Changes so far:
    - DoR-06 now states how MoSCoW derives from the 1–10 priority.
    - DoD-10 now says production plays staging while it is the only environment.
    - After the Sprint 1 review (5 November 2026), the professor found the checklists on each issue too long: the template had 27 boxes, many of which did not apply. The criteria are unchanged, but each now says whether it applies to every item, only to some kinds of item, or is enforced by the merge. The issues show only the criteria a person checks: 8 boxes, plus the ones of the item's kinds.
