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
  - Deployment scripts: `shellcheck -x` on `deployment/*.sh` and
    `docker stack config` on `deployment/docker-compose.swarm.yml`.
  - Scripts (`*.sh`, `*.ps1`), locally only (no CI job): `shellcheck -x`
    (koalaman/shellcheck:v0.11.0 image) on every tracked `.sh`, and
    `Invoke-ScriptAnalyzer -Path . -Recurse` (PSScriptAnalyzer 1.24.0, in the
    mcr.microsoft.com/powershell image when pwsh is missing; no findings at any
    severity). `scripts/local/` and the scripts Lefthook runs must work on
    macOS's bash 3.2 with BSD tools (`scripts/local/lib.sh`); then run the changed
    script for real, e.g. `stack-up.sh` and `stack-down.sh` twice each.
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
  - Local identity: none. Both apps use demo data (`backoffice/src/data/`,
    `participant-app/lib/data/`) until the Core API exists; the database has demo
    accounts (`database/README.md`).
- Graph: auto
- Hooks: `lefthook.yml` (pre-commit formats staged files and runs gitleaks; pre-push
  lints changed projects and runs gitleaks). `lefthook install` writes to the
  shared `.git/hooks`, so it applies to every worktree of the clone.
- Pitfalls:
  - Every app has a CI workflow (ADR 0011). The ruleset requires one result job
    per app (`Backend result`, `Backoffice result`, `Participant app result`,
    `Database result`) plus the two Security checks; renaming those jobs breaks the
    merge gate.
  - The services connect as their own database users (`crowded_core`, ...), which
    cannot change the schema and write only their own tables (ADR 0012). A new table
    needs its writer in `database/migrations/R__grants.sql`.
  - Stack: Java 25, the latest stable Spring Boot (4.1.2) and Maven (ADR 0006).
    Maven must run on JDK 25 (`JAVA_HOME`); the enforcer plugin stops the build
    otherwise, and google-java-format 1.36.1 needs JVM 21 or later. The services do
    not start without PostgreSQL, RabbitMQ and Redis (`deployment/` runs them).
  - `participant-gallery` serves a stale build unless you rebuild the web app.
  - PRs that change the UI need mobile and desktop screenshots (PR template). UI copy
    is in European Portuguese (`participant-app/README.md`).
  - Local `main` can lag `origin/main`. Fetch before branching.
