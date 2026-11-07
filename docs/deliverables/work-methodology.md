# Work Methodology

How the Crowded team plans, builds, reviews and improves its work. The [Definition of Ready and Definition of Done](definition-of-ready-and-done.md) are part of it, and so are the conventions in [Contributing](/README.md#contributing).

## 1. Why Scrum

We use **Scrum**, with three sprints that follow the course's milestones. We considered three options:

| Option | Why we did or did not choose it |
|---|---|
| **Scrum** (chosen) | The course delivers in three fixed increments, each with its own goal and a review by the professors acting as investors. Scrum's sprint goal, review and retrospective match that rhythm. A Product Owner orders the backlog against one goal per sprint, which keeps a four-person team from spreading over 70 stories. |
| Kanban | Good for continuous flow, but it has no sprint goal or review, and the course evaluates fixed increments. We take two things from it: the board's work-in-progress limit (section 4) and pulling work as soon as it is ready. |
| Waterfall | Requirements and design would be frozen before any feedback. Our product rests on assumptions that still need validating (A1–A9 in the [Problem and Opportunity Report](problem-and-opportunity-report.md#5-assumptions)), so we need feedback after every increment. |

## 2. Roles

| Person | Role | Responsibilities |
|---|---|---|
| André Reis | Scrum Master and Product Owner | Orders the backlog and agrees priorities (DoR-06). Accepts finished stories (DoD-16). Removes impediments. Settles decisions the issue, the docs and the ADRs leave open. Owns the repository settings and the shared environment. |
| Guilherme Cunha | Project Manager | Plans the sprints against the milestones and tracks scope, risks, costs and deliverables against the evaluation criteria. Owns the product vision and the mockups. |
| Margarida Pereira | QA | Owns the test strategy, the evaluation datasets with the AI owner, the accessibility and performance evidence, and the Definition of Done checks in reviews. |
| Acácio Coutinho | UI/UX | Owns the design system, the mockups' consistency and usability evaluation. |

**Everyone is also a developer**, and roles can change with the needs of a sprint. The Product Owner is one person, so priorities have one owner. The Scrum Master is the same person: in a team of four, a separate Scrum Master would leave one fewer developer.

## 3. Sprints

| Sprint | Dates | Goal | Review |
|---|---|---|---|
| 1 | until 1 November 2026 | Discover, validate and design: a meaningful problem and a credible product and technical direction | Presentations to the professors (TTP and PL), [Sprint 1 Scope](sprint-1-scope.md) |
| 2 | until 6 December 2026 | Build and operate an end-to-end version of the most important journey | Demonstration on the shared environment |
| 3 | until 3 January 2027 | Evaluate, harden and present a credible, maintainable MVP | Final investor pitch and technical demonstration |

The goals and deliverables of each sprint are the GitHub milestones *Sprint 1*, *Sprint 2* and *Sprint 3*.

### Events

| Event | When | Duration | Output |
|---|---|---|---|
| **Sprint planning** | First lab class of the sprint | 1 hour | The sprint goal and the stories pulled into the milestone. Each story must meet the Definition of Ready (DoR-10 is confirmed here). |
| **Weekly sync** | Each lab class, plus asynchronous updates on the board | 15 minutes | What moved, what is blocked, who needs a review. A blocked story gets the `blocked` label and an owner for the impediment. |
| **Backlog refinement** | Mid-sprint, for the next sprint | 30 minutes | Next sprint's stories written, linked to requirements, estimated and with acceptance criteria. Large ones are split. |
| **Sprint review** | Last week of the sprint, with the professors | The class | The increment shown against the goal; the feedback becomes backlog items |
| **Sprint retrospective** | After the review | 30 minutes | Up to three actions to change, each with an owner and an issue. The Definition of Ready and Done are reviewed too. |

## 4. Backlog and Board

- **Backlog:** GitHub Issues, structured in three levels.
  - **Epics** (#14–#19), one per section of the [Requirements Specification](requirements.md).
  - **User stories**, each written as *As a <persona>, I want <goal>, so that <benefit>*, linked to requirements and with *Given / When / Then* acceptance criteria.
  - **Technical items** for the platform.
  - The order and the reasons for it are in the [Product Backlog](product-backlog.md).
- **Priority:** each requirement has a priority from 1 to 10, and a story takes the highest of its requirements. MoSCoW follows from it: **Must have 8–10**, **Should/Could have 5–7**, **Won't have in the MVP 1–4**.
- **Estimate:** story points on the board's *Estimate* field, from 1 to 8, relative to a simple story worth 1 (for example #26, removing a ticket). The team agrees them in planning or refinement. An 8 is split if it can be.
- **Board** ([crowded-labdsof-board](https://github.com/users/andrereis26/projects/6)): *Backlog → Ready → In progress → In review → Done*.
  - *Ready* means the story meets the Definition of Ready.
  - *Done* means it meets the Definition of Done.
  - **Work-in-progress limit:** at most two items *In progress* per person, so work is finished before more is started.

## 5. Engineering Practices

| Practice | How | Why |
|---|---|---|
| Branches and pull requests | One branch per story (`feature/us-<issue>`), merged into `main` by rebase through a pull request with the template, approved by another team member (DoD-02) | Every change is reviewed, and `main` stays a linear history of reviewed commits |
| Commit convention | `type(#issue): message` (DoD-03) | Every change is traceable to its story |
| Git hooks | Lefthook: formatting and a secret scan before each commit; lint and a secret scan before each push | Problems are caught on the developer's machine, before CI |
| Continuous integration | One workflow per app ([ADR 0011](adr/0011-ci-pipeline-and-vulnerability-policy.md)): format, lint, unit tests with 80% line coverage, integration tests, static analysis, dependency and image scans. A required result check per app blocks the merge. | The Definition of Done is checked by machines wherever it can be (DoD-04, DoD-05, DoD-09, DoD-12) |
| Continuous delivery | Every merge to `main` releases images to Docker Hub and deploys them to the shared environment, with an automatic rollback ([ADR 0013](adr/0013-single-azure-vm-docker-swarm-with-terraform.md)) | Every story can be verified deployed (DoD-10) |
| Decisions | Architecture Decision Records in `docs/deliverables/adr/`; open questions go to the Product Owner before implementation | Decisions keep their reasons, and nobody guesses |
| Documentation as code | Deliverables in Markdown, diagrams as PlantUML and BPMN in the repository, reviewed in pull requests like code | The documentation changes with the product and has a history |
| AI assistance | AI coding assistants are used under the same rules as people: a branch, a pull request, review by a team member and every check. The prompts behind generated documents are kept in `docs/prompts/`. | The team stays accountable for what is merged |

## 6. Adapting to Change

| When | What we do |
|---|---|
| A requirement or priority changes | The Product Owner updates the requirement and the story. Stories outside the current sprint are re-ordered in the backlog. Inside the sprint, a story is swapped only if the sprint goal still holds. |
| A story turns out bigger than estimated | It is split: what fits the sprint stays, the rest goes back to the backlog with its own acceptance criteria |
| A decision proves wrong | A new ADR supersedes the old one, which stays for history. Example: ADR 0013 replaced the AWS deployment of ADR 0008 when the free instance was too small. |
| An external dependency is late or unavailable | Adapters with fakes ([ADR 0010](adr/0010-external-integrations.md)) keep development going. The degraded modes of the [Technical Design](technical-design.md#degraded-operation) are part of the product. |
| Someone is unavailable | Roles are flexible and every story has acceptance criteria, so another member can take it over. Pull requests describe how to test them. |
| The sprint goal is at risk | Raised at the weekly sync. The scope is cut by priority, lowest first, not the quality bar of the Definition of Done. |

## 7. Evaluating and Improving the Process

The methodology is reviewed at every retrospective, using data as well as opinion:

| Indicator | Source | What it tells us |
|---|---|---|
| Story points done per sprint (velocity) | Board, *Estimate* of the items in *Done* | How much to plan for next sprint |
| Stories carried over | Milestone, open items at the sprint's end | Whether we over-commit or estimate badly |
| Time from *In review* to merged | Pull requests | Whether reviews are a bottleneck |
| Failed checks on `main` | Actions | Whether the pull request checks catch enough |
| Escaped defects | Issues labelled `bug` after a story was *Done* | Whether the Definition of Done is strict enough |
| Costs against the estimate | [Operating Costs](operating-costs.md#5-how-the-costs-are-monitored) | Whether the infrastructure and tools fit the budget |

Each retrospective keeps at most three improvement actions, each an issue with an owner, checked at the next retrospective. A change to the Definition of Ready or Done, or to this document, is made in a pull request, so the team sees it and approves it.
