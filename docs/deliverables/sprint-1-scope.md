# Sprint 1 Scope

Sprint 1 (until 1 November 2026) is the first sprint of the [Work Methodology](work-methodology.md#3-sprints). It is the GitHub milestone *Sprint 1*.

## 1. Goal and Question

**Sprint goal:** establish that the team is addressing a meaningful problem and has proposed a credible product and technical direction (assignment, section 7.1).

**Question for the investor review:** *Is this a valuable problem, and is the proposed MVP the smallest credible product experiment?*

The scope answers the question in four parts. Every deliverable serves one of them, so nothing in the sprint is there "because it is next":

| Part of the answer | Deliverables | Why they are needed |
|---|---|---|
| 1. Is the problem valuable? | Problem and Opportunity Report, Market and Competitor Analysis, User Research | Evidence that the problem exists and is frequent (documented cases in Portugal and Europe), that alternatives leave it unsolved, and what users say about it |
| 2. Is the MVP the smallest credible experiment? | Product Vision, Product Backlog, Ethical Engagement Design, mockups | What is in and out of the MVP and why, how success is measured, and that the experience is understood before it is built |
| 3. Is the technical direction credible and responsible? | Technical Design, ADRs, Security and Privacy Assessment, Responsible AI Opportunity Assessment | The architecture, its reasons, its risks and how AI is used safely |
| 4. Does something run? | Walking Skeleton | Proof that the chosen stack builds, tests and deploys end to end before Sprint 2 builds on it |

## 2. Deliverables and Acceptance Criteria

Sprint 1 items are documents, so their acceptance criteria are written as checks a reviewer can make on the delivered document. Priority uses the 1–10 scale of the backlog. The estimate is in story points.

| Deliverable | Issue | Priority | Estimate | Acceptance criteria |
|---|---|---|---|---|
| Problem and Opportunity Report | #2 | 9 | 2 | **Given** the report, **then** it defines the target community, the problem statement, a stakeholder map, evidence with sources (documented disruptions in Portugal and Europe), and assumptions and risks, each risk with a mitigation. |
| Market and Competitor Analysis | #3 | 8 | 2 | **Given** the report, **then** it compares at least five relevant alternatives (ticketing platforms, festival apps, municipal agendas, social media), states each one's strengths and weaknesses for our problem, and derives an opportunity for differentiation and a value proposition. |
| User Research | #4 | 8 | 2 | **Given** the report, **then** it states its research method, findings with evidence (reviews of user experiences on real platforms), its limitations, and the product decisions that follow from it. |
| Product Vision | #5 | 10 | 3 | **Given** the vision, **then** it has a vision statement, personas with goals and frustrations, a value proposition, the principal user journeys and workflows, success criteria with measurable MVP targets, and an MVP boundary that says what is out. |
| Product Backlog | #6 | 7 | 2 | **Given** the backlog, **then** every requirement has a 1–10 priority, the user stories are grouped into epics with acceptance criteria, and the [Product Backlog](product-backlog.md) gives sprint objectives and an initial release plan. |
| Responsible AI Opportunity Assessment | #7 | 6 | 1 | **Given** the assessment, **then** it defines the AI-assisted workflow, its expected value, its risks with mitigations, an evaluation approach with metrics, and a non-AI fallback for every AI feature. |
| Technical Design | #8 (sub-issues #83–#89) | 6 | 2 + 12 | **Given** the design, **then** it contains the domain model (#83), data model (#84), API outline (#85), system context and component view (#86), asynchronous workflow (#87), external integrations (#88) and deployment view (#89), each consistent with the requirements and the ADRs. |
| Architecture Decision Records | #9 | 8 | 2 | **Given** the ADRs, **then** each principal architectural and technological choice has one record with its context, the alternatives considered, the decision's rationale and its consequences. |
| Security and Privacy Assessment | #10 | 5 | 1 | **Given** the assessment, **then** it has an initial STRIDE threat model, an inventory of sensitive data, access-control requirements, data-minimization measures and retention considerations. |
| Walking Skeleton | #11 | 3 | 4 | **Given** the simulator publishes an event, **when** the synchronization component reads it and the core component stores it, **then** the client displays it. **Given** a push, **when** the automated build runs, **then** all components build and at least one test passes. **Given** the containers, **when** they start with the documented command and an environment file, **then** the skeleton runs with no secrets committed. |
| Ethical Engagement Design | #94 | 7 | 3 | **Given** the document, **then** it describes one engagement mechanism, the behaviour it encourages, why it benefits users, how misuse is prevented, who it could exclude, and how users control or opt out of it. |
| Mockups, desktop and mobile | #75 | 9 | 5 | **Given** the Figma file, **then** the principal journeys have mobile and desktop frames, built from one design system, that show provenance and AI labels where the requirements ask for them. |
| Presentation | #12 | 7 | 1 | **Given** the two presentations (TTP and PL), **then** they answer the investor question with the deliverables above, within the class's time. |

**Total: about 42 points**, with the Technical Design counted through its seven sub-issues.

## 3. Feasibility

- **Capacity:** four people over five weeks, with each deliverable owned by one person and reviewed by another. 42 points is about 10 per person. Each point is roughly a day of part-time work alongside other courses.
- **Order:** the risk is in the dependencies, not the volume.
  - The problem evidence and user research come first, because the vision depends on them.
  - The vision comes before the backlog and the mockups.
  - The ADRs come before the technical design views.
  - The walking skeleton comes last, on the chosen stack.
- **What keeps it achievable:**
  - Sprint 1 builds no product features. Only the walking skeleton is code, and it is minimal by definition.
  - Its CI and deployment work was split into Sprint 2 stories (#69, #98, #101), so the skeleton proves the chain without finishing it.
- **Result:** every Sprint 1 item was closed before the review (milestone *Sprint 1*: 22 closed, 0 open).

## 4. Review Plan

| When | What | Who | Evidence |
|---|---|---|---|
| During the sprint | Each deliverable is reviewed in a pull request against its acceptance criteria above before it is merged | Another team member | The approved pull request |
| Before the review | The Product Owner checks that every deliverable is merged, its issue closed and the presentations rehearsed | André Reis | Milestone *Sprint 1* complete |
| Sprint review (investor review) | The two presentations: TTP covers the product vision, personas, journeys, epics and this scope; PL covers the domain model, architecture, quality requirements and the team. The professors' questions and feedback are written down. | The team and the professors | Notes in the review issue; each piece of feedback becomes a backlog issue or a change to a document |
| Sprint retrospective | What helped and what got in the way. Up to three improvement actions. The Definition of Ready and Done are reviewed. | The team | Actions recorded as issues for Sprint 2 |
| Start of Sprint 2 | Sprint 2 planning pulls stories that meet the Definition of Ready into the milestone *Sprint 2* | The team, led by the Product Owner | Milestone *Sprint 2* |

### How the outcome is evaluated

- **Sprint goal met:** the professors' answer to the investor question, and their grading of the deliverables against the course's criteria.
- **Process:** the number of points done against the number planned, and items carried over (none in Sprint 1). This becomes the baseline velocity for Sprint 2.
- **Product direction:** the feedback is compared with the assumptions (A1–A9). An assumption the professors question gets a validation step in the [Problem and Opportunity Report](problem-and-opportunity-report.md#5-assumptions).
