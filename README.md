# Crowded

Project developed for the LABDSOF (Software Development Laboratory) course, part of the Master's Degree in Informatics Engineering – Software Engineering, during the first semester of the 2026/2027 academic year.

Crowded keeps people informed when something changes or goes wrong at the events they attend, and gives organizations a direct channel to their audience: from testing interest in a proposed event, to announcing changes, to redirecting people on site. Crowded does not sell tickets; it works with tickets bought on any platform and with free public events.

## Team

| Student Number | Name               | Role                              |
|-----------------|-------------------|-----------------------------------|
| 1191256         | André Reis        | Scrum Master / Product Owner      |
| 1201506         | Guilherme Cunha   | Project Manager                   |
| 1211105         | Margarida Pereira | QA                                |
| 1250491         | Acácio Coutinho   | UI/UX                             |

## Documents

- [Assignment](/docs/LABDSOF-26-27-Assignment.md)
- [Problem and Opportunity Report](/docs/deliverables/problem-and-opportunity-report.md)
- [Market and Competitor Analysis Report](/docs/deliverables/market-and-competitor-analysis-report.md)
- [User Research](/docs/deliverables/user-research.md)
- [Product Vision](/docs/deliverables/product-vision.md)
- [Responsible AI Opportunity Assessment](/docs/deliverables/responsible-ai-opportunity-assessment.md)
- [Ethical Engagement Design](/docs/deliverables/ethical-engagement-design.md)
- [Requirements Specification](/docs/deliverables/requirements.md)
- [Security and Privacy Assessment](/docs/deliverables/security-and-privacy-assessment.md)
- [Definition of Ready and Definition of Done](/docs/deliverables/definition-of-ready-and-done.md)
- [Technical Design](/docs/deliverables/technical-design.md)
- [Architecture Decision Records](/docs/deliverables/adr/)
- [API Outline](/docs/deliverables/api-outline.md)
- [Asynchronous Flows](/docs/deliverables/asynchronous-flows.md)

## Tech Stack

| Part | Technology |
|---|---|
| Backend services (`core/`, `jobs/`, `notifications/`) | Java 25 (JDK 25), latest stable Spring Boot (4.1.2), Maven |
| Data and messaging | PostgreSQL, RabbitMQ, Redis |
| Participant app (`participant-app/`) | Flutter |
| Backoffice (`backoffice/`) | React, Vite and Tailwind CSS |

The reasons are in [ADR 0006](/docs/deliverables/adr/0006-technology-stack.md) and the other [Architecture Decision Records](/docs/deliverables/adr/).

## Domain Model

![Crowded — Domain Model](/docs/deliverables/diagrams/domain-model.svg)

The domain model shows the main concepts of Crowded and how they relate, independently of how they are stored or deployed.

- **Event** is the centre of the model. It can start as a **proposed** event (no date or venue yet) whose **InterestPoll** tests demand before it is confirmed. Participants connect to it through a **Ticket** or a **Follow**, organizations own it and publish changes, on-site information, alerts and surveys about it, and participants report what they see.
- **Aggregate roots** (yellow) mark consistency boundaries: each one is created and changed as a unit, and other parts of the model refer to it rather than changing it directly.
- **Provenance** is attached to every piece of event information, so the product can always show where it came from and when it was last updated.
- **Disruptions** take three forms: official changes (**EventChange**, published by the organization or detected in the ticketing source, and recorded once when both report it), organizer alerts (**Alert**) and incidents reported by participants (**Incident**). Changes and alerts share a **Severity** (critical, warning, information), alerts stay active until the organization resolves them, and every **Notification** points to the disruption that caused it.
- Integration details (ticketing sync state, external providers) are kept out of the model; they belong to the services described in the [Technical Design](/docs/deliverables/technical-design.md).


Note: everyone's role is flexible and can change according to the needs of the project and everyone's assumed as developer as well.

## Contributing

### Commit Messages

All commits must follow the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) convention and reference the corresponding GitHub issue:

```
[fix|docs|chore|feat](#IssueNumber): message
```

Example:

```
fix(#10): raccoon now steals food with success
```

### Naming Conventions

- File names should be descriptive and use `kebab-case` and/or `camelCase`.

### Git Hooks

The repository has one set of Git hooks for all projects, configured in [`lefthook.yml`](/lefthook.yml) and run by [Lefthook](https://lefthook.dev). Set them up once after cloning:

1. Install [Lefthook](https://lefthook.dev/installation/) 2.x, for example `npm install -g lefthook`, `brew install lefthook` or `winget install evilmartians.lefthook`.
2. Install [gitleaks 8.30.1](https://github.com/gitleaks/gitleaks#installing), or have Docker running: [`scripts/gitleaks.sh`](/scripts/gitleaks.sh) uses the `zricethezav/gitleaks:v8.30.1` image, the one the Security workflow uses, when gitleaks 8.30.1 is not installed.
3. Install each project's own tools (JDK 25 as `JAVA_HOME` for the backend, Node.js, Flutter) and dependencies (`npm install` in `backoffice/`, `flutter pub get` in `participant-app/`).
4. Run `lefthook install` at the repository root.

| Hook | What it does |
|---|---|
| `pre-commit` | Formats the staged files of each project with its formatter and stages the result, then runs `gitleaks git --pre-commit --staged --redact` on the staged changes. |
| `pre-push` | Runs the lint command of each project changed in the pushed commits, then gitleaks on those commits. Any failure blocks the push. |

`git commit --no-verify` and `git push --no-verify` skip the hooks. The pipeline (#69) runs the same checks, so skipping them only delays the failure.

### Pull Requests

Every change reaches `main` through a pull request that follows the [template](.github/pull_request_template.md), passes the required checks and is approved by another team member (DoD-02). Pull requests are merged by **rebase** only, the branch must be up to date with `main`, and the merged branch is deleted. After the merge, `main` runs the checks again and only then builds and pushes the images ([ADR 0011](docs/deliverables/adr/0011-ci-pipeline-and-vulnerability-policy.md#images-on-main)).

Dependabot's pull requests approve and merge themselves once their checks pass, and are asked to rebase when they fall behind `main`. If Dependabot does not respond, the workflow tags the team in the pull request: comment `@dependabot rebase` there ([ADR 0011](docs/deliverables/adr/0011-ci-pipeline-and-vulnerability-policy.md#dependabot)).

### Format and Lint Commands

Run them from the project's folder. The format check fails and names the unformatted files.

| Project | Format | Format check | Lint | Tests |
|---|---|---|---|---|
| `core/`, `jobs/`, `notifications/` | `./mvnw spotless:apply` | `./mvnw spotless:check` | `./mvnw test-compile` | `./mvnw verify` |
| `backoffice/` | `npm run format` | `npm run format:check` | `npm run lint` | `npm run test:coverage` |
| `participant-app/` | `dart format lib test` | `dart format --output=none --set-exit-if-changed lib test` | `flutter analyze --fatal-infos` | `flutter test` |

- Backend: Spotless with google-java-format, and the compiler with `-Xlint:all` and `failOnWarning` as lint, so any warning fails. `./mvnw verify` runs the unit tests with the JaCoCo coverage check, the integration tests (Testcontainers, so Docker must be running), SpotBugs and `spotless:check`. Maven must run on JDK 25 (`JAVA_HOME`).
- Backoffice: Prettier and oxlint (`.oxlintrc.json`). `npm run test:coverage` fails below 80% line coverage.
- Participant app: `dart format` and `flutter analyze` (`analysis_options.yaml`).
- CI runs the same checks per app (`.github/workflows/`), in the order and with the vulnerability policy of [ADR 0011](/docs/deliverables/adr/0011-ci-pipeline-and-vulnerability-policy.md).
- Secret scan of the whole history, as the Security workflow does it: `scripts/gitleaks.sh git --redact --verbose` from the repository root.
- [`.editorconfig`](/.editorconfig) sets UTF-8, LF line endings and indentation for editors, and [`.gitattributes`](/.gitattributes) keeps LF line endings in the repository.
