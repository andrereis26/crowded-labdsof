# Crowded

## Agent playbook

- Base branch: `main`. Work goes into `main` by pull request using
  `.github/pull_request_template.md`, merged by rebase only (merge commits and
  squash are off) and deleted after the merge. There are no release branches.
  Dependabot pull requests approve and merge themselves (ADR 0011).
- Branch names: a user story uses `feature/us-<issue number>`, e.g. `feature/us-98`
  for issue #98. Other work uses `<type>/<slug>` (`fix/`, `docs/`, `ci/`). Name the
  branch before pushing: renaming a pushed branch on GitHub closes the pull requests
  opened from it. Worktree branches created as `claude/...` must be renamed first.
- When in doubt, ask André (Scrum Master and Product Owner) instead of guessing:
  tools, versions, names, conventions and any decision that the issue, the docs and
  the ADRs do not settle. Ask before implementing, and list what you decided.
- Gates, narrowest first (run only for the app you changed):
  - Backend services (`core/`, `jobs/`, `notifications/`), from the service folder
    with `JAVA_HOME` set to a JDK 25: `./mvnw spotless:check`, `./mvnw test-compile`
    (lint: `-Xlint:all`, warnings fail), then `./mvnw verify` (unit tests with the
    80% JaCoCo check, Testcontainers integration tests, SpotBugs and
    `spotless:check`; Docker must be running). Format with `./mvnw spotless:apply`.
  - Backoffice unit tests: `npm --prefix backoffice run test:coverage` (Vitest,
    fails below 80% line coverage).
  - Backoffice format, lint and build: `npm --prefix backoffice run format:check`,
    `npm --prefix backoffice run lint`, then `npm --prefix backoffice run build`
    (`tsc -b` plus Vite). Format with `npm --prefix backoffice run format`.
  - Participant app, from `participant-app/`:
    `dart format --output=none --set-exit-if-changed lib test`,
    `flutter analyze --fatal-infos`, then `flutter test --coverage`.
    CI fails below 80% line coverage of `coverage/lcov.info`.
  - Database (`database/`): `docker build -t crowded/database database`, then
    `bash scripts/ci/test-database.sh crowded/database` (migrations with and without
    the seed, a second run that changes nothing, each service user's rights). A
    migration also runs in every service's integration tests, so run `./mvnw verify`
    in the services that use the changed tables. `database/README.md` explains how to
    add a migration (forward-only, compatible with the previous release).
  - Secret scan: `scripts/gitleaks.sh git --redact --verbose` (gitleaks v8.30.1,
    local binary or Docker image, as in `.github/workflows/security.yml`).
  - Full: an app's image job (`.github/actions/build-image`): `docker build`, the
    Trivy image scan (`.github/actions/trivy-scan`) (the participant app needs
    `flutter build web --release` first). Smoke tests run after the deployment, not
    on the image.
    Pull requests build no images (only the release on main does), so run it
    locally whenever that app's `Dockerfile`, `nginx.conf` or build changes.
  - Infrastructure (`infra/`, OpenTofu 1.13): `tofu fmt -check -recursive`,
    `tofu init -backend=false` and `tofu validate`, then the Trivy misconfiguration
    scan of `infra/` (`.trivyignore.yaml`). `tofu plan` needs the state passphrase
    and Azure access (`infra/README.md`); CI posts it in the pull request.
  - Deployment scripts: `shellcheck -x` on `deployment/*.sh` and
    `docker stack config` on `deployment/docker-compose.swarm.yml`.
  - Scripts (`*.sh`, `*.ps1`), locally only (no CI job): `shellcheck -x`
    (koalaman/shellcheck:v0.11.0 image) on every tracked `.sh`, and
    `Invoke-ScriptAnalyzer -Path . -Recurse` (PSScriptAnalyzer 1.24.0, in the
    mcr.microsoft.com/powershell image when pwsh is missing; no findings at any
    severity). `scripts/local/` and the scripts Lefthook runs must work on
    macOS's bash 3.2 with BSD tools (`scripts/local/lib.sh`); then run the changed
    script for real, e.g. `stack-up.sh` and `stack-down.sh` twice each.
- Backend architecture (`core/`, `jobs/`, `notifications/` and any new service; the
  backoffice and the participant app keep their own layouts): onion architecture
  with domain-driven design, the same in every service. Core is the reference:
  `core/README.md` (Code Layout) and `OnionArchitectureTest`; for domain types,
  `UserAccount`, `Event` (aggregates) and `Email`, `Schedule` (value objects).
  - Folders: one package per domain under `com.crowded.<service>` (Core:
    `event`, `user`, `organization`, `audit`, `outbox`; `shared` for what
    several domains use). Inside each domain, the same subfolders:
    `domain/{model,exception,service,repository}`, `application/{service,dto}`,
    `api/{controller,dto,mapper}` and
    `infrastructure/{persistence/{entity,repository,mapper},messaging,config}`.
    Create only the folders a domain needs, never other names.
  - Dependencies point inwards: api and infrastructure → application → domain.
    Adapters never use each other. The domain uses no framework: no Spring, JPA,
    validation, Jackson or MapStruct.
  - DDD: names come from the data model and the requirements. An aggregate
    changes only through its root and is saved through its own port. Another
    module is reached through its application service or port, never through its
    entities. Refusals are domain exceptions with a `code`
    (`shared/domain/exception`); `ApiExceptionHandler` turns them into RFC 9457
    problems.
  - Rich domain model (ADR 0017), never records of data with the rules
    elsewhere. Before writing a domain type, decide which kind it is:
    - Entity or aggregate root (it has an identity: a user, an event, an
      organization): a `final class`, never a record. Private fields, no
      setters, `equals`/`hashCode` on the id, getters named `getX()`. A private
      constructor; static factories named after the domain
      (`UserAccount.registerParticipant`) check every invariant, and
      `restore(...)` rebuilds a stored one for the persistence mapper. State
      changes are methods named after domain verbs (`deactivate()`,
      `Event.publishChange(...)`) that check their rule and throw a domain exception;
      never a setter or a `with*` copy that skips the rule.
    - Value object (no identity, equal by value: an email, a password, a
      schedule, a venue, coordinates): a record whose compact constructor
      validates and normalizes, so an invalid one cannot exist, plus the
      behaviour about that value (`Schedule.movedTo`). Wrap a `String`,
      number or `Instant` in one as soon as something checks it (a format, a
      range, a normalization, fields that only make sense together). Group the
      fields of an entity into value objects instead of a long flat list.
    - Domain event or outgoing message (`NotificationRequested`): an immutable
      record.
  - Where a rule goes: on the aggregate that owns the data, or in a value object.
    A domain service only for a rule that spans several aggregates or needs a
    port; never a static helper that computes an aggregate's next state for it.
    An application service only orchestrates: authorize, load, call domain
    methods, save, then the side effects (audit, outbox, metrics, logs). An `if`
    on domain state or a `RuleViolationException` built in `application/` means
    the rule is in the wrong layer. Bean Validation on an API DTO only gives an
    early 400: the domain checks the same rule, because jobs, consumers and
    tests create objects without the API.
  - Mapping a rich model: MapStruct maps the domain → JPA direction from the
    getters (nested value objects with `@Mapping(source = "venue.name")`). The
    JPA → domain direction of an entity is a `default` method in the mapper
    interface that calls `restore(...)`; when it needs another bean (such as
    `JsonColumnConverter`), the adapter passes it as a parameter. Never widen a
    constructor or turn a class into a record to please MapStruct.
  - Tests: every invariant and state change has a unit test on the domain type
    itself, without Spring or mocks.
  - Single responsibility and interface segregation: one use case per application
    method, small ports per aggregate.
  - Dependency inversion: the application depends on the ports in
    `domain/repository`. Infrastructure implements each port as a
    `<Name>RepositoryAdapter` over a package-private `SpringData<Name>Repository`.
    JPA entities never leave `infrastructure`.
  - Liskov: an adapter keeps its port's contract (what it returns when nothing is
    found, which exceptions it throws, what transaction it expects), so a test fake
    can replace it. A subclass of a domain exception keeps its category's HTTP
    status.
  - DTOs: one class per request, response, command or result (`api/dto`,
    `application/dto`), never nested in a controller. API DTOs are never entities
    or domain objects; domain enums are fine. Map between layers only with
    MapStruct interfaces (`api/mapper`, `infrastructure/persistence/mapper`).
  - Enforced: each service has an `OnionArchitectureTest` (ArchUnit) like Core's.
    It also checks the rich model: a `domain.model` type with an `id` is a class
    without public constructors or setters, and only the domain builds a
    `RuleViolationException`. Value objects and where a rule goes are left to
    review.
    `jobs/` and `notifications/` add theirs, and Core's MapStruct compiler setup,
    with their first domain.
- Deferred work: when a story leaves something for another story (a check that
  waits for a column, wiring that waits for an API, a queue the consumer must
  declare), comment it on that story's issue before the pull request is ready, as
  one checkbox per item saying what to do, where in the code, and which story and
  pull request it comes from. Ask before commenting on an issue another person
  owns. A follow-up kept only in a pull request or a chat is forgotten after the
  merge. When reading a story's issue to implement it, read its comments too
  (`gh issue view <n> --comments`): deferred checkboxes left there by other stories
  are part of its scope, so list them with the acceptance criteria. Before closing
  the story, do or move each one.
- Code comments, in every app, script and config: say what the code is for and
  why, in a sentence or two; never repeat what the code already says.
  - Cite no requirement or story: no FR-, NFR-, TR-, SR- or US- ids, no `#123`
    issue numbers, no "#70 adds this later". They go stale and mean nothing
    when reading the code; that trace belongs in the issue, the pull request
    and `docs/`. The only reference allowed is an ADR, as `(ADR 0011)`.
  - No long blocks: a short summary per class or method; design detail goes to
    the README or an ADR. A script's header may keep its usage and
    environment variables.
  - Never edit an applied `database/migrations/V*.sql`, comments included:
    Flyway's checksum check fails on every deployed database.
- Deliverable and commits: a commit on a feature branch. Messages follow
  `type(#issue): message` with type `feat|fix|docs|chore|ci` (README, DoD-03).
- Worktrees: use one for parallel or delegated work, at `.claude/worktrees/<name>`.
  No root `.gitignore` covers this path yet, so add one first (unverified as a team
  convention).
- Live pass: the scripts in `scripts/local/` (README, Getting Started; `.ps1`
  twins on Windows). Run `scripts/local/setup.sh` once: it creates the git-ignored
  `.env` that every script reads. Ports are in `.env`, on `127.0.0.1` only.
  - Whole system: `scripts/local/stack-up.sh` builds every image from the working
    copy and deploys the Swarm stack, migrated and seeded; ready at
    `http://localhost:8080/` (backoffice `/backoffice/`, API `/api/`) when it
    prints the URLs. `scripts/local/stack-down.sh` removes it with its data.
  - One app from source: `scripts/local/infra-up.sh` (PostgreSQL, RabbitMQ, Redis),
    then `scripts/local/start.sh <app>`: core 8081, jobs 8082, notifications 8083,
    backoffice 5173, participant-app 8090. `stop.sh <app>`, `logs.sh <app>`,
    `status.sh`, `reset-db.sh`, `infra-down.sh`. Stack and infra share ports: one
    at a time.
  - `.claude/launch.json` (preview_start): `backoffice`
    (`npm --prefix backoffice run dev -- --port 5173 --strictPort`) and
    `participant-gallery` (serves `participant-app/build/web` on
    `http://localhost:8765`, so run `flutter build web --release` first).
  - Local identity: both apps sign in with Core (`POST /auth/login`) and the
    database's demo accounts (`database/README.md`); the screens after sign-in
    still use demo data (`backoffice/src/data/`, `participant-app/lib/data/`)
    until #148.
- Graph: auto
- Hooks: `lefthook.yml` (pre-commit formats staged files and runs gitleaks; pre-push
  lints changed projects and runs gitleaks). `lefthook install` writes to the
  shared `.git/hooks`, so it applies to every worktree of the clone.
- Pitfalls:
  - Every app has a CI workflow (ADR 0011). The ruleset requires one result job
    per app (`Backend result`, `Backoffice result`, `Participant app result`,
    `Database result`, `Infrastructure result`) plus the two Security checks;
    renaming those jobs breaks the merge gate. On `main`, `release.yml` calls the
    app workflows (`workflow_call`), then applies `infra/` and deploys: the app
    workflows do not run on `push` themselves (ADR 0014).
  - The services connect as their own database users (`crowded_core`, ...), which
    cannot change the schema and write only their own tables (ADR 0012). A new table
    needs its writer in `database/migrations/R__grants.sql`.
  - Stack: Java 25, the latest stable Spring Boot (4.1.2) and Maven (ADR 0006).
    Maven must run on JDK 25 (`JAVA_HOME`); the enforcer plugin stops the build
    otherwise, and google-java-format 1.36.1 needs JVM 21 or later. The services do
    not start without PostgreSQL, RabbitMQ and Redis (`deployment/` runs them).
  - MapStruct under the strict compiler (`-Xlint:all`, warnings fail): keep
    `-Xlint:-processing`, and set the `-Amapstruct.*` options on the
    `default-compile` execution only, or test-compile warns (`core/pom.xml`).
    MapStruct reads a one-argument method that returns its own type, such as
    `withStatus`, as a setter. Mappers are Spring beans with constructor
    injection, and an unmapped target fails the build.
  - SpotBugs flags `EI_EXPOSE_REP2` on public constructors that store injected beans.
    Make the constructor package-private when its tests share the package;
    otherwise add a `Match` with the reason to `spotbugs-exclude.xml`.
  - `participant-gallery` serves a stale build unless you rebuild the web app.
  - PRs that change the UI need mobile and desktop screenshots (PR template). UI copy
    is in European Portuguese (`participant-app/README.md`).
  - Local `main` can lag `origin/main`. Fetch before branching.
