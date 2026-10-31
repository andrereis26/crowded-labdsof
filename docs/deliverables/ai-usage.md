# How we used AI

We worked with AI coding agents through **Tinker**, an agent team that runs inside
the AI app (Claude Code, Codex or Antigravity). Tinker has no server of its own. It
gives the agent a Lead and a set of specialist roles, skills and policies. The
submitted zip holds the three pieces we used:

| Submitted | What it is | How we used it |
| --- | --- | --- |
| Tinker repository | Lead charter (`AGENTS.md`), roles (`roles/`: product manager, designer, engineer, test engineer, reviewer, researcher, technical writer, delivery engineer, explorer), skills (`implement`, `investigate`, `review`, `shape`, `artifacts`, `team`), policies (`policies/`: safety, evidence, workspace, delegation) and templates (`templates/`: user story, ADR) | The working method for every AI task: the Lead picks the workflow (feature, defect, review, documentation), loads only the roles it needs and reports evidence for its claims |
| Our [`AGENTS.md`](/AGENTS.md) | The project's agent playbook: base branch, branch and commit naming, the quality gates for each app, local run scripts, known pitfalls | Project facts and rules the agent reads before each task, so its output follows our conventions and passes the same checks as our own code |
| [`docs/prompts/`](/docs/prompts/) | The prompts behind our main documents: product vision, user research, market and competitor analysis, problem and opportunity report, Responsible AI assessment, security and privacy assessment, definition of ready and done, mockups | A record of what we asked for, the context we gave and the constraints we set for each document |

## Agents, skills and prompts

- **Agents and roles:** one Lead per task. It brings in a specialist role (for
  example the Reviewer or the Test Engineer) only when that role adds something.
- **Skills:** `tinker-implement` for user stories and refactors, `tinker-investigate`
  for defects, `tinker-review` for pull request reviews, `tinker-shape` for product
  discovery, `tinker-artifacts` for documentation, user stories, ADRs and diagrams.
- **Prompts:** written by us for each document. Each one states the goal, the
  sources in `docs/`, the required sections and our product decisions (see
  [`product-vision-prompt.md`](/docs/prompts/product-vision-prompt.md)).

## Content creation

AI drafted the documentation from our prompts and the existing `docs/`, wrote user
stories and ADRs from Tinker's templates, and implemented code on feature branches
(`feature/us-<issue>`), one commit per change in the `type(#issue): message` format.

## Content review

- Every change goes into `main` through a pull request that a team member reviews.
- AI output passes the same gates as our own work (`AGENTS.md`): formatting, lint,
  unit and integration tests with an 80% coverage minimum, SpotBugs, secret scanning
  and image scans in CI.
- Tinker's Reviewer role gives an independent, read-only review of a change before
  we open the pull request.

## How we challenged the AI outputs

- **Evidence over claims:** Tinker's evidence policy (`policies/evidence.md`) makes
  the agent show the command, result and changed files for every claim. A test
  counts as passing only when its output shows it.
- **Ask instead of guessing:** our `AGENTS.md` has the agent ask the Scrum Master
  when the issue, the docs or the ADRs do not settle a decision, and list what it
  decided.
- **We kept the decisions:** the prompts state our decisions (for example, the
  product refocus in the product vision prompt). We checked drafts against the
  research and rejected or rewrote the parts that did not match.
- **Safety limits:** the agent does not commit, push, publish or change remote items
  unless we tell it to (`policies/safety.md`).
