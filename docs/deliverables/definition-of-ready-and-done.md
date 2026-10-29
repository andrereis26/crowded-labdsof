# Definition of Ready and Definition of Done

This document defines when a backlog item is ready to be pulled into a sprint (**Definition of Ready**) and when it can be considered complete (**Definition of Done**). Both apply to every backlog item tracked in GitHub Issues and are reviewed at each sprint retrospective.

---

## 1. Definition of Ready (DoR)

A user story can only enter a sprint when **all** of the following criteria are met.

| # | Criterion |
|---|---|
| DoR-01 | The item is written as a user story: *As a <persona>, I want <goal>, so that <benefit>*. |
| DoR-02 | The item is linked to at least one requirement in `requirements.md` (e.g., FR-013, NFR-003). |
| DoR-03 | The item has clear and testable acceptance criteria (preferably in *Given / When / Then* format). |
| DoR-04 | The item is small enough to be completed within one sprint; otherwise it is split. |
| DoR-05 | The item has been estimated by the team (story points). |
| DoR-06 | The item has a MoSCoW priority agreed with the Product Owner. |
| DoR-07 | Dependencies (other stories, external services, simulators, AI provider) are identified and do not block the item. |
| DoR-08 | UI-related items have a sketch or mockup of the screen or flow. |
| DoR-09 | Items involving personal data, authentication or AI identify the relevant security, privacy or AI evaluation concerns. |
| DoR-10 | The team understands the item and agrees it can be delivered, confirmed during sprint planning. |

---

## 2. Definition of Done (DoD)

A user story is **done** when **all** of the following criteria are met.

**Code and review**

| # | Criterion |
|---|---|
| DoD-01 | All acceptance criteria are met and verified. |
| DoD-02 | The code is merged into the main branch through a pull request reviewed and approved by at least one other team member. |
| DoD-03 | Commits follow the repository convention (`type(#issue): message`) and reference the GitHub issue. |
| DoD-04 | The code follows the agreed coding standards and passes static analysis without new warnings. |

**Testing**

| # | Criterion |
|---|---|
| DoD-05 | Unit tests are written for the new logic and pass. |
| DoD-06 | Integration or end-to-end tests are written when the item affects communication between components, external integrations or a principal user workflow. |
| DoD-07 | Failure scenarios are handled and tested where relevant (unavailable external source, unavailable AI service, duplicate events). |
| DoD-08 | AI-assisted features have their evaluation dataset updated and meet the defined acceptance criteria. |

**Delivery and operation**

| # | Criterion |
|---|---|
| DoD-09 | The CI/CD pipeline passes (build, tests, static analysis, dependency checks, container image). |
| DoD-10 | The feature is deployed to staging and verified there. |
| DoD-11 | Relevant structured logs, metrics and trace identifiers are in place for the new functionality. |
| DoD-12 | No secrets are committed to the repository. |

**Quality and documentation**

| # | Criterion |
|---|---|
| DoD-13 | Security and privacy requirements relevant to the item are implemented (access control, data minimisation, no exposure of personal data). |
| DoD-14 | User interfaces follow the agreed accessibility guidelines (WCAG 2.1 AA for principal user journeys). |
| DoD-15 | API documentation, architecture documentation or ADRs are updated when the item changes them. |
| DoD-16 | The Product Owner has reviewed and accepted the item. |
| DoD-17 | The GitHub issue is closed and the board is updated. |
