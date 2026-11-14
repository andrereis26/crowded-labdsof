# Crowded participant app

Flutter app for participants, on mobile and the web ([ADR 0006](../docs/deliverables/adr/0006-technology-stack.md)).

## Setup

**Prerequisites:** Flutter 3.47, the version CI pins (3.47.7), and Chrome for `flutter run -d chrome`. [`scripts/local/setup.sh`](../scripts/local/setup.sh) (`setup.ps1` on Windows) checks Flutter and creates `.env` ([Getting Started](../README.md#getting-started)). Signing in, the notifications and the pages of events Core knows need Core and Notifications behind the router, as `scripts/local/stack-up.sh` runs them; the other screens run on demo data.

**Configuration from `.env`:** `PARTICIPANT_APP_PORT` (8090).

**Running it alone,** from source, without the whole Docker stack:

| Step | Debian, macOS | Windows (PowerShell 7) |
|---|---|---|
| Run `flutter run -d web-server` on `http://localhost:8090/` | `scripts/local/start.sh participant-app` | `scripts\local\start.ps1 participant-app` |
| Follow its log | `scripts/local/logs.sh participant-app` | `scripts\local\logs.ps1 participant-app` |
| Stop it | `scripts/local/stop.sh participant-app` | `scripts\local\stop.ps1 participant-app` |

The web server compiles the app when the page first opens. For hot reload and the mobile targets, run it in the foreground from this folder instead, the same on every OS:

```bash
flutter pub get
flutter run -d chrome
```

**Tests and checks,** from this folder, the same on every OS:

```bash
flutter test --coverage    # CI fails below 80% line coverage
dart format --output=none --set-exit-if-changed lib test
flutter analyze --fatal-infos
```

## Structure

| Folder | Contents |
|---|---|
| `lib/ui/` | Design system: tokens and shared components |
| `lib/screens/` | Participant screens, one folder per area (wallet, import, event, report, requests, notifications, events, account) |
| `lib/data/` | Models, `AppStore` and the API clients: sign-in (`auth_api.dart`), notifications (`notifications_api.dart`) and event pages (`events_api.dart`); the rest is demo data until the Core API exists |
| `lib/app.dart` | Routes (`go_router`); each route notes its mockup code (P1, P2, …) |

The notification center (P8) lists the participant's notifications from Notifications and shows each new one as a banner at the top of the page while the app is open, through the live stream; after the connection drops it reconnects and loads what it missed. The event page (P5), its history (P6) and a change (P7) load an event Core knows, whose id is a UUID, from Core each time they open, then follow the event's live stream while open, so a published change or correction shows without a reload. When a later load fails or the stream drops, they keep showing what loaded last, with the time it was saved, and reconnect. The wallet still lists demo events, so a seeded event is opened by its address (`/event/30000000-0000-4000-8000-000000000001` for Ana). The other screens use demo data relative to the current date, so the Festival Margens ticket is always "today". The Conta tab has switches to simulate being offline and being away from the venue (P12 "Não estás perto do recinto"), and a link to the component gallery (`/dev/components`). File picking and opening an external map show "Disponível em breve" for now. The map in P18 is an illustration and the entry code in P19 is drawn from the ticket until the ticketing simulator supplies real codes; on the website the code shows for two minutes after the password.

## Container image

The image serves the web build with NGINX as a non-root user on port 8080, with a health check at `/healthz`:

```bash
flutter build web --release
docker build -t crowded/participant-app .
docker run -p 8080:8080 crowded/participant-app
```

## CI

[`.github/workflows/participant-app.yml`](../.github/workflows/participant-app.yml) runs on every pull request that touches this folder and on every push to `main`:

| Job | What it checks |
|---|---|
| Analyze and test | `dart format`, `flutter analyze --fatal-infos`, `flutter test --coverage`; fails below 80% line coverage |
| Image (`main` only, after the checks pass) | Web build, Docker image, Trivy scan (fails on fixable HIGH/CRITICAL), a request to the running container, push to Docker Hub |

[`.github/workflows/security.yml`](../.github/workflows/security.yml) runs Gitleaks (committed secrets) and Trivy (dependency vulnerabilities and Dockerfile misconfigurations) on every pull request, on `main` and weekly. Dependabot opens weekly update PRs for pub packages, the base image and the GitHub Actions, which merge themselves once CI passes ([ADR 0011](../docs/deliverables/adr/0011-ci-pipeline-and-vulnerability-policy.md#dependabot)). The Flutter version is pinned in the workflow (`FLUTTER_VERSION`); update it there when upgrading.

## Design system

The shared components follow the "0 · Componentes base" page and the participant screens of the [Crowded — Mockups](https://www.figma.com/design/EdgpgHGNgfRGewcW5kvbES/Crowded-%E2%80%94-Mockups) Figma file. Screens should import `package:crowded/ui/ui.dart` and use these components and tokens instead of raw colours or sizes.

| Need | Use |
|---|---|
| Colours, type, spacing, radii | `AppColors`, `AppTextStyles`, `AppSpacing`, `AppRadii`, `AppTheme.light()` |
| Buttons | `AppButton` (primary), `AppButton.secondary`, `AppButton.link`, sizes small/regular/large, optional `trailingIcon`, `AppInlineLink`, `AppCircleButton`, `AppChip` |
| Form fields | `AppTextField`, `AppTextField.multiline`, `AppSelectField`, `AppSegmentedControl`, `AppFieldLabel` |
| Choices | `AppCategoryTile` (P11), `AppRadioCard`, `AppOptionTile`, `AppNavigationCard` (P2) |
| Layout | `AppPanel`, `AppListGroup` / `AppListTile`, `AppIconBadge`, `AppStepHeading`, `showAppBottomSheet`, `showAppDialog` / `AppDialog` (desktop), `AppFloatingCard` |
| Navigation | `AppTopBar`, `AppTabBar` (mobile), `AppWebHeader` and `AppBreadcrumbs` (desktop), `isDesktopLayout` |
| Event information | `WalletCard`, `AlertBanner`, `CrowdLevelIndicator`, `ConfidenceIndicator`, `ConfirmationBar`, `EstimateNotice`, `SourceLabel` / `formatSource` |
| Tags | `AppTag` |
| States | `AppEmptyState`, `AppLoadingState`, `AppErrorState`, `AppResultHeader`, `AppStatusStrip.offline` / `.degraded` |

Rules carried over from the mockups:

- Every piece of event information shows its source and update time (`SourceLabel`); after 20 minutes it shows "Desatualizado".
- Crowd level, confidence and status always have text, never colour alone.
- Low-confidence participant estimates are not shown, and any estimate is shown with `EstimateNotice`.
- UI copy is in European Portuguese.

Inter is bundled in `assets/fonts/inter` (SIL Open Font License) so the app works offline. Icons are Material icons close to the ones in the mockups.
