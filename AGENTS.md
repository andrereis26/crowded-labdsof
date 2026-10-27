# Crowded

## Agent playbook

- Base branch: `main`. Work happens on `<type>/<slug>` branches (`feature/`, `fix/`,
  `docs/`, `ci/`) and goes into `main` by pull request using
  `.github/pull_request_template.md`. There are no release branches.
- Gates, narrowest first (run only for the app you changed):
  - Backend services (`core/`, `jobs/`, `notifications/`), from the service folder:
    `./gradlew spotlessCheck`, `./gradlew lint` (`-Xlint:all -Werror`), then
    `./gradlew build` (unit tests plus `spotlessCheck`). Format with
    `./gradlew spotlessApply`.
  - Backoffice unit tests: `npm --prefix backoffice test` (Vitest).
  - Backoffice format, lint and build: `npm --prefix backoffice run format:check`,
    `npm --prefix backoffice run lint`, then `npm --prefix backoffice run build`
    (`tsc -b` plus Vite). Format with `npm --prefix backoffice run format`.
  - Participant app, from `participant-app/`:
    `dart format --output=none --set-exit-if-changed lib test`,
    `flutter analyze --fatal-infos`, then `flutter test --coverage`.
    CI fails below 80% line coverage of `coverage/lcov.info`.
  - Secret scan: `scripts/gitleaks.sh git --redact --verbose` (gitleaks v8.30.1,
    local binary or Docker image, as in `.github/workflows/security.yml`).
  - Full: the participant image job (`flutter build web --release`, then
    `docker build` and the Trivy scan in `.github/workflows/participant-app.yml`).
    Run it only when `Dockerfile`, `nginx.conf` or the web build changes.
- Deliverable and commits: a commit on a feature branch. Messages follow
  `type(#issue): message` with type `feat|fix|docs|chore|ci` (README, DoD-03).
- Worktrees: use one for parallel or delegated work, at `.claude/worktrees/<name>`.
  No root `.gitignore` covers this path yet, so add one first (unverified as a team
  convention).
- Live pass: `.claude/launch.json` defines both servers.
  - `backoffice`: `npm --prefix backoffice run dev -- --port 5173 --strictPort`,
    ready at `http://localhost:5173`.
  - `participant-gallery`: serves `participant-app/build/web` on
    `http://localhost:8765`, so run `flutter build web --release` first.
  - Local identity: none. Both apps use demo data (`backoffice/src/data/`,
    `participant-app/lib/data/`) until the Core API exists.
- Graph: auto
- Hooks: `lefthook.yml` (pre-commit formats staged files and runs gitleaks; pre-push
  lints changed projects and runs gitleaks). `lefthook install` writes to the
  shared `.git/hooks`, so it applies to every worktree of the clone.
- Pitfalls:
  - The backoffice and the backend services have no CI workflow yet (#69); only the
    Security workflow touches them. Run their gates locally before opening a PR.
  - The backend compiles with Java 25 and runs Gradle on a JDK 25 daemon
    (`gradle/gradle-daemon-jvm.properties`), downloading it when missing:
    google-java-format 1.36.1 needs JVM 21 or later. The services do not start
    without PostgreSQL, RabbitMQ and Redis until the walking skeleton (#11).
  - `participant-gallery` serves a stale build unless you rebuild the web app.
  - PRs that change the UI need mobile and desktop screenshots (PR template). UI copy
    is in European Portuguese (`participant-app/README.md`).
  - Local `main` can lag `origin/main`. Fetch before branching.
